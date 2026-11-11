import SwiftUI

private struct CoffeeTier: Identifiable {
    let id: Int
    let title: String
    let price: String
    let note: String
}

private let coffeeTiers: [CoffeeTier] = [
    CoffeeTier(id: 1, title: "1 Cup", price: "$5", note: "A quick caffeine boost for late-night coding."),
    CoffeeTier(id: 3, title: "3 Cups", price: "$15", note: "Fuels a full weekend of polishing & updates."),
    CoffeeTier(id: 5, title: "5 Cups", price: "$25", note: "Pure legend status — keeps the builds flowing!")
]

struct SupportView: View {
    @State private var selectedCups: Int = 1
    @State private var copiedLink: Bool = false
    @State private var pulseGlow: Bool = false

    private let bmcURL = URL(string: "https://buymeacoffee.com/FINDEX")!
    private let bmcDisplay = "buymeacoffee.com/FINDEX"

    private let coffeeGold = Color(hex: 0xFFDD00)
    private let coffeeAmber = Color(hex: 0xFFB703)
    private let auralixPurple = Color(hex: 0xA855F7)
    private let auralixViolet = Color(hex: 0x7C3AED)

    private var activeTier: CoffeeTier {
        coffeeTiers.first { $0.id == selectedCups } ?? coffeeTiers[0]
    }

