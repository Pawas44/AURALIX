import SwiftUI
import WebKit

private let razorpayURL = URL(string: "https://razorpay.me/@pawas")!
private let bmcProfileURL = URL(string: "https://buymeacoffee.com/FINDEX")!
private let bmcWidgetURL = URL(string: "https://www.buymeacoffee.com/widget/page/FINDEX?description=Support%20me%20on%20Buy%20me%20a%20coffee!&color=%2340DCA5")!

private let mintAccent = Color(hex: 0x40DCA5)
private let razorpayAccent = Color(hex: 0x3395FF)
private let coffeeGold = Color(hex: 0xFFDD00)

enum SupportPortalType {
    case razorpayUPI
    case buyMeACoffee
}

struct SupportView: View {
    @ObservedObject var playbackService = MediaPlaybackService.shared
    @State private var selectedPortal: SupportPortalType = .razorpayUPI
    @State private var isLoading: Bool = true
    @State private var hasError: Bool = false
    @State private var reloadTrigger: Int = 0

    private var activeURL: URL {
        selectedPortal == .razorpayUPI ? razorpayURL : bmcWidgetURL
    }

    var body: some View {
        VStack(spacing: 10) {
            // Header Bar
            HStack(spacing: 10) {
                ZStack {
                    Circle()
                        .fill(
                            LinearGradient(
                                colors: [razorpayAccent, mintAccent],
                                startPoint: .topLeading,
                                endPoint: .bottomTrailing
                            )
                        )
                        .frame(width: 36, height: 36)
                    Image(systemName: "bolt.fill")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundColor(.white)
                }

                VStack(alignment: .leading, spacing: 1) {
                    Text("Support AURALIX")
                        .font(.system(size: 18, weight: .bold))
                        .foregroundColor(.white)
                    Text(selectedPortal == .razorpayUPI ? "razorpay.me/@pawas (UPI & Cards)" : "buymeacoffee.com/FINDEX")
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
                        let target = selectedPortal == .razorpayUPI ? razorpayURL : bmcProfileURL
                        UIApplication.shared.open(target)
                    }) {
                        Image(systemName: "safari")
                            .font(.system(size: 15, weight: .semibold))
                            .foregroundColor(selectedPortal == .razorpayUPI ? razorpayAccent : mintAccent)
                            .frame(width: 36, height: 36)
                            .background(Color.white.opacity(0.08))
                            .clipShape(Circle())
                            .overlay(Circle().strokeBorder(LiquidGlassDefaults.borderSubtle, lineWidth: 1))
                    }
                }
            }
            .padding(.horizontal, 4)

