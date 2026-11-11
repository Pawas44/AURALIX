import SwiftUI
import WebKit

struct GoogleAuthSheet: View {
    let onDismiss: () -> Void
    let onLoginSuccess: () -> Void

    @State private var isLoading = true
    @State private var currentDomain = "accounts.google.com"
    @State private var webViewRef: WKWebView? = nil

    var body: some View {
        VStack(spacing: 0) {
            // Top Header Bar
            HStack(spacing: 10) {
                Button(action: onDismiss) {
                    Image(systemName: "xmark")
                        .font(.system(size: 18, weight: .semibold))
                        .foregroundColor(.white)
                        .padding(6)
                }

                Image(systemName: "lock.fill")
                    .font(.system(size: 14))
                    .foregroundColor(Color(hex: 0x4CAF50))

                VStack(alignment: .leading, spacing: 1) {
                    Text("Sign in to YouTube")
                        .font(.system(size: 15, weight: .bold))
                        .foregroundColor(.white)
                    Text(currentDomain)
                        .font(.system(size: 11))
                        .foregroundColor(Color(hex: 0xAAAAAA))
                }

                Spacer()

                if isLoading {
                    ProgressView()
                        .progressViewStyle(CircularProgressViewStyle(tint: Color(hex: 0x3EA6FF)))
                        .scaleEffect(0.8)
                        .padding(.trailing, 4)
                } else {
                    Button(action: { webViewRef?.reload() }) {
                        Image(systemName: "arrow.clockwise")
                            .font(.system(size: 17))
                            .foregroundColor(.white)
                            .padding(6)
                    }
                }

                Button(action: completeLoginManually) {
                    Image(systemName: "checkmark.circle.fill")
                        .font(.system(size: 22))
                        .foregroundColor(Color(hex: 0x4CAF50))
                        .padding(4)
                }
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 8)
            .background(Color(hex: 0x282828))

            GoogleAuthWebView(
                isLoading: $isLoading,
                currentDomain: $currentDomain,
                webViewRef: $webViewRef,
                onLoginSuccess: onLoginSuccess
            )
        }
        .background(Color(hex: 0x1E1E1E).ignoresSafeArea())
    }

    private func completeLoginManually() {
        webViewRef?.evaluateJavaScript(GoogleAuthWebView.extractionScript, completionHandler: nil)
        Task {
            _ = await YouTubeSyncEngine.shared.syncCookiesFromWebKit()
            await YouTubeSyncEngine.shared.syncAllUserData()
            await MainActor.run {
                onLoginSuccess()
            }
        }
    }
}

struct GoogleAuthWebView: UIViewRepresentable {
    @Binding var isLoading: Bool
    @Binding var currentDomain: String
    @Binding var webViewRef: WKWebView?
    let onLoginSuccess: () -> Void

    static let startURL = "https://accounts.google.com/AccountChooser?service=youtube&continue=https%3A%2F%2Fm.youtube.com%2F"

    func makeCoordinator() -> Coordinator {
        Coordinator(self)
    }

    func makeUIView(context: Context) -> WKWebView {
        let config = WKWebViewConfiguration()
        config.websiteDataStore = .default()
        config.allowsInlineMediaPlayback = true

        let controller = WKUserContentController()
        controller.add(context.coordinator, name: "VideoGuardAuth")
        config.userContentController = controller

        let webView = WKWebView(frame: .zero, configuration: config)
        webView.navigationDelegate = context.coordinator
        webView.customUserAgent = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1"

        DispatchQueue.main.async {
            self.webViewRef = webView
        }

        if let url = URL(string: Self.startURL) {
            webView.load(URLRequest(url: url))
        }
        return webView
    }

    func updateUIView(_ uiView: WKWebView, context: Context) {
        context.coordinator.parent = self
    }

    static func dismantleUIView(_ uiView: WKWebView, coordinator: Coordinator) {
        uiView.configuration.userContentController.removeScriptMessageHandler(forName: "VideoGuardAuth")
    }

    class Coordinator: NSObject, WKNavigationDelegate, WKScriptMessageHandler {
        var parent: GoogleAuthWebView

        init(_ parent: GoogleAuthWebView) {
            self.parent = parent
        }

        func webView(_ webView: WKWebView, didStartProvisionalNavigation navigation: WKNavigation!) {
            DispatchQueue.main.async {
                self.parent.isLoading = true
                if let host = webView.url?.host {
                    self.parent.currentDomain = host.contains("youtube.com") ? "m.youtube.com" : host
                }
            }
        }

        func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
            let urlStr = webView.url?.absoluteString ?? ""
            DispatchQueue.main.async {
                self.parent.isLoading = false
                if let host = webView.url?.host {
                    self.parent.currentDomain = host.contains("youtube.com") ? "m.youtube.com" : host
                }
            }

            if (urlStr.contains("m.youtube.com") || urlStr.contains("www.youtube.com")) &&
               !urlStr.contains("accounts.google.com") &&
               !urlStr.contains("ServiceLogin") &&
               !urlStr.contains("signin") {
                Task {
                    _ = await YouTubeSyncEngine.shared.syncCookiesFromWebKit()
                }
                webView.evaluateJavaScript(GoogleAuthWebView.extractionScript, completionHandler: nil)
            }
        }

