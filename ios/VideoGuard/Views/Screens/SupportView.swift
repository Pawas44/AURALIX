import SwiftUI

// ========================================================
// SUPPORT CONFIGURATION (Pawas Payment Handles)
// ========================================================
private let pawasUpiId = "findex13@nyes"
private let pawasName = "Pawas"
private let razorpayURL = URL(string: "https://razorpay.me/@pawas")!
private let bmcProfileURL = URL(string: "https://buymeacoffee.com/findex")!

private let mintAccent = Color(hex: 0x40DCA5)
private let razorpayAccent = Color(hex: 0x3395FF)
private let razorpayDark = Color(hex: 0x0C2038)
private let coffeeGold = Color(hex: 0xFFFFDD00)
private let bmcOrange = Color(hex: 0xFF813F)
private let coffeeDark = Color(hex: 0xFF231B05)
private let upiOrange = Color(hex: 0xF47920)
private let upiGreen = Color(hex: 0x00A859)

struct SupportView: View {
    @ObservedObject var playbackService = MediaPlaybackService.shared
    @State private var selectedAmount: String = "100"
    @State private var customAmount: String = ""
    @State private var copiedToast: Bool = false
    @State private var toastMessage: String = ""

    private let presetAmounts = ["50", "100", "250", "500"]

    private var effectiveAmount: String {
        customAmount.isEmpty ? selectedAmount : customAmount
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                // Top Header
                HStack(spacing: 12) {
                    ZStack {
                        Circle()
                            .fill(
                                LinearGradient(
                                    colors: [razorpayAccent, mintAccent],
                                    startPoint: .topLeading,
                                    endPoint: .bottomTrailing
                                )
                            )
                            .frame(width: 44, height: 44)
                        Image(systemName: "heart.fill")
                            .font(.system(size: 20, weight: .bold))
                            .foregroundColor(.white)
                    }

                    VStack(alignment: .leading, spacing: 2) {
                        Text("Support AURALIX")
                            .font(.system(size: 20, weight: .bold))
                            .foregroundColor(.white)
                        Text("Help keep the app fast, ad-free & updated")
                            .font(.system(size: 12))
                            .foregroundColor(Color(hex: 0x9E9EAF))
                    }
                }
                .padding(.horizontal, 4)

                // ========================================================
                // 1. UPI Payment Card (Direct Native UPI App Launch)
                // ========================================================
                VStack(alignment: .leading, spacing: 14) {
                    HStack {
                        Button(action: {
                            openUpiPayment(amount: effectiveAmount)
                        }) {
                            HStack(spacing: 10) {
                                OfficialUpiBadge()

                                VStack(alignment: .leading, spacing: 2) {
                                    Text("Pay with UPI")
                                        .font(.system(size: 16, weight: .bold))
                                        .foregroundColor(.white)
                                    Text("GPay, PhonePe, Paytm, BHIM, Cred")
                                        .font(.system(size: 11))
                                        .foregroundColor(Color(hex: 0xA0AEC0))
                                }
                            }
                        }

                        Spacer()

                        Button(action: {
                            UIPasteboard.general.string = pawasUpiId
                            triggerToast("UPI ID copied: \(pawasUpiId)")
                        }) {
                            HStack(spacing: 4) {
                                Image(systemName: "doc.on.doc")
                                    .font(.system(size: 11))
                                Text("Copy")
                                    .font(.system(size: 11, weight: .semibold))
                            }
                            .foregroundColor(razorpayAccent)
                            .padding(.horizontal, 10)
                            .padding(.vertical, 6)
                            .background(razorpayAccent.opacity(0.15))
                            .cornerRadius(10)
                        }
                    }

                    // UPI ID details pill
                    HStack {
                        Text("UPI ID: \(pawasUpiId)")
                            .font(.system(size: 12, weight: .medium))
                            .foregroundColor(Color(hex: 0xCBD5E1))
                        Spacer()
                        Text("Tap button to pay")
                            .font(.system(size: 11))
                            .foregroundColor(Color(hex: 0x94A3B8))
                    }
                    .padding(.horizontal, 12)
                    .padding(.vertical, 8)
                    .background(Color.white.opacity(0.05))
                    .cornerRadius(10)
                    .overlay(
                        RoundedRectangle(cornerRadius: 10)
                            .strokeBorder(Color.white.opacity(0.08), lineWidth: 1)
                    )

                    Text("Select Amount (INR):")
                        .font(.system(size: 12, weight: .medium))
                        .foregroundColor(Color(hex: 0xCBD5E1))

                    // Preset Amount Chips
                    HStack(spacing: 8) {
                        ForEach(presetAmounts, id: \.self) { amt in
                            let isSelected = selectedAmount == amt && customAmount.isEmpty
                            Button(action: {
                                selectedAmount = amt
                                customAmount = ""
                            }) {
                                Text("₹\(amt)")
                                    .font(.system(size: 13.5, weight: isSelected ? .bold : .medium))
                                    .foregroundColor(isSelected ? .white : Color(hex: 0xE2E8F0))
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, 10)
                                    .background(isSelected ? razorpayAccent : Color.white.opacity(0.08))
                                    .cornerRadius(12)
                                    .overlay(
                                        RoundedRectangle(cornerRadius: 12)
                                            .strokeBorder(isSelected ? razorpayAccent : Color.white.opacity(0.12), lineWidth: 1)
                                    )
                            }
                        }
                    }

                    // Action 1: Open UPI App Directly
                    Button(action: {
                        openUpiPayment(amount: effectiveAmount)
                    }) {
                        HStack(spacing: 10) {
                            OfficialUpiArrowsView()
                                .frame(width: 24, height: 16)
                            Text("Pay ₹\(effectiveAmount) via UPI App")
                        }
                        .font(.system(size: 14.5, weight: .bold))
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14)
                        .background(razorpayAccent)
                        .cornerRadius(14)
                    }

