import SwiftUI

struct SupportView: View {
    @State private var selectedCups: Int = 1
    @State private var copiedLink: Bool = false

    private let bmcURL = URL(string: "https://buymeacoffee.com/FINDEX")!
    private let bmcDisplay = "buymeacoffee.com/FINDEX"
    private let githubURL = URL(string: "https://github.com/Pawas44/AURALIX")!

    private let coffeeGold = Color(hex: 0xFFDD00)
    private let coffeeAmber = Color(hex: 0xFFB703)
    private let auralixPurple = Color(hex: 0x9D4EDD)

    var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(spacing: 16) {
                // Header
                HStack(alignment: .center) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Support AURALIX")
                            .font(.system(size: 24, weight: .heavy))
                            .foregroundColor(.white)
                        Text("Created by Findex")
                            .font(.system(size: 13, weight: .medium))
                            .foregroundColor(Color(hex: 0x9E9EAF))
                    }

                    Spacer()

                    HStack(spacing: 6) {
                        Image(systemName: "cup.and.saucer.fill")
                            .font(.system(size: 13))
                        Text("FINDEX")
                            .font(.system(size: 12, weight: .bold))
                            .tracking(0.8)
                    }
                    .foregroundColor(coffeeGold)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 6)
                    .background(coffeeGold.opacity(0.14))
                    .clipShape(RoundedRectangle(cornerRadius: 16))
                    .overlay(
                        RoundedRectangle(cornerRadius: 16)
                            .strokeBorder(coffeeGold.opacity(0.45), lineWidth: 1)
                    )
                }

                // Hero Buy Me a Coffee Card
                VStack(spacing: 16) {
                    ZStack {
                        Circle()
                            .fill(
                                RadialGradient(
                                    colors: [coffeeGold.opacity(0.28), auralixPurple.opacity(0.18)],
                                    center: .center,
                                    startRadius: 4,
                                    endRadius: 38
                                )
                            )
                            .frame(width: 72, height: 72)
                            .overlay(
                                Circle().strokeBorder(coffeeGold.opacity(0.70), lineWidth: 1.5)
                            )

                        Image(systemName: "cup.and.saucer.fill")
                            .font(.system(size: 32))
                            .foregroundColor(coffeeGold)
                    }

                    VStack(spacing: 8) {
                        Text("Fuel AURALIX's Development")
                            .font(.system(size: 20, weight: .bold))
                            .foregroundColor(.white)
                            .multilineTextAlignment(.center)

                        Text("Building, testing, and maintaining smooth Android & iOS builds takes time and resources. If AURALIX helps you enjoy music and videos seamlessly, consider buying me a coffee!")
                            .font(.system(size: 13.5))
                            .foregroundColor(Color(hex: 0xB8B8C8))
                            .multilineTextAlignment(.center)
                            .lineSpacing(3)
                    }

                    // Cup Selector
                    HStack(spacing: 10) {
                        cupTierButton(cups: 1, title: "1 Cup", price: "$5")
                        cupTierButton(cups: 3, title: "3 Cups", price: "$15")
                        cupTierButton(cups: 5, title: "5 Cups", price: "$25")
                    }

                    // Primary CTA
                    Button(action: {
                        UIApplication.shared.open(bmcURL)
                    }) {
                        HStack(spacing: 8) {
                            Image(systemName: "cup.and.saucer.fill")
                                .font(.system(size: 16, weight: .bold))
                            Text(selectedCups == 1 ? "Buy Me a Coffee" : "Buy \(selectedCups) Coffees")
                                .font(.system(size: 15, weight: .heavy))
                            Image(systemName: "arrow.up.right")
                                .font(.system(size: 13, weight: .bold))
                        }
                        .foregroundColor(Color(hex: 0x14110B))
                        .frame(maxWidth: .infinity)
                        .frame(height: 52)
                        .background(
                            LinearGradient(
                                colors: [coffeeGold, coffeeAmber],
                                startPoint: .leading,
                                endPoint: .trailing
                            )
                        )
                        .clipShape(RoundedRectangle(cornerRadius: 16))
                        .shadow(color: coffeeGold.opacity(0.35), radius: 10, x: 0, y: 4)
                    }

                    // Direct Link + Copy Pill
                    Button(action: {
                        UIPasteboard.general.string = bmcURL.absoluteString
                        copiedLink = true
                        DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) {
                            copiedLink = false
                        }
                    }) {
                        HStack {
                            Text(bmcDisplay)
                                .font(.system(size: 12.5, weight: .semibold))
                                .foregroundColor(Color(hex: 0xE2E2EE))
                            Spacer()
                            HStack(spacing: 4) {
                                Image(systemName: copiedLink ? "checkmark" : "doc.on.doc")
                                    .font(.system(size: 12, weight: .bold))
                                Text(copiedLink ? "Copied!" : "Copy")
                                    .font(.system(size: 12, weight: .bold))
                            }
                            .foregroundColor(copiedLink ? Color(hex: 0x4ADE80) : coffeeGold)
                        }
                        .padding(.horizontal, 14)
                        .padding(.vertical, 10)
                        .background(Color.white.opacity(0.06))
                        .clipShape(RoundedRectangle(cornerRadius: 14))
                        .overlay(
                            RoundedRectangle(cornerRadius: 14)
                                .strokeBorder(LiquidGlassDefaults.borderSubtle, lineWidth: 1)
                        )
                    }
                }
                .padding(22)
                .background(
                    LinearGradient(
                        colors: [Color(hex: 0x23192D), Color(hex: 0x15121E), Color(hex: 0x121017)],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                )
                .clipShape(RoundedRectangle(cornerRadius: 24))
                .overlay(
                    RoundedRectangle(cornerRadius: 24)
                        .strokeBorder(
                            LinearGradient(
                                colors: [coffeeGold.opacity(0.65), auralixPurple.opacity(0.40), Color.white.opacity(0.18)],
                                startPoint: .topLeading,
                                endPoint: .bottomTrailing
                            ),
                            lineWidth: 1
                        )
                )
                .shadow(color: coffeeGold.opacity(0.18), radius: 14, x: 0, y: 6)

                // What Your Support Powers Card
                VStack(alignment: .leading, spacing: 14) {
                    Text("What Your Support Powers")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundColor(.white)

                    supportFeatureRow(
                        icon: "music.note",
                        accent: auralixPurple,
                        title: "Ad-Free Streaming",
                        subtitle: "Clean YouTube & YT Music experience without interruptions."
                    )

                    supportFeatureRow(
                        icon: "headphones",
                        accent: coffeeGold,
                        title: "Background & Lock Screen",
                        subtitle: "Reliable audio playback, media controls, and synced progress."
                    )

                    supportFeatureRow(
                        icon: "sparkles",
                        accent: Color(hex: 0x38BDF8),
                        title: "Android & iOS Releases",
                        subtitle: "Ongoing updates, UI polish, and automated builds for both platforms."
                    )
                }
                .padding(18)
                .liquidGlass(cornerRadius: 22)

                // GitHub Link Row
                Button(action: {
                    UIApplication.shared.open(githubURL)
                }) {
                    HStack {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("AURALIX on GitHub")
                                .font(.system(size: 14, weight: .semibold))
                                .foregroundColor(.white)
                            Text("github.com/Pawas44/AURALIX")
                                .font(.system(size: 12))
                                .foregroundColor(Color(hex: 0x9E9EAF))
                        }
                        Spacer()
                        Image(systemName: "arrow.up.right")
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundColor(Color(hex: 0x9E9EAF))
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 14)
                    .liquidGlass(cornerRadius: 18)
                }
            }
            .padding(.horizontal, 16)
            .padding(.top, 12)
            .padding(.bottom, 120)
        }
    }

    @ViewBuilder
    private func cupTierButton(cups: Int, title: String, price: String) -> some View {
        let isSelected = selectedCups == cups
        Button(action: { selectedCups = cups }) {
            VStack(spacing: 2) {
                Text(title)
                    .font(.system(size: 13, weight: .bold))
                    .foregroundColor(isSelected ? coffeeGold : .white)
                Text(price)
                    .font(.system(size: 11, weight: .medium))
                    .foregroundColor(isSelected ? .white : Color(hex: 0x9E9EAF))
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 10)
            .background(isSelected ? coffeeGold.opacity(0.18) : Color.white.opacity(0.06))
            .clipShape(RoundedRectangle(cornerRadius: 16))
            .overlay(
                RoundedRectangle(cornerRadius: 16)
                    .strokeBorder(isSelected ? coffeeGold : Color.white.opacity(0.14), lineWidth: isSelected ? 1.5 : 1)
            )
        }
    }

    @ViewBuilder
    private func supportFeatureRow(icon: String, accent: Color, title: String, subtitle: String) -> some View {
        HStack(spacing: 12) {
            ZStack {
                RoundedRectangle(cornerRadius: 12)
                    .fill(accent.opacity(0.16))
                    .frame(width: 40, height: 40)
                    .overlay(
                        RoundedRectangle(cornerRadius: 12)
                            .strokeBorder(accent.opacity(0.40), lineWidth: 1)
                    )
                Image(systemName: icon)
                    .font(.system(size: 17))
                    .foregroundColor(accent)
            }

            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundColor(.white)
                Text(subtitle)
                    .font(.system(size: 12))
                    .foregroundColor(Color(hex: 0x9E9EAF))
                    .fixedSize(horizontal: false, vertical: true)
            }
            Spacer(minLength: 0)
        }
    }
}