        func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) {
            guard message.name == "VideoGuardAuth", let body = message.body as? [String: Any],
                  let action = body["action"] as? String else { return }

            switch action {
            case "profile":
                let name = (body["name"] as? String ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
                let email = (body["email"] as? String ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
                let avatar = body["avatarUrl"] as? String
                let handle = body["handle"] as? String ?? ""
                if !name.isEmpty && name.caseInsensitiveCompare("Google Account") != .orderedSame &&
                    name.caseInsensitiveCompare("YouTube") != .orderedSame &&
                    name.caseInsensitiveCompare("Sign in") != .orderedSame {
                    let finalHandle: String
                    if !handle.isEmpty {
                        finalHandle = handle.hasPrefix("@") ? handle : "@\(handle)"
                    } else {
                        finalHandle = "@" + name.lowercased().replacingOccurrences(of: "[^a-z0-9_]", with: "_", options: .regularExpression)
                    }
                    AccountRepository.shared.setGoogleAccount(
                        name: name,
                        email: email,
                        avatarUrl: (avatar?.isEmpty == false) ? avatar : nil,
                        handle: finalHandle
                    )
                }
            case "history":
                if let json = body["json"] as? String, !json.isEmpty {
                    let videos = YouTubeSyncEngine.parseVideosFromJsonString(json)
                    if !videos.isEmpty {
                        UserDataRepository.shared.setOnlineHistory(videos)
                    }
                }
            case "feed":
                if let json = body["json"] as? String, !json.isEmpty {
                    let videos = YouTubeSyncEngine.parseVideosFromJsonString(json)
                    if !videos.isEmpty {
                        YouTubeSyncEngine.shared.cacheHomeFeed(videos)
                    }
                }
            case "completed":
                Task {
                    _ = await YouTubeSyncEngine.shared.syncCookiesFromWebKit()
                    await YouTubeSyncEngine.shared.syncAllUserData()
                    await MainActor.run {
                        self.parent.onLoginSuccess()
                    }
                }
            default:
                break
            }
        }
    }

    static let extractionScript = """
    (function() {
        if (!window.location.hostname.includes('youtube.com')) return;

        function postAuth(payload) {
            try {
                if (window.webkit && window.webkit.messageHandlers && window.webkit.messageHandlers.VideoGuardAuth) {
                    window.webkit.messageHandlers.VideoGuardAuth.postMessage(payload);
                }
            } catch(e) {}
        }

        var apiKey = (window.ytcfg && window.ytcfg.get) ? (window.ytcfg.get('INNERTUBE_API_KEY') || 'AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8') : 'AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8';
        var clientVer = (window.ytcfg && window.ytcfg.get) ? (window.ytcfg.get('INNERTUBE_CLIENT_VERSION') || '2.20261010.01.00') : '2.20261010.01.00';

        function getContext() {
            if (window.ytcfg && window.ytcfg.get) {
                return window.ytcfg.get('INNERTUBE_CONTEXT') || {
                    client: { clientName: 'MWEB', clientVersion: clientVer, hl: 'en', gl: 'US' }
                };
            }
            return { client: { clientName: 'MWEB', clientVersion: clientVer, hl: 'en', gl: 'US' } };
        }

        fetch('/youtubei/v1/account/account_menu?key=' + apiKey, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            credentials: 'include',
            body: JSON.stringify({ context: getContext() })
        }).then(function(r) { return r.json(); }).then(function(data) {
            try {
                var header = data.actions[0].openPopupAction.popup.multiPageMenuRenderer.header.activeAccountHeaderRenderer;
                if (header) {
                    var name = header.accountName ? (header.accountName.simpleText || (header.accountName.runs && header.accountName.runs[0] && header.accountName.runs[0].text) || '') : '';
                    var email = header.email ? (header.email.simpleText || (header.email.runs && header.email.runs[0] && header.email.runs[0].text) || '') : '';
                    var handle = header.channelHandle ? (header.channelHandle.simpleText || (header.channelHandle.runs && header.channelHandle.runs[0] && header.channelHandle.runs[0].text) || '') : '';
                    var avatarUrl = '';
                    if (header.accountPhoto && header.accountPhoto.thumbnails && header.accountPhoto.thumbnails.length > 0) {
                        avatarUrl = header.accountPhoto.thumbnails[header.accountPhoto.thumbnails.length - 1].url || '';
                    }
                    if (name && name !== 'Google Account' && name !== 'YouTube') {
                        postAuth({ action: 'profile', name: name, email: email, avatarUrl: avatarUrl, handle: handle });
                    }
                }
            } catch(e) {}
        }).catch(function(e) {});

        fetch('/youtubei/v1/browse?key=' + apiKey, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            credentials: 'include',
            body: JSON.stringify({ context: getContext(), browseId: 'FEhistory' })
        }).then(function(r) { return r.json(); }).then(function(data) {
            try { postAuth({ action: 'history', json: JSON.stringify(data) }); } catch(e) {}
        }).catch(function(e) {});

        fetch('/youtubei/v1/browse?key=' + apiKey, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            credentials: 'include',
            body: JSON.stringify({ context: getContext(), browseId: 'FEwhat_to_watch' })
        }).then(function(r) { return r.json(); }).then(function(data) {
            try { postAuth({ action: 'feed', json: JSON.stringify(data) }); } catch(e) {}
        }).catch(function(e) {});

        setTimeout(function() {
            postAuth({ action: 'completed' });
        }, 1200);
    })();
    """
}
