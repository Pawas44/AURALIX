import SwiftUI
import WebKit

struct YouTubeVideoPlayer: UIViewRepresentable {
    let videoId: String
    let isPlaying: Bool
    var onStateChange: ((Int) -> Void)? = nil

    func makeCoordinator() -> Coordinator {
        Coordinator(self)
    }

    func makeUIView(context: Context) -> WKWebView {
        let config = WKWebViewConfiguration()
        config.allowsInlineMediaPlayback = true
        config.mediaTypesRequiringUserActionForPlayback = []
        config.allowsPictureInPictureMediaPlayback = true
        config.websiteDataStore = .default()

        let contentController = WKUserContentController()
        contentController.add(context.coordinator, name: "iOSBridge")
        config.userContentController = contentController

        let webView = WKWebView(frame: .zero, configuration: config)
        webView.backgroundColor = .black
        webView.isOpaque = false
        webView.scrollView.isScrollEnabled = false
        webView.scrollView.bounces = false
        webView.isUserInteractionEnabled = false
        webView.customUserAgent = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1"

        context.coordinator.webView = webView
        context.coordinator.currentLoadedId = videoId

        MediaPlaybackService.shared.setCommandListener { [weak webView, weak coord = context.coordinator] command in
            guard let webView = webView else { return }
            if command.hasPrefix("loadVideo(") {
                let idPart = command
                    .replacingOccurrences(of: "loadVideo('", with: "")
                    .replacingOccurrences(of: "')", with: "")
                coord?.currentLoadedId = idPart
            }
            DispatchQueue.main.async {
                webView.evaluateJavaScript(command, completionHandler: nil)
            }
        }

        let html = Self.playerHTML(videoId: videoId)
        webView.loadHTMLString(html, baseURL: URL(string: "https://www.youtube-nocookie.com"))
        return webView
    }

    func updateUIView(_ webView: WKWebView, context: Context) {
        context.coordinator.parent = self
        if context.coordinator.currentLoadedId != videoId {
            context.coordinator.currentLoadedId = videoId
            webView.evaluateJavaScript("loadVideo('\(videoId)');", completionHandler: nil)
        }
        webView.evaluateJavaScript("setPlaybackState(\(isPlaying));", completionHandler: nil)
    }

    static func dismantleUIView(_ uiView: WKWebView, coordinator: Coordinator) {
        uiView.configuration.userContentController.removeScriptMessageHandler(forName: "iOSBridge")
        MediaPlaybackService.shared.setCommandListener(nil)
    }

    class Coordinator: NSObject, WKScriptMessageHandler {
        var parent: YouTubeVideoPlayer
        weak var webView: WKWebView?
        var currentLoadedId: String?

        init(_ parent: YouTubeVideoPlayer) {
            self.parent = parent
        }