                    // Action 2: Web Payment Gateway Fallback
                    Button(action: {
                        UIApplication.shared.open(razorpayURL)
                    }) {
                        HStack(spacing: 6) {
                            Image(systemName: "arrow.up.right.square")
                            Text("Open Razorpay Web Portal ↗")
                        }
                        .font(.system(size: 12.5, weight: .medium))
                        .foregroundColor(Color(hex: 0xCBD5E1))
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 10)
                        .overlay(
                            RoundedRectangle(cornerRadius: 12)
                                .strokeBorder(Color.white.opacity(0.2), lineWidth: 1)
                        )
                    }
                }
                .padding(18)
                .background(razorpayDark)
                .cornerRadius(20)
                .overlay(
                    RoundedRectangle(cornerRadius: 20)
                        .strokeBorder(razorpayAccent.opacity(0.4), lineWidth: 1.2)
                )

                // ========================================================
                // 2. Buy Me a Coffee Card (Opens in Browser)
                // ========================================================
                VStack(alignment: .leading, spacing: 14) {
                    HStack {
                        Button(action: {
                            UIApplication.shared.open(bmcProfileURL)
                        }) {
                            HStack(spacing: 10) {
                                OfficialBmcBadge()

                                VStack(alignment: .leading, spacing: 2) {
                                    Text("Buy Me a Coffee")
                                        .font(.system(size: 16, weight: .bold))
                                        .foregroundColor(.white)
                                    Text("International cards, PayPal & Apple Pay")
                                        .font(.system(size: 11))
                                        .foregroundColor(Color(hex: 0xA0AEC0))
                                }
                            }
                        }

                        Spacer()

                        Button(action: {
                            UIPasteboard.general.string = bmcProfileURL.absoluteString
                            triggerToast("BMC link copied to clipboard")
                        }) {
                            HStack(spacing: 4) {
                                Image(systemName: "doc.on.doc")
                                    .font(.system(size: 11))
                                Text("Copy")
                                    .font(.system(size: 11, weight: .semibold))
                            }
                            .foregroundColor(coffeeGold)
                            .padding(.horizontal, 10)
                            .padding(.vertical, 6)
                            .background(coffeeGold.opacity(0.15))
                            .cornerRadius(10)
                        }
                    }

                    Text("Support AURALIX from anywhere in the world using your preferred international payment method.")
                        .font(.system(size: 12))
                        .foregroundColor(Color(hex: 0xCBD5E1))
                        .lineSpacing(3)

                    // Official Buy Me a Coffee Widget Button matching user screenshot
                    OfficialBmcWidgetButton {
                        UIApplication.shared.open(bmcProfileURL)
                    }
                }
                .padding(18)
                .background(coffeeDark)
                .cornerRadius(20)
                .overlay(
                    RoundedRectangle(cornerRadius: 20)
                        .strokeBorder(coffeeGold.opacity(0.4), lineWidth: 1.2)
                )

                // ========================================================
                // 3. Why Support AURALIX
                // ========================================================
                VStack(alignment: .leading, spacing: 8) {
                    Text("Why Your Support Matters")
                        .font(.system(size: 14, weight: .bold))
                        .foregroundColor(.white)
                    Text("• 100% Ad-Free & Privacy-First: No user tracking or injected ads.\n• Spotify-Level AI Recommendations: Continuous improvements to algorithms.\n• High-Fidelity Audio & Offline Playback: Free unlimited music for everyone.")
                        .font(.system(size: 11.5))
                        .foregroundColor(Color(hex: 0x9E9EAF))
                        .lineSpacing(4)
                }
                .padding(16)
                .background(Color.white.opacity(0.04))
                .cornerRadius(18)
                .overlay(
                    RoundedRectangle(cornerRadius: 18)
                        .strokeBorder(Color.white.opacity(0.08), lineWidth: 1)
                )
            }
            .padding(.horizontal, 16)
            .padding(.top, 8)
            .padding(.bottom, playbackService.currentTrack != nil ? 148 : 84)
        }
        .overlay(
            Group {
                if copiedToast {
                    VStack {
                        Spacer()
                        Text(toastMessage)
                            .font(.system(size: 12, weight: .medium))
                            .foregroundColor(.white)
                            .padding(.horizontal, 16)
                            .padding(.vertical, 10)
                            .background(Color.black.opacity(0.85))
                            .cornerRadius(20)
                            .padding(.bottom, 96)
                    }
                    .transition(.opacity)
                }
            }
        )
    }

    private func triggerToast(_ msg: String) {
        toastMessage = msg
        withAnimation { copiedToast = true }
        DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) {
            withAnimation { copiedToast = false }
        }
    }

    private func openUpiPayment(amount: String) {
        let upiString = "upi://pay?pa=\(pawasUpiId)&pn=\(pawasName)&am=\(amount)&cu=INR&tn=Support%20AURALIX"
        if let encoded = upiString.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed),
           let upiURL = URL(string: encoded),
           UIApplication.shared.canOpenURL(upiURL) {
            UIApplication.shared.open(upiURL)
        } else {
            // Fallback to Razorpay web URL
            UIApplication.shared.open(razorpayURL)
        }
    }
}

