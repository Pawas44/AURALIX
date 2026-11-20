package com.videoguard.app.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color as AndroidColor
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import android.view.MotionEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import com.videoguard.app.service.PlaybackController

class BackgroundPlayWebView(context: Context) : WebView(context) {
    override fun onWindowVisibilityChanged(visibility: Int) {
        // Report VISIBLE so Chromium does not kill audio decoder in background
        super.onWindowVisibilityChanged(View.VISIBLE)
    }

    override fun dispatchWindowVisibilityChanged(visibility: Int) {
        super.dispatchWindowVisibilityChanged(View.VISIBLE)
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, View.VISIBLE)
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(true)
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        // Delegate all touch events to Compose layer so native YouTube controls handle them
        return false
    }
}

class YouTubeBridge(
    private val readyCallback: () -> Unit,
    private val stateChangeCallback: (Int) -> Unit,
    private val timeUpdateCallback: (Float, Float) -> Unit
) {
    @JavascriptInterface
    fun onReady() {
        readyCallback()
    }

    @JavascriptInterface
    fun onStateChange(state: Int) {
        stateChangeCallback(state)
    }

    @JavascriptInterface
    fun onTimeUpdate(currentTime: Float, duration: Float) {
        timeUpdateCallback(currentTime, duration)
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubeVideoPlayer(
    videoId: String,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    isVisible: Boolean = true,
    onStateChange: (Int) -> Unit = {}
) {
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var currentLoadedId by remember { mutableStateOf<String?>(null) }

    // Register with PlaybackController so transport buttons execute JS commands directly
    DisposableEffect(webViewRef) {
        PlaybackController.setCommandListener { command ->
            if (PlaybackController.isOfflineModeActive) {
                // Ignore WebView commands when offline playback is active
                return@setCommandListener
            }
            if (command.startsWith("loadVideo('") && command.endsWith("')")) {
                currentLoadedId = command.removePrefix("loadVideo('").removeSuffix("')")
            }
            webViewRef?.post {
                webViewRef?.evaluateJavascript(command, null)
            }
        }
        onDispose {
            PlaybackController.setCommandListener(null)
        }
    }

    // Video ID updates
    LaunchedEffect(videoId) {
        if (PlaybackController.isOfflineModeActive) {
            webViewRef?.evaluateJavascript("stopVideo();", null)
            return@LaunchedEffect
        }
        if (currentLoadedId != null && currentLoadedId != videoId) {
            currentLoadedId = videoId
            webViewRef?.evaluateJavascript("loadVideo('$videoId');", null)
        }
    }

    // Play/Pause sync from Compose state
    LaunchedEffect(isPlaying) {
        if (PlaybackController.isOfflineModeActive) {
            webViewRef?.evaluateJavascript("stopVideo();", null)
            return@LaunchedEffect
        }
        webViewRef?.evaluateJavascript("setPlaybackState($isPlaying);", null)
    }

    Box(
        modifier = modifier.background(Color.Black),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                BackgroundPlayWebView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setBackgroundColor(AndroidColor.BLACK)

                    settings.apply {
                        javaScriptEnabled = true
                        mediaPlaybackRequiresUserGesture = false
                        domStorageEnabled = true
                        databaseEnabled = true
                        cacheMode = WebSettings.LOAD_DEFAULT
                        allowContentAccess = true
                        allowFileAccess = true
                        allowFileAccessFromFileURLs = true
                        allowUniversalAccessFromFileURLs = true
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    }

                    setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                    webChromeClient = WebChromeClient()

                    val desktopUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"
                    settings.userAgentString = desktopUserAgent
                    webViewClient = WebViewClient()

                    val bridge = YouTubeBridge(
                        readyCallback = {
                            post {
                                if (PlaybackController.isOfflineModeActive) {
                                    evaluateJavascript("stopVideo();", null)
                                    return@post
                                }
                                currentLoadedId = videoId
                                evaluateJavascript("setPlaybackState($isPlaying);", null)
                                if (PlaybackController.isCaptionsEnabled.value) {
                                    evaluateJavascript("setCaptions(true);", null)
                                }
                            }
                        },
                        stateChangeCallback = { state ->
                            if (PlaybackController.isOfflineModeActive) {
                                // Strictly suppress WebView state callbacks during offline playback
                                return@YouTubeBridge
                            }
                            // Synchronize player state with PlaybackController
                            when (state) {
                                1 -> PlaybackController.setPlayingState(true)   // PLAYING
                                2 -> PlaybackController.setPlayingState(false)  // PAUSED
                                0 -> {
                                    // Guard against transient state 0 dispatched prematurely during loading/buffering
                                    if (PlaybackController.isSongFinished()) {
                                        if (PlaybackController.isLoopEnabled.value) {
                                            PlaybackController.seekTo(0f)
                                            PlaybackController.play()
                                        } else if (PlaybackController.isAutoplayEnabled.value) {
                                            PlaybackController.nextTrack()
                                        } else {
                                            PlaybackController.pause()
                                        }
                                    } else {
                                        android.util.Log.d("YouTubeVideoPlayer", "Debounced premature state 0: current=${PlaybackController.currentTimeSeconds.value}, duration=${PlaybackController.durationSeconds.value}")
                                    }
                                }
                            }
                            onStateChange(state)
                        },
                        timeUpdateCallback = { current, duration ->
                            if (PlaybackController.isOfflineModeActive) {
                                return@YouTubeBridge
                            }
                            PlaybackController.updateProgress(current, duration)
                        }
                    )
                    addJavascriptInterface(bridge, "AndroidBridge")

                    val htmlData = getYouTubePlayerHtml(videoId)
                    loadDataWithBaseURL("https://www.youtube.com", htmlData, "text/html", "UTF-8", null)

                    webViewRef = this
                }
            },
            update = { webView ->
                webViewRef = webView
                val wasHidden = webView.alpha == 0f || webView.translationX != 0f
                webView.alpha = if (isVisible) 1f else 0f
                if (!isVisible) {
                    webView.translationX = -50000f
                    webView.translationY = -50000f
                } else {
                    webView.translationX = 0f
                    webView.translationY = 0f
                    if (wasHidden) {
                        webView.evaluateJavascript("if (window.targetQuality) { setPlaybackQuality(window.targetQuality, false); }", null)
                    }
                }
            }
        )
    }
}

