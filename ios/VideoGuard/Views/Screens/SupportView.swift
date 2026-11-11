import SwiftUI
import WebKit

private let bmcWidgetURL = URL(string: "https://www.buymeacoffee.com/widget/page/FINDEX?description=Support%20me%20on%20Buy%20me%20a%20coffee!&color=%2340DCA5")!
private let bmcProfileURL = URL(string: "https://buymeacoffee.com/FINDEX")!
private let mintAccent = Color(hex: 0x40DCA5)

struct SupportView: View {
    @ObservedObject var playbackService = MediaPlaybackService.shared
    @State private var isLoading: Bool = true
    @State private var hasError: Bool = false
    @State private var reloadTrigger: Int = 0

    var body: some View {
        VStack(spacing: 10) {
            // Compact Header Bar
            HStack(spacing: 10) {
                ZStack {
                    Circle()
                        .fill(mintAccent)
                        .frame(width: 36, height: 36)
                    Image(systemName: "cup.and.saucer.fill")
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundColor(Color(hex: 0x0D1F18))
                }

                VStack(alignment: .leading, spacing: 1) {
                    Text("Support Findex")
                        .font(.system(size: 18, weight: .bold))
                        .foregroundColor(.white)
                    Text("buymeacoffee.com/FINDEX")
                        .font(.system(size: 11.5))
                        .foregroundColor(Color(hex: 0x9E9EAF))
                }

                Spacer()

                HStack(spacing: 8) {
                    Button(action: {
                        hasError = false
                        isLoading = true
                        reloadTrigger += 1
                    }) {
                        Image(systemName: "arrow.clockwise")
                            .font(.system(size: 15, weight: .semibold))
                            .foregroundColor(.white)
                            .frame(width: 36, height: 36)
                            .background(Color.white.opacity(0.08))
                            .clipShape(Circle())
                            .overlay(Circle().strokeBorder(LiquidGlassDefaults.borderSubtle, lineWidth: 1))
                    }

                    Button(action: {
                        UIApplication.shared.open(bmcProfileURL)
                    }) {
                        Image(systemName: "safari")
                            .font(.system(size: 15, weight: .semibold))
                            .foregroundColor(mintAccent)
                            .frame(width: 36, height: 36)
                            .background(Color.white.opacity(0.08))
                            .clipShape(Circle())
                            .overlay(Circle().strokeBorder(LiquidGlassDefaults.borderSubtle, lineWidth: 1))
                    }
                }
            }
            .padding(.horizontal, 4)

            // Embedded Official Buy Me a Coffee Widget
            ZStack {
                Color.white

                BMCWidgetWebView(
                    url: bmcWidgetURL,
                    reloadTrigger: reloadTrigger,
                    isLoading: $isLoading,
                    hasError: $hasError
                )

                if isLoading {
                    Color(hex: 0xF9FAFB)
                    VStack(spacing: 12) {
                        ProgressView()
                            .tint(mintAccent)
                            .scaleEffect(1.1)
                        Text("Loading Buy Me a Coffee...")
                            .font(.system(size: 13, weight: .medium))
                            .foregroundColor(Color(hex: 0x4B5563))
                    }
                }

                if hasError {
                    Color(hex: 0x13141F)
                    VStack(spacing: 12) {
                        Text("Unable to load widget right now")
                            .font(.system(size: 15, weight: .semibold))
                            .foregroundColor(.white)
                        Button(action: {
                            hasError = false
                            isLoading = true
                            reloadTrigger += 1
                        }) {
                            Text("Retry")
                                .font(.system(size: 13.5, weight: .bold))
                                .foregroundColor(Color(hex: 0x0D1F18))
                                .padding(.horizontal, 20)
                                .padding(.vertical, 10)
                                .background(mintAccent)
                                .clipShape(RoundedRectangle(cornerRadius: 12))
                        }
                    }
                }
            }
            .clipShape(RoundedRectangle(cornerRadius: 24))
            .overlay(
                RoundedRectangle(cornerRadius: 24)
                    .strokeBorder(Color.white.opacity(0.16), lineWidth: 1.5)
            )
            .shadow(color: Color.black.opacity(0.55), radius: 14, x: 0, y: 6)
        }
        .padding(.horizontal, 14)
        .padding(.top, 8)
        .padding(.bottom, playbackService.currentTrack != nil ? 148 : 84)
    }
}

private struct BMCWidgetWebView: UIViewRepresentable {
    let url: URL
    let reloadTrigger: Int
    @Binding var isLoading: Bool
    @Binding var hasError: Bool

    func makeCoordinator() -> Coordinator {
        Coordinator(self)
    }

    func makeUIView(context: Context) -> WKWebView {
        let config = WKWebViewConfiguration()
        config.websiteDataStore = .default()
        config.preferences.javaScriptCanOpenWindowsAutomatically = true

        let webView = WKWebView(frame: .zero, configuration: config)
        webView.navigationDelegate = context.coordinator
        webView.uiDelegate = context.coordinator
        webView.backgroundColor = .white
        webView.scrollView.bounces = true
        webView.load(URLRequest(url: url))
        return webView
    }

    func updateUIView(_ uiView: WKWebView, context: Context) {
        if context.coordinator.lastReloadTrigger != reloadTrigger {
            context.coordinator.lastReloadTrigger = reloadTrigger
            uiView.load(URLRequest(url: url))
        }
    }

    class Coordinator: NSObject, WKNavigationDelegate, WKUIDelegate {
        var parent: BMCWidgetWebView
        var lastReloadTrigger: Int = 0

        init(_ parent: BMCWidgetWebView) {
            self.parent = parent
        }

        func webView(_ webView: WKWebView, didStartProvisionalNavigation navigation: WKNavigation!) {
            DispatchQueue.main.async {
                self.parent.isLoading = true
                self.parent.hasError = false
            }
        }

        func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
            DispatchQueue.main.async {
                self.parent.isLoading = false
            }
        }

        func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
            DispatchQueue.main.async {
                self.parent.isLoading = false
                self.parent.hasError = true
            }
        }

        func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
            DispatchQueue.main.async {
                self.parent.isLoading = false
                self.parent.hasError = true
            }
        }

        func webView(
            _ webView: WKWebView,
            decidePolicyFor navigationAction: WKNavigationAction,
            decisionHandler: @escaping (WKNavigationActionPolicy) -> Void
        ) {
            guard let targetURL = navigationAction.request.url else {
                decisionHandler(.allow)
                return
            }
            let scheme = targetURL.scheme?.lowercased() ?? ""
            if scheme == "http" || scheme == "https" {
                decisionHandler(.allow)
            } else {
                UIApplication.shared.open(targetURL)
                decisionHandler(.cancel)
            }
        }

        func webView(
            _ webView: WKWebView,
            createWebViewWith configuration: WKWebViewConfiguration,
            for navigationAction: WKNavigationAction,
            windowFeatures: WKWindowFeatures
        ) -> WKWebView? {
            if navigationAction.targetFrame == nil {
                webView.load(navigationAction.request)
            }
            return nil
        }
    }
}
