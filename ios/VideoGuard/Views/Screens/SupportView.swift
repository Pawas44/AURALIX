import SwiftUI

private let razorpayURL = URL(string: "https://razorpay.me/@pawas")!
private let bmcProfileURL = URL(string: "https://buymeacoffee.com/FINDEX")!

private let mintAccent = Color(hex: 0x40DCA5)
private let razorpayAccent = Color(hex: 0x3395FF)
private let razorpayDark = Color(hex: 0x0C2038)
private let coffeeGold = Color(hex: 0xFFDD00)
private let coffeeDark = Color(hex: 0x261D02)

struct SupportView: View {
    @ObservedObject var playbackService = MediaPlaybackService.shared
    @State private var selectedAmount: String = "100"
    @State private var customAmount: String = ""
    @State private var copiedToast: Bool = false

    private let presetAmounts = ["50", "100", "250", "500"]

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

                // 1. UPI & Razorpay Native Card
                VStack(alignment: .leading, spacing: 14) {
                    HStack {
                        ZStack {
                            RoundedRectangle(cornerRadius: 10)
                                .fill(razorpayAccent.opacity(0.2))
                                .frame(width: 36, height: 36)
                            Image(systemName: "creditcard.fill")
                                .font(.system(size: 17))
                                .foregroundColor(razorpayAccent)
                        }

                        VStack(alignment: .leading, spacing: 2) {
                            Text("Pay via UPI / Razorpay")
                                .font(.system(size: 16, weight: .bold))
                                .foregroundColor(.white)
                            Text("GPay, PhonePe, Paytm, Cards (@pawas)")
                                .font(.system(size: 11))
                                .foregroundColor(Color(hex: 0xA0AEC0))
                        }

                        Spacer()

                        Button(action: {
                            UIPasteboard.general.string = razorpayURL.absoluteString
                            triggerToast()
                        }) {
                            Image(systemName: "doc.on.doc")
                                .font(.system(size: 14))
                                .foregroundColor(razorpayAccent)
                                .padding(8)
                                .background(razorpayAccent.opacity(0.15))
                                .clipShape(Circle())
                        }
                    }

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

                    // Action: Open in Browser via Razorpay
                    let effectiveAmount = customAmount.isEmpty ? selectedAmount : customAmount
                    Button(action: {
                        UIApplication.shared.open(razorpayURL)
                    }) {
                        HStack(spacing: 8) {
                            Image(systemName: "bolt.fill")
                            Text("Pay ₹\(effectiveAmount) via UPI / Razorpay ↗")
                        }
                        .font(.system(size: 14, weight: .bold))
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14)
                        .background(razorpayAccent)
                        .cornerRadius(14)
                    }
                }
                .padding(18)
                .background(razorpayDark)
                .cornerRadius(20)
                .overlay(
                    RoundedRectangle(cornerRadius: 20)
                        .strokeBorder(razorpayAccent.opacity(0.4), lineWidth: 1.2)
                )

                // 2. Buy Me a Coffee Card (Opens in Browser)
                VStack(alignment: .leading, spacing: 14) {
                    HStack {
                        ZStack {
                            RoundedRectangle(cornerRadius: 10)
                                .fill(coffeeGold.opacity(0.2))
                                .frame(width: 36, height: 36)
                            Image(systemName: "cup.and.saucer.fill")
                                .font(.system(size: 17))
                                .foregroundColor(coffeeGold)
                        }

                        VStack(alignment: .leading, spacing: 2) {
                            Text("Buy Me a Coffee")
                                .font(.system(size: 16, weight: .bold))
                                .foregroundColor(.white)
                            Text("buymeacoffee.com/FINDEX")
                                .font(.system(size: 11))
                                .foregroundColor(Color(hex: 0xA0AEC0))
                        }

                        Spacer()

                        Button(action: {
                            UIPasteboard.general.string = bmcProfileURL.absoluteString
                            triggerToast()
                        }) {
                            Image(systemName: "doc.on.doc")
                                .font(.system(size: 14))
                                .foregroundColor(coffeeGold)
                                .padding(8)
                                .background(coffeeGold.opacity(0.15))
                                .clipShape(Circle())
                        }
                    }

                    Text("Ideal for international supporters via Credit/Debit Cards, PayPal, and Apple Pay.")
                        .font(.system(size: 12))
                        .foregroundColor(Color(hex: 0xCBD5E1))
                        .lineSpacing(3)

                    // Button to open Safari
                    Button(action: {
                        UIApplication.shared.open(bmcProfileURL)
                    }) {
                        HStack(spacing: 8) {
                            Image(systemName: "cup.and.saucer.fill")
                            Text("Open Buy Me a Coffee in Safari ↗")
                        }
                        .font(.system(size: 13.5, weight: .bold))
                        .foregroundColor(Color(hex: 0x1E1702))
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14)
                        .background(coffeeGold)
                        .cornerRadius(14)
                    }
                }
                .padding(18)
                .background(coffeeDark)
                .cornerRadius(20)
                .overlay(
                    RoundedRectangle(cornerRadius: 20)
                        .strokeBorder(coffeeGold.opacity(0.4), lineWidth: 1.2)
                )

                // 3. Why Support AURALIX
                VStack(alignment: .leading, spacing: 8) {
                    Text("Why Your Support Matters")
                        .font(.system(size: 14, weight: .bold))
                        .foregroundColor(.white)
                    Text("• 100% Ad-Free & Privacy-First: No user tracking or injected ads.\n• Spotify-Level AI Recommendations: Continuous improvements to algorithms.\n• YouTube InnerTube Streaming & Downloads: Maintenance and bandwidth.")
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
    }

    private func triggerToast() {
        copiedToast = true
        DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) {
            copiedToast = false
        }
    }
}