            // Portal Selection Buttons
            HStack(spacing: 10) {
                // Razorpay / UPI
                Button(action: {
                    if selectedPortal != .razorpayUPI {
                        selectedPortal = .razorpayUPI
                        hasError = false
                        isLoading = true
                        reloadTrigger += 1
                    }
                }) {
                    HStack(spacing: 8) {
                        Image(systemName: "creditcard.fill")
                            .foregroundColor(razorpayAccent)
                        VStack(alignment: .leading, spacing: 2) {
                            Text("UPI / Razorpay")
                                .font(.system(size: 13, weight: .bold))
                                .foregroundColor(.white)
                            Text("GPay, PhonePe, Paytm")
                                .font(.system(size: 10.5))
                                .foregroundColor(Color(hex: 0xA0AEC0))
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 10)
                    .background(
                        selectedPortal == .razorpayUPI ?
                        LinearGradient(colors: [Color(hex: 0x0F2B48), Color(hex: 0x13365C)], startPoint: .topLeading, endPoint: .bottomTrailing) :
                        LinearGradient(colors: [Color(hex: 0x161826), Color(hex: 0x1C1E30)], startPoint: .topLeading, endPoint: .bottomTrailing)
                    )
                    .clipShape(RoundedRectangle(cornerRadius: 16))
                    .overlay(
                        RoundedRectangle(cornerRadius: 16)
                            .strokeBorder(selectedPortal == .razorpayUPI ? razorpayAccent : Color.white.opacity(0.08), lineWidth: 1.2)
                    )
                }

                // Buy Me a Coffee
                Button(action: {
                    if selectedPortal != .buyMeACoffee {
                        selectedPortal = .buyMeACoffee
                        hasError = false
                        isLoading = true
                        reloadTrigger += 1
                    }
                }) {
                    HStack(spacing: 8) {
                        Image(systemName: "cup.and.saucer.fill")
                            .foregroundColor(coffeeGold)
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Buy Me a Coffee")
                                .font(.system(size: 13, weight: .bold))
                                .foregroundColor(.white)
                            Text("buymeacoffee.com")
                                .font(.system(size: 10.5))
                                .foregroundColor(Color(hex: 0xA0AEC0))
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 10)
                    .background(
                        selectedPortal == .buyMeACoffee ?
                        LinearGradient(colors: [Color(hex: 0x2E2405), Color(hex: 0x3D3008)], startPoint: .topLeading, endPoint: .bottomTrailing) :
                        LinearGradient(colors: [Color(hex: 0x161826), Color(hex: 0x1C1E30)], startPoint: .topLeading, endPoint: .bottomTrailing)
                    )
                    .clipShape(RoundedRectangle(cornerRadius: 16))
                    .overlay(
                        RoundedRectangle(cornerRadius: 16)
                            .strokeBorder(selectedPortal == .buyMeACoffee ? coffeeGold : Color.white.opacity(0.08), lineWidth: 1.2)
                    )
                }
            }

            // Direct Open In Browser Link
            Button(action: {
                let target = selectedPortal == .razorpayUPI ? razorpayURL : bmcProfileURL
                UIApplication.shared.open(target)
            }) {
                HStack {
                    Image(systemName: "arrow.up.right.square")
                        .font(.system(size: 14))
                        .foregroundColor(selectedPortal == .razorpayUPI ? razorpayAccent : mintAccent)
                    Text(selectedPortal == .razorpayUPI ? "Open https://razorpay.me/@pawas directly" : "Open buymeacoffee.com/FINDEX in Safari")
                        .font(.system(size: 12, weight: .medium))
                        .foregroundColor(Color(hex: 0xE2E8F0))
                    Spacer()
                    Text("OPEN ↗")
                        .font(.system(size: 11, weight: .bold))
                        .foregroundColor(selectedPortal == .razorpayUPI ? razorpayAccent : mintAccent)
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 8)
                .background(Color.white.opacity(0.05))
                .clipShape(RoundedRectangle(cornerRadius: 12))
                .overlay(RoundedRectangle(cornerRadius: 12).strokeBorder(Color.white.opacity(0.08), lineWidth: 1))
            }

            // Embedded Web Portal (With native UPI deep-link routing)
            ZStack {
                Color.white

                SupportPortalWebView(
                    url: activeURL,
                    reloadTrigger: reloadTrigger,
                    isLoading: $isLoading,
                    hasError: $hasError
                )

                if isLoading {
                    Color(hex: 0xF9FAFB)
                    VStack(spacing: 12) {
                        ProgressView()
                            .tint(selectedPortal == .razorpayUPI ? razorpayAccent : mintAccent)
                            .scaleEffect(1.1)
                        Text(selectedPortal == .razorpayUPI ? "Loading Razorpay & UPI..." : "Loading Buy Me a Coffee...")
                            .font(.system(size: 13, weight: .medium))
                            .foregroundColor(Color(hex: 0x4B5563))
                    }
                }

                if hasError {
                    Color(hex: 0x13141F)
                    VStack(spacing: 12) {
                        Text("Unable to load portal right now")
                            .font(.system(size: 15, weight: .semibold))
                            .foregroundColor(.white)
                        HStack(spacing: 12) {
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
                                    .background(selectedPortal == .razorpayUPI ? razorpayAccent : mintAccent)
                                    .clipShape(RoundedRectangle(cornerRadius: 12))
                            }

                            Button(action: {
                                UIApplication.shared.open(activeURL)
                            }) {
                                Text("Open in Browser")
                                    .font(.system(size: 13.5, weight: .bold))
                                    .foregroundColor(.white)
                                    .padding(.horizontal, 20)
                                    .padding(.vertical, 10)
                                    .background(Color.white.opacity(0.12))
                                    .clipShape(RoundedRectangle(cornerRadius: 12))
                            }
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

private struct SupportPortalWebView: UIViewRepresentable {
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
        if context.coordinator.lastReloadTrigger != reloadTrigger || context.coordinator.currentURL != url {
            context.coordinator.lastReloadTrigger = reloadTrigger
            context.coordinator.currentURL = url
            uiView.load(URLRequest(url: url))
        }
    }

    class Coordinator: NSObject, WKNavigationDelegate, WKUIDelegate {
        var parent: SupportPortalWebView
        var lastReloadTrigger: Int = 0
        var currentURL: URL?

        init(_ parent: SupportPortalWebView) {
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