        func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) {
            guard message.name == "iOSBridge", let body = message.body as? [String: Any],
                  let event = body["event"] as? String else { return }

            switch event {
            case "onReady":
                DispatchQueue.main.async { [weak self] in
                    guard let self = self, let webView = self.webView else { return }
                    self.currentLoadedId = self.parent.videoId
                    webView.evaluateJavaScript("setPlaybackState(\(self.parent.isPlaying));", completionHandler: nil)
                    if MediaPlaybackService.shared.isCaptionsEnabled {
                        webView.evaluateJavaScript("setCaptions(true);", completionHandler: nil)
                    }
                }
            case "onStateChange":
                if let state = body["state"] as? Int {
                    DispatchQueue.main.async {
                        switch state {
                        case 1:
                            MediaPlaybackService.shared.setPlayingState(true)
                        case 2:
                            MediaPlaybackService.shared.setPlayingState(false)
                        case 0:
                            if MediaPlaybackService.shared.isLoopEnabled {
                                MediaPlaybackService.shared.seekTo(seconds: 0)
                                MediaPlaybackService.shared.play()
                            } else if MediaPlaybackService.shared.isAutoplayEnabled {
                                MediaPlaybackService.shared.nextTrack()
                            } else {
                                MediaPlaybackService.shared.pause()
                            }
                        default:
                            break
                        }
                        self.parent.onStateChange?(state)
                    }
                }
            case "onTimeUpdate":
                if let current = body["currentTime"] as? Double,
                   let duration = body["duration"] as? Double {
                    MediaPlaybackService.shared.updateProgress(current: current, duration: duration)
                }
            default:
                break
            }
        }
    }

    private static func playerHTML(videoId: String) -> String {
        return """
        <!DOCTYPE html>
        <html>
        <head>
          <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
          <style>
            * { margin: 0; padding: 0; box-sizing: border-box; }
            body, html { width: 100%; height: 100%; background-color: #000; overflow: hidden; pointer-events: none; user-select: none; }
            #player-wrapper { width: 100%; height: 100%; position: relative; overflow: hidden; background-color: #000; }
            #player { width: 100%; height: 100%; position: absolute; top: 0; left: 0; border: none; }
          </style>
          <script>
            window.userWantsPlay = true;
            window.isLoadingNewVideo = false;
            window.currentVideoId = '\(videoId)';

            try {
              Object.defineProperty(document, 'hidden', { get: function() { return false; } });
              Object.defineProperty(document, 'visibilityState', { get: function() { return 'visible'; } });
              Object.defineProperty(document, 'webkitVisibilityState', { get: function() { return 'visible'; } });
              document.addEventListener('visibilitychange', function(e) { e.stopImmediatePropagation(); }, true);
              window.addEventListener('visibilitychange', function(e) { e.stopImmediatePropagation(); }, true);
              window.addEventListener('pagehide', function(e) { e.stopImmediatePropagation(); }, true);
              window.addEventListener('blur', function(e) { e.stopImmediatePropagation(); }, true);
            } catch(e) {}

            function postBridge(payload) {
              try {
                if (window.webkit && window.webkit.messageHandlers && window.webkit.messageHandlers.iOSBridge) {
                  window.webkit.messageHandlers.iOSBridge.postMessage(payload);
                }
              } catch(e) {}
            }

            function setPlaybackState(play) {
              window.userWantsPlay = play;
              if (play) {
                if (window.ytPlayer && typeof window.ytPlayer.playVideo === 'function') {
                  window.ytPlayer.playVideo();
                } else {
                  var iframe = document.getElementById('player');
                  if (iframe && iframe.contentWindow) {
                    iframe.contentWindow.postMessage(JSON.stringify({
                      "event": "command",
                      "func": "playVideo",
                      "args": ""
                    }), '*');
                  }
                }
              } else {
                window.isLoadingNewVideo = false;
                if (window.ytPlayer && typeof window.ytPlayer.pauseVideo === 'function') {
                  window.ytPlayer.pauseVideo();
                } else {
                  var iframe = document.getElementById('player');
                  if (iframe && iframe.contentWindow) {
                    iframe.contentWindow.postMessage(JSON.stringify({
                      "event": "command",
                      "func": "pauseVideo",
                      "args": ""
                    }), '*');
                  }
                }
              }
            }

            function loadVideo(id) {
              window.currentVideoId = id;
              window.userWantsPlay = true;
              window.isLoadingNewVideo = true;

              if (window.ytPlayer && typeof window.ytPlayer.loadVideoById === 'function') {
                try {
                  window.ytPlayer.loadVideoById({ 'videoId': id, 'startSeconds': 0 });
                  window.ytPlayer.playVideo();
                } catch(e) {
                  try {
                    window.ytPlayer.loadVideoById(id, 0);
                    window.ytPlayer.playVideo();
                  } catch(e2) {}
                }
              } else {
                var iframe = document.getElementById('player');
                if (iframe && iframe.contentWindow) {
                  iframe.contentWindow.postMessage(JSON.stringify({
                    "event": "command", "func": "loadVideoById", "args": [id, 0]
                  }), '*');
                  iframe.contentWindow.postMessage(JSON.stringify({
                    "event": "command", "func": "playVideo", "args": ""
                  }), '*');
                }
              }
            }

            function seekTo(seconds) {
              if (window.ytPlayer && window.ytPlayer.seekTo) {
                window.ytPlayer.seekTo(seconds, true);
              }
            }

            function seekBy(seconds) {
              if (window.ytPlayer && window.ytPlayer.getCurrentTime && window.ytPlayer.seekTo) {
                var cur = window.ytPlayer.getCurrentTime();
                window.ytPlayer.seekTo(cur + seconds, true);
              }
            }

            function setPlaybackRate(rate) {
              if (window.ytPlayer && window.ytPlayer.setPlaybackRate) {
                window.ytPlayer.setPlaybackRate(rate);
              }
            }

            function setPlaybackQuality(quality) {
              if (window.ytPlayer && window.ytPlayer.setPlaybackQuality) {
                window.ytPlayer.setPlaybackQuality(quality);
              }
            }

            function setCaptions(enabled) {
              if (window.ytPlayer) {
                try {
                  if (enabled) {
                    window.ytPlayer.loadModule("captions");
                    window.ytPlayer.setOption("captions", "track", {"languageCode": "en"});
                    window.ytPlayer.setOption("captions", "reload", true);
                  } else {
                    window.ytPlayer.unloadModule("captions");
                  }
                } catch(e) {}
              }
            }

            setInterval(function() {
              if (window.ytPlayer && typeof window.ytPlayer.getCurrentTime === 'function' && typeof window.ytPlayer.getDuration === 'function') {
                try {
                  var cur = window.ytPlayer.getCurrentTime();
                  var dur = window.ytPlayer.getDuration();
                  if (typeof cur === 'number' && typeof dur === 'number' && !isNaN(cur) && !isNaN(dur) && cur >= 0) {
                    postBridge({ event: 'onTimeUpdate', currentTime: cur, duration: dur });
                  }
                } catch(e) {}
              }
            }, 500);
          </script>
        </head>
        <body>
          <div id="player-wrapper">
            <div id="player"></div>
          </div>
          <script>
            var tag = document.createElement('script');
            tag.src = "https://www.youtube.com/iframe_api";
            var firstScriptTag = document.getElementsByTagName('script')[0];
            firstScriptTag.parentNode.insertBefore(tag, firstScriptTag);

            var ytPlayer;
            function onYouTubeIframeAPIReady() {
              ytPlayer = new YT.Player('player', {
                videoId: '\(videoId)',
                host: 'https://www.youtube-nocookie.com',
                playerVars: {
                  'autoplay': 1,
                  'playsinline': 1,
                  'controls': 0,
                  'rel': 0,
                  'modestbranding': 1,
                  'fs': 0,
                  'disablekb': 1,
                  'enablejsapi': 1,
                  'iv_load_policy': 3,
                  'showinfo': 0,
                  'autohide': 1,
                  'origin': 'https://www.youtube-nocookie.com'
                },
                events: {
                  'onReady': onPlayerReady,
                  'onStateChange': onPlayerStateChange
                }
              });
              window.ytPlayer = ytPlayer;
            }

            function onPlayerReady(event) {
              window.ytPlayer = event.target;
              if (window.currentVideoId && window.currentVideoId !== '\(videoId)') {
                try {
                  event.target.loadVideoById({ 'videoId': window.currentVideoId, 'startSeconds': 0 });
                } catch(e) {}
              }
              if (window.userWantsPlay) {
                event.target.playVideo();
              }
              postBridge({ event: 'onReady' });
            }

            function onPlayerStateChange(event) {
              var state = event.data;
              if (state === 1) {
                window.isLoadingNewVideo = false;
                window.userWantsPlay = true;
                postBridge({ event: 'onStateChange', state: 1 });
              } else if (state === 2) {
                if (window.isLoadingNewVideo || window.userWantsPlay) {
                  if (window.ytPlayer && typeof window.ytPlayer.playVideo === 'function') {
                    window.ytPlayer.playVideo();
                  }
                } else {
                  window.userWantsPlay = false;
                  postBridge({ event: 'onStateChange', state: 2 });
                }
              } else if (state === 5 || state === -1) {
                if (window.userWantsPlay) {
                  if (window.ytPlayer && typeof window.ytPlayer.playVideo === 'function') {
                    window.ytPlayer.playVideo();
                  }
                }
              } else if (state === 0) {
                window.isLoadingNewVideo = false;
                postBridge({ event: 'onStateChange', state: 0 });
              }
            }
          </script>
        </body>
        </html>
        """
    }
}