// ========================================================
// AUTHENTIC BRAND ICONS (UPI & BMC)
// ========================================================

/**
 * Authentic NPCI UPI Logo Badge with orange & green directional chevrons
 * and bold italic typography.
 */
struct OfficialUpiBadge: View {
    var body: some View {
        HStack(spacing: 5) {
            OfficialUpiArrowsView()
                .frame(width: 20, height: 14)

            Text("UPI")
                .font(.system(size: 14, weight: .black, design: .default))
                .italic()
                .foregroundColor(.white)
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 6)
        .background(Color.white.opacity(0.08))
        .cornerRadius(10)
        .overlay(
            RoundedRectangle(cornerRadius: 10)
                .strokeBorder(Color.white.opacity(0.12), lineWidth: 1)
        )
    }
}

/**
 * NPCI UPI Dual Slanted Chevrons drawn using SwiftUI GeometryReader & Path.
 */
struct OfficialUpiArrowsView: View {
    var body: some View {
        GeometryReader { geo in
            let w = geo.size.width
            let h = geo.size.height

            // Left / Orange Chevron (45° slant)
            Path { p in
                p.move(to: CGPoint(x: 0, y: h))
                p.addLine(to: CGPoint(x: w * 0.28, y: h))
                p.addLine(to: CGPoint(x: w * 0.55, y: 0))
                p.addLine(to: CGPoint(x: w * 0.27, y: 0))
                p.closeSubpath()
            }
            .fill(upiOrange)

            // Right / Green Chevron (parallel slant)
            Path { p in
                p.move(to: CGPoint(x: w * 0.38, y: h))
                p.addLine(to: CGPoint(x: w * 0.66, y: h))
                p.addLine(to: CGPoint(x: w * 0.93, y: 0))
                p.addLine(to: CGPoint(x: w * 0.65, y: 0))
                p.closeSubpath()
            }
            .fill(upiGreen)
        }
    }
}

/**
 * Official Buy Me a Coffee Widget Button matching user screenshot
 */
struct OfficialBmcWidgetButton: View {
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 12) {
                BmcCupWidgetIcon()
                    .frame(width: 28, height: 32)

                Text("Buy me a coffee")
                    .font(.system(size: 20, weight: .bold, design: .serif))
                    .foregroundColor(.white)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 14)
            .background(bmcOrange)
            .cornerRadius(16)
            .shadow(color: Color.black.opacity(0.3), radius: 6, x: 0, y: 3)
        }
    }
}

struct BmcCupWidgetIcon: View {
    var body: some View {
        ZStack {
            Image(systemName: "cup.and.saucer.fill")
                .font(.system(size: 20, weight: .bold))
                .foregroundColor(Color(hex: 0xFFFFDD00))
        }
    }
}

/**
 * Authentic Buy Me A Coffee Logo Badge with iconic orange background.
 */
struct OfficialBmcBadge: View {
    var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: 10)
                .fill(bmcOrange)
                .frame(width: 38, height: 38)

            Image(systemName: "cup.and.saucer.fill")
                .font(.system(size: 18, weight: .bold))
                .foregroundColor(.white)
        }
    }
}