    var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(spacing: 16) {
                // Top Creator Pill
                HStack(spacing: 7) {
                    Circle()
                        .fill(coffeeGold)
                        .frame(width: 7, height: 7)
                    Text("SUPPORT THE CREATOR")
                        .font(.system(size: 11, weight: .bold))
                        .tracking(1.2)
                        .foregroundColor(.white)
                }
                .padding(.horizontal, 14)
                .padding(.vertical, 7)
                .background(
                    LinearGradient(
                        colors: [auralixPurple.opacity(0.22), coffeeGold.opacity(0.14)],
                        startPoint: .leading,
                        endPoint: .trailing
                    )
                )
                .clipShape(Capsule())
                .overlay(
                    Capsule().strokeBorder(
                        LinearGradient(
                            colors: [auralixPurple.opacity(0.60), coffeeGold.opacity(0.60)],
                            startPoint: .leading,
                            endPoint: .trailing
                        ),
                        lineWidth: 1
                    )
                )

                // Main Floating Glass Card
                VStack(spacing: 0) {
                    // Pulsing Emblem
                    ZStack {
                        Circle()
                            .fill(
                                RadialGradient(
                                    colors: [coffeeGold.opacity(0.45), auralixPurple.opacity(0.22), .clear],
                                    center: .center,
                                    startRadius: 4,
                                    endRadius: 50
                                )
                            )
                            .frame(width: 96, height: 96)
                            .scaleEffect(pulseGlow ? 1.07 : 0.94)
                            .opacity(pulseGlow ? 0.65 : 0.35)

                        Circle()
                            .fill(
                                LinearGradient(
                                    colors: [Color(hex: 0x2B1E3E), Color(hex: 0x1A1426)],
                                    startPoint: .topLeading,
                                    endPoint: .bottomTrailing
                                )
                            )
                            .frame(width: 76, height: 76)
                            .overlay(
                                Circle().strokeBorder(
                                    LinearGradient(
                                        colors: [coffeeGold, auralixPurple],
                                        startPoint: .topLeading,
                                        endPoint: .bottomTrailing
                                    ),
                                    lineWidth: 1.5
                                )
                            )

                        Image(systemName: "cup.and.saucer.fill")
                            .font(.system(size: 32))
                            .foregroundColor(coffeeGold)
                    }
                    .frame(height: 104)

                    Spacer().frame(height: 10)

                    // Handle Badge
                    Text("@FINDEX")
                        .font(.system(size: 11.5, weight: .heavy))
                        .tracking(1.0)
                        .foregroundColor(coffeeGold)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 4)
                        .background(Color.white.opacity(0.08))
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                        .overlay(
                            RoundedRectangle(cornerRadius: 12)
                                .strokeBorder(Color.white.opacity(0.14), lineWidth: 1)
                        )

                    Spacer().frame(height: 14)

                    Text("Enjoying the vibe?")
                        .font(.system(size: 24, weight: .heavy))
                        .foregroundColor(.white)
                        .multilineTextAlignment(.center)

                    Spacer().frame(height: 8)

                    Text("AURALIX is 100% free for everyone — crafted solo with late nights, good music, and lots of coffee. If it made your day better, feel free to drop a coffee. Zero pressure, always appreciated.")
                        .font(.system(size: 13.5))
                        .foregroundColor(Color(hex: 0xBDBDD0))
                        .multilineTextAlignment(.center)
                        .lineSpacing(3)

                    Spacer().frame(height: 22)

                    // Cup Selector
                    HStack(spacing: 10) {
                        ForEach(coffeeTiers) { tier in
                            cupTierButton(tier)
                        }
                    }

                    Spacer().frame(height: 10)

                    Text(activeTier.note)
                        .font(.system(size: 12))
                        .foregroundColor(Color(hex: 0x9E9EAF))
                        .multilineTextAlignment(.center)
                        .animation(.easeInOut(duration: 0.2), value: selectedCups)

                    Spacer().frame(height: 20)

                    // Primary Golden CTA
                    Button(action: {
                        UIApplication.shared.open(bmcURL)
                    }) {
                        HStack(spacing: 8) {
                            Image(systemName: "cup.and.saucer.fill")
                                .font(.system(size: 16, weight: .bold))
                            Text(selectedCups == 1 ? "Buy Me a Coffee" : "Buy \(selectedCups) Coffees")
                                .font(.system(size: 15.5, weight: .heavy))
                            Image(systemName: "arrow.up.right")
                                .font(.system(size: 13, weight: .bold))
                        }
                        .foregroundColor(Color(hex: 0x14110B))
                        .frame(maxWidth: .infinity)
                        .frame(height: 54)
                        .background(
                            LinearGradient(
                                colors: [coffeeGold, coffeeAmber],
                                startPoint: .leading,
                                endPoint: .trailing
                            )
                        )
                        .clipShape(RoundedRectangle(cornerRadius: 18))
                        .shadow(color: coffeeGold.opacity(0.40), radius: 12, x: 0, y: 5)
                    }

                    Spacer().frame(height: 12)

                    // Copy & Share Row
                    HStack(spacing: 10) {
                        Button(action: {
                            UIPasteboard.general.string = bmcURL.absoluteString
                            copiedLink = true
                            DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) {
                                copiedLink = false
                            }
                        }) {
                            HStack(spacing: 6) {
                                Image(systemName: copiedLink ? "checkmark" : "doc.on.doc")
                                    .font(.system(size: 12, weight: .bold))
                                    .foregroundColor(copiedLink ? Color(hex: 0x4ADE80) : coffeeGold)
                                Text(copiedLink ? "Link Copied!" : bmcDisplay)
                                    .font(.system(size: 11.5, weight: .semibold))
                                    .foregroundColor(copiedLink ? Color(hex: 0x4ADE80) : Color(hex: 0xE5E5F2))
                                    .lineLimit(1)
                            }
                            .frame(maxWidth: .infinity)
                            .padding(.horizontal, 12)
                            .padding(.vertical, 11)
                            .background(Color.white.opacity(0.06))
                            .clipShape(RoundedRectangle(cornerRadius: 14))
                            .overlay(
                                RoundedRectangle(cornerRadius: 14)
                                    .strokeBorder(Color.white.opacity(0.14), lineWidth: 1)
                            )
                        }

                        ShareLink(item: bmcURL, message: Text("Support Findex (creator of AURALIX) on Buy Me a Coffee")) {
                            HStack(spacing: 5) {
                                Image(systemName: "square.and.arrow.up")
                                    .font(.system(size: 12, weight: .semibold))
                                Text("Share")
                                    .font(.system(size: 11.5, weight: .semibold))
                            }
                            .foregroundColor(.white)
                            .padding(.horizontal, 14)
                            .padding(.vertical, 11)
                            .background(Color.white.opacity(0.06))
                            .clipShape(RoundedRectangle(cornerRadius: 14))
                            .overlay(
                                RoundedRectangle(cornerRadius: 14)
                                    .strokeBorder(Color.white.opacity(0.14), lineWidth: 1)
                            )
                        }
                    }
                }
                .padding(.horizontal, 22)
                .padding(.vertical, 26)
                .background(
                    LinearGradient(
                        colors: [Color(hex: 0x1F1630), Color(hex: 0x14111E), Color(hex: 0x0C0B13)],
                        startPoint: .top,
                        endPoint: .bottom
                    )
                )
                .clipShape(RoundedRectangle(cornerRadius: 28))
                .overlay(
                    RoundedRectangle(cornerRadius: 28)
                        .strokeBorder(
                            LinearGradient(
                                colors: [auralixPurple.opacity(0.75), coffeeGold.opacity(0.55), Color.white.opacity(0.18)],
                                startPoint: .topLeading,
                                endPoint: .bottomTrailing
                            ),
                            lineWidth: 1.2
                        )
                )
                .shadow(color: auralixViolet.opacity(0.28), radius: 18, x: 0, y: 8)

                // Minimal Footer
                HStack(spacing: 5) {
                    Text("Crafted with")
                        .font(.system(size: 12))
                        .foregroundColor(Color(hex: 0x7E7E92))
                    Image(systemName: "heart.fill")
                        .font(.system(size: 11))
                        .foregroundColor(auralixPurple)
                    Text("& caffeine by Findex")
                        .font(.system(size: 12))
                        .foregroundColor(Color(hex: 0x7E7E92))
                }
                .padding(.top, 4)
            }
            .padding(.horizontal, 18)
            .padding(.top, 18)
            .padding(.bottom, 120)
        }
        .onAppear {
            withAnimation(.easeInOut(duration: 2.2).repeatForever(autoreverses: true)) {
                pulseGlow = true
            }
        }
    }

    @ViewBuilder
    private func cupTierButton(_ tier: CoffeeTier) -> some View {
        let isSelected = selectedCups == tier.id
        Button(action: { selectedCups = tier.id }) {
            VStack(spacing: 3) {
                Text(tier.title)
                    .font(.system(size: 13.5, weight: .bold))
                    .foregroundColor(isSelected ? coffeeGold : .white)
                Text(tier.price)
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundColor(isSelected ? .white : Color(hex: 0x9E9EAF))
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 12)
            .background(isSelected ? coffeeGold.opacity(0.18) : Color.white.opacity(0.05))
            .clipShape(RoundedRectangle(cornerRadius: 18))
            .overlay(
                RoundedRectangle(cornerRadius: 18)
                    .strokeBorder(isSelected ? coffeeGold : Color.white.opacity(0.14), lineWidth: isSelected ? 1.5 : 1)
            )
            .animation(.easeInOut(duration: 0.18), value: isSelected)
        }
    }
}