private fun getYouTubePlayerHtml(videoId: String): String {
    val qualityCode = when {
        com.videoguard.app.service.PlaybackController.playbackQuality.value.contains("720") -> "hd720"
        com.videoguard.app.service.PlaybackController.playbackQuality.value.contains("480") -> "large"
        com.videoguard.app.service.PlaybackController.playbackQuality.value.contains("360") -> "medium"
        else -> "hd1080"
    }
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
            .ytPlayerControlsContainerHost,
            .ytPlayerControlsContainerRendered,
            .ytmCustomControlHost,
            .ytmWatchPlayerControlsHost,
            .player-controls-content,
            .player-controls-background-container,
            .player-controls-background,
            .player-controls-top,
            .player-controls-middle,
            .player-controls-bottom,
            .ytwPlayerTopControlsHost,
            .ytwPlayerBottomControlsHost,
            .ytwPlayerMiddleControlsHost,
            .ytmVideoInfoHost,
            .ytwPlayerSeekOverlayHost,
            .ytwPlayerFullscreenControlsHost,
            .ytp-player-content,
            .ytp-unmute,
            .ytp-chrome-top,
            .ytp-show-cards-title,
            .ytp-pause-overlay,
            .ytp-pause-overlay-container,
            .ytp-youtube-button,
            .ytp-watermark,
            .ytp-copylink-button,
            .ytp-impression-link,
            .ytp-title,
            .ytp-title-text,
            .ytp-title-channel,
            .ytp-endscreen-content,
            .ytp-ce-element,
            .ytp-cards-teaser,
            .ytp-cards-button,
            .ytp-gradient-top,
            .ytp-gradient-bottom,
            .ytp-chrome-bottom,
            .ytp-scroll-min {
                display: none !important;
                opacity: 0 !important;
                visibility: hidden !important;
                pointer-events: none !important;
                height: 0 !important;
                width: 0 !important;
            }
          </style>
          <script>
            try {
              var exp = Date.now() + 31536000000;
              localStorage.setItem('yt-player-quality', JSON.stringify({ data: '$qualityCode', expiration: exp, creation: Date.now() }));
              localStorage.setItem('yt-player-bandwidth', JSON.stringify({ data: 50000000, expiration: exp, creation: Date.now() }));
              localStorage.setItem('yt-player-av1-pref', JSON.stringify({ data: 1, expiration: exp, creation: Date.now() }));
            } catch(e) {}

            window.userWantsPlay = true;
            window.isLoadingNewVideo = false;
            window.currentVideoId = '$videoId';
            window.targetQuality = '$qualityCode';
            window.pendingSeekTime = null;
            window.lastSeekTimestamp = 0;

            try {
              Object.defineProperty(document, 'hidden', { get: function() { return false; } });
              Object.defineProperty(document, 'visibilityState', { get: function() { return 'visible'; } });
              Object.defineProperty(document, 'webkitVisibilityState', { get: function() { return 'visible'; } });
              document.addEventListener('visibilitychange', function(e) { e.stopImmediatePropagation(); }, true);
              window.addEventListener('visibilitychange', function(e) { e.stopImmediatePropagation(); }, true);
              window.addEventListener('pagehide', function(e) { e.stopImmediatePropagation(); }, true);
              window.addEventListener('blur', function(e) { e.stopImmediatePropagation(); }, true);
            } catch(e) {}

            function stopVideo() {
              window.userWantsPlay = false;
              window.isLoadingNewVideo = false;
              window.pendingSeekTime = null;
              try {
                if (window.ytPlayer && typeof window.ytPlayer.stopVideo === 'function') {
                  window.ytPlayer.stopVideo();
                } else if (window.ytPlayer && typeof window.ytPlayer.pauseVideo === 'function') {
                  window.ytPlayer.pauseVideo();
                }
                var vids = document.getElementsByTagName('video');
                for (var i = 0; i < vids.length; i++) {
                  vids[i].pause();
                  vids[i].currentTime = 0;
                }
                var iframe = document.getElementById('player');
                if (iframe && iframe.contentWindow) {
                  iframe.contentWindow.postMessage(JSON.stringify({
                    "event": "command",
                    "func": "stopVideo",
                    "args": ""
                  }), '*');
                }
              } catch(e) {}
            }

            function pauseVideo() {
              window.userWantsPlay = false;
              window.isLoadingNewVideo = false;
              try {
                if (window.ytPlayer && typeof window.ytPlayer.pauseVideo === 'function') {
                  window.ytPlayer.pauseVideo();
                }
                var vids = document.getElementsByTagName('video');
                for (var i = 0; i < vids.length; i++) {
                  vids[i].pause();
                }
                var iframe = document.getElementById('player');
                if (iframe && iframe.contentWindow) {
                  iframe.contentWindow.postMessage(JSON.stringify({
                    "event": "command",
                    "func": "pauseVideo",
                    "args": ""
                  }), '*');
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
              setTimeout(function() {
                if (window.currentVideoId === id) {
                  window.isLoadingNewVideo = false;
                }
              }, 2500);

              var targetQ = window.targetQuality || '$qualityCode';
              if (window.ytPlayer && typeof window.ytPlayer.loadVideoById === 'function') {
                try {
                  window.ytPlayer.loadVideoById({
                    'videoId': id,
                    'startSeconds': 0,
                    'suggestedQuality': targetQ
                  });
                  window.ytPlayer.playVideo();
                  setPlaybackQuality(targetQ);
                } catch(e) {
                  try {
                    window.ytPlayer.loadVideoById(id, 0, targetQ);
                    window.ytPlayer.playVideo();
                    setPlaybackQuality(targetQ);
                  } catch(e2) {}
                }
              } else {
                var iframe = document.getElementById('player');
                if (iframe && iframe.contentWindow) {
                  iframe.contentWindow.postMessage(JSON.stringify({
                    "event": "command",
                    "func": "loadVideoById",
                    "args": [id, 0, targetQ]
                  }), '*');
                  iframe.contentWindow.postMessage(JSON.stringify({
                    "event": "command",
                    "func": "playVideo",
                    "args": ""
                  }), '*');
                  setPlaybackQuality(targetQ);
                }
              }
            }

            function seekTo(seconds) {
              window.pendingSeekTime = seconds;
              window.lastSeekTimestamp = Date.now();
              if (window.ytPlayer && window.ytPlayer.seekTo) {
                window.ytPlayer.seekTo(seconds, true);
              }
            }

            function seekBy(seconds) {
              if (window.ytPlayer && window.ytPlayer.getCurrentTime && window.ytPlayer.seekTo) {
                var cur = window.ytPlayer.getCurrentTime();
                var target = cur + seconds;
                window.pendingSeekTime = target;
                window.lastSeekTimestamp = Date.now();
                window.ytPlayer.seekTo(target, true);
              }
            }

            function setPlaybackRate(rate) {
              if (window.ytPlayer && window.ytPlayer.setPlaybackRate) {
                window.ytPlayer.setPlaybackRate(rate);
              }
            }

            function setPlaybackQuality(quality, forceReload) {
              window.targetQuality = quality;
              try {
                var exp = Date.now() + 31536000000;
                localStorage.setItem('yt-player-quality', JSON.stringify({ data: quality, expiration: exp, creation: Date.now() }));
                localStorage.setItem('yt-player-bandwidth', JSON.stringify({ data: 50000000, expiration: exp, creation: Date.now() }));
              } catch(e) {}

              if (window.ytPlayer) {
                try { if (typeof window.ytPlayer.setPlaybackQuality === 'function') window.ytPlayer.setPlaybackQuality(quality); } catch(e) {}
                try { if (typeof window.ytPlayer.setPlaybackQualityRange === 'function') window.ytPlayer.setPlaybackQualityRange(quality, quality); } catch(e) {}
                if (forceReload && window.ytPlayer.loadVideoById && window.currentVideoId) {
                  try {
                    var curTime = window.ytPlayer.getCurrentTime ? window.ytPlayer.getCurrentTime() : 0;
                    window.ytPlayer.loadVideoById({
                      'videoId': window.currentVideoId,
                      'startSeconds': curTime,
                      'suggestedQuality': quality
                    });
                    window.ytPlayer.playVideo();
                  } catch(e) {}
                }
              }
              var iframe = document.querySelector('iframe') || document.getElementById('player');
              if (iframe && iframe.contentWindow) {
                try {
                  iframe.contentWindow.postMessage(JSON.stringify({
                    "event": "command",
                    "func": "setPlaybackQuality",
                    "args": [quality]
                  }), '*');
                  iframe.contentWindow.postMessage(JSON.stringify({
                    "event": "command",
                    "func": "setPlaybackQualityRange",
                    "args": [quality, quality]
                  }), '*');
                } catch(e) {}
              }
            }

            function setVolume(vol) {
              if (window.ytPlayer && window.ytPlayer.setVolume) {
                window.ytPlayer.setVolume(vol);
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

            function cleanEmbedDOM() {
              try {
                var iframe = document.getElementById('player');
                if (!iframe) return;
                var doc = iframe.contentDocument || (iframe.contentWindow ? iframe.contentWindow.document : null);
                if (!doc) return;

                if (!doc.getElementById('vg-clean-style')) {
                  var style = doc.createElement('style');
                  style.id = 'vg-clean-style';
                  style.textContent = [
                    '.ytPlayerControlsContainerHost,',
                    '.ytPlayerControlsContainerRendered,',
                    '.ytmCustomControlHost,',
                    '.ytmWatchPlayerControlsHost,',
                    '.player-controls-content,',
                    '.player-controls-background-container,',
                    '.player-controls-background,',
                    '.player-controls-top,',
                    '.player-controls-middle,',
                    '.player-controls-bottom,',
                    '.ytwPlayerTopControlsHost,',
                    '.ytwPlayerBottomControlsHost,',
                    '.ytwPlayerMiddleControlsHost,',
                    '.ytmVideoInfoHost,',
                    '.ytwPlayerSeekOverlayHost,',
                    '.ytwPlayerFullscreenControlsHost,',
                    '.ytp-player-content,',
                    '.ytp-unmute,',
                    '.ytp-chrome-top,',
                    '.ytp-show-cards-title,',
                    '.ytp-pause-overlay,',
                    '.ytp-pause-overlay-container,',
                    '.ytp-youtube-button,',
                    '.ytp-watermark,',
                    '.ytp-copylink-button,',
                    '.ytp-impression-link,',
                    '.ytp-title,',
                    '.ytp-title-text,',
                    '.ytp-title-channel,',
                    '.ytp-endscreen-content,',
                    '.ytp-ce-element,',
                    '.ytp-cards-teaser,',
                    '.ytp-cards-button,',
                    '.ytp-gradient-top,',
                    '.ytp-gradient-bottom,',
                    '.ytp-chrome-bottom,',
                    '.ytp-scroll-min {',
                    '  display: none !important;',
                    '  opacity: 0 !important;',
                    '  visibility: hidden !important;',
                    '  pointer-events: none !important;',
                    '  height: 0 !important;',
                    '  width: 0 !important;',
                    '}'
                  ].join('\n');
                  (doc.head || doc.body || doc.documentElement).appendChild(style);
                }

                // Hide any overlays inside html5-video-player except the video container
                var vp = doc.querySelector('.html5-video-player');
                if (vp && vp.children) {
                  for (var i = 0; i < vp.children.length; i++) {
                    var c = vp.children[i];
                    var cls = c.className || "";
                    if (typeof cls === 'string' && cls.indexOf('html5-video-container') !== -1) {
                      continue;
                    }
                    c.style.setProperty('display', 'none', 'important');
                    c.style.setProperty('opacity', '0', 'important');
                    c.style.setProperty('visibility', 'hidden', 'important');
                    c.style.setProperty('pointer-events', 'none', 'important');
                  }
                }

                var selectors = [
                  '.ytPlayerControlsContainerHost',
                  '.ytmWatchPlayerControlsHost',
                  '.player-controls-content',
                  '.player-controls-background-container',
                  '.player-controls-top',
                  '.player-controls-bottom',
                  '.player-controls-middle',
                  '.ytwPlayerTopControlsHost',
                  '.ytwPlayerBottomControlsHost',
                  '.ytmVideoInfoHost',
                  '.ytp-player-content',
                  '.ytp-unmute',
                  '.ytp-chrome-top',
                  '.ytp-pause-overlay',
                  '.ytp-pause-overlay-container',
                  '.ytp-youtube-button',
                  '.ytp-watermark',
                  '.ytp-copylink-button',
                  '.ytp-impression-link',
                  '.ytp-gradient-top',
                  '.ytp-gradient-bottom',
                  '.ytp-scroll-min',
                  '.ytp-ce-element'
                ];
                for (var s = 0; s < selectors.length; s++) {
                  var els = doc.querySelectorAll(selectors[s]);
                  for (var e = 0; e < els.length; e++) {
                    els[e].style.setProperty('display', 'none', 'important');
                    els[e].style.setProperty('opacity', '0', 'important');
                    els[e].style.setProperty('visibility', 'hidden', 'important');
                    els[e].style.setProperty('pointer-events', 'none', 'important');
                  }
                }
              } catch(e) {}
            }
            setInterval(cleanEmbedDOM, 50);

            setInterval(function() {
              if (window.isLoadingNewVideo) return;
              if (window.ytPlayer && typeof window.ytPlayer.getCurrentTime === 'function' && typeof window.ytPlayer.getDuration === 'function') {
                try {
                  var cur = window.ytPlayer.getCurrentTime();
                  var dur = window.ytPlayer.getDuration();
                  if (typeof cur === 'number' && typeof dur === 'number' && !isNaN(cur) && !isNaN(dur) && cur >= 0 && dur > 0) {
                    if (window.pendingSeekTime !== null) {
                      var elapsedSeek = Date.now() - window.lastSeekTimestamp;
                      if (elapsedSeek < 1800) {
                        if (Math.abs(cur - window.pendingSeekTime) > 2.0) {
                          return; // Still seeking/buffering, suppress stale timestamp jump
                        }
                        window.pendingSeekTime = null;
                      } else {
                        window.pendingSeekTime = null;
                      }
                    }
                    if (window.AndroidBridge && window.AndroidBridge.onTimeUpdate) {
                      window.AndroidBridge.onTimeUpdate(cur, dur);
                    }
                  }
                } catch(e) {}
              }
            }, 250);
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
                videoId: '$videoId',
                host: 'https://www.youtube.com',
                suggestedQuality: '$qualityCode',
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
                  'vq': '$qualityCode',
                  'origin': 'https://www.youtube.com'
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
              if (window.targetQuality) {
                setPlaybackQuality(window.targetQuality);
              }
              if (window.currentVideoId && window.currentVideoId !== '$videoId') {
                try {
                  event.target.loadVideoById({
                    'videoId': window.currentVideoId,
                    'startSeconds': 0,
                    'suggestedQuality': window.targetQuality || '$qualityCode'
                  });
                } catch(e) {}
              }
              if (window.userWantsPlay) {
                event.target.playVideo();
              }
              if (window.AndroidBridge && window.AndroidBridge.onReady) {
                window.AndroidBridge.onReady();
              }
            }

            function onPlayerStateChange(event) {
              // -1: UNSTARTED, 0: ENDED, 1: PLAYING, 2: PAUSED, 3: BUFFERING, 5: CUED
              var state = event.data;

              if (state === 1) { // PLAYING
                window.isLoadingNewVideo = false;
                window.userWantsPlay = true;
                if (window.targetQuality) {
                  setPlaybackQuality(window.targetQuality);
                }
                if (window.AndroidBridge && window.AndroidBridge.onStateChange) {
                  window.AndroidBridge.onStateChange(1);
                }
              } else if (state === 2) { // PAUSED
                // If a new track was loaded or user requested playback,
                // ignore transient unload pause and ensure playVideo is called!
                if (window.isLoadingNewVideo || window.userWantsPlay) {
                  if (window.ytPlayer && typeof window.ytPlayer.playVideo === 'function') {
                    window.ytPlayer.playVideo();
                  }
                } else {
                  window.userWantsPlay = false;
                  if (window.AndroidBridge && window.AndroidBridge.onStateChange) {
                    window.AndroidBridge.onStateChange(2);
                  }
                }
              } else if (state === 5) { // CUED
                if (window.userWantsPlay) {
                  if (window.ytPlayer && typeof window.ytPlayer.playVideo === 'function') {
                    window.ytPlayer.playVideo();
                  }
                }
              } else if (state === -1) { // UNSTARTED
                if (window.userWantsPlay) {
                  if (window.ytPlayer && typeof window.ytPlayer.playVideo === 'function') {
                    window.ytPlayer.playVideo();
                  }
                }
              } else if (state === 0) { // ENDED
                window.isLoadingNewVideo = false;
                if (window.AndroidBridge && window.AndroidBridge.onStateChange) {
                  window.AndroidBridge.onStateChange(0);
                }
              }
            }
          </script>
        </body>
        </html>
    """.trimIndent()
}
