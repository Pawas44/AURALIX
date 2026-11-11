import SwiftUI

private struct SupportOption: Identifiable {
    let id: Int
    let emoji: String
    let label: String
    let price: String
}

private let supportOptions: [SupportOption] = [
    SupportOption(id: 1, emoji: "☕", label: "Buy 1 Coffee", price: "$5"),
    SupportOption(id: 3, emoji: "🫶", label: "Buy 3 Coffees", price: "$15"),
    SupportOption(id: 5, emoji: "⚡", label: "Buy 5 Coffees", price: "$25")
]

struct SupportView: View {
    @State private var selectedCups: Int = 1
    @State private var copiedLink: Bool = false

    private let bmcURL = URL(string: "https://buymeacoffee.com/FINDEX")!
    private let bmcDisplay = "buymeacoffee.com/FINDEX"

    private var selectedOption: SupportOption {
        supportOptions.first { $0.id == selectedCups } ?? supportOptions[0]
    }

    var body: some View {
        GeometryReader { geo in
            ScrollView(showsIndicators: false) {
                VStack {
                    Spacer(minLength: 20)

                ZStack(alignment: .top) {
                    // Card Body
                    VStack(spacing: 0) {
                        Text("Support Findex")
                            .font(.system(size: 21, weight: .bold))
                            .foregroundColor(.white)

                        Spacer().frame(height: 4)

                        Text("Creator of AURALIX")
                            .font(.system(size: 13, weight: .medium))
                            .foregroundColor(Color(hex: 0x8C8FA3))

                        Spacer().frame(height: 22)

                        // Clean Selectable Rows
                        VStack(spacing: 10) {
                            ForEach(supportOptions) { option in
                                let isSelected = selectedCups == option.id
                                Button(action: {
                                    selectedCups = option.id
                                }) {
                                    HStack {
                                        HStack(spacing: 10) {
                                            Text(option.emoji)
                                                .font(.system(size: 15))
                                            Text(option.label)
                                                .font(.system(size: 14.5, weight: .medium))
                                                .foregroundColor(isSelected ? .white : Color(hex: 0xD6D8E5))
                                                .lineLimit(1)
                                        }
                                        Spacer()
                                        Text(option.price)
                                            .font(.system(size: 14.5, weight: .bold))
                                            .foregroundColor(isSelected ? Color(hex: 0xFFDD00) : .white)
                                    }
                                    .padding(.horizontal, 16)
                                    .padding(.vertical, 14)
                                    .background(isSelected ? Color(hex: 0x211E33) : Color(hex: 0x1D1E2A))
                                    .clipShape(RoundedRectangle(cornerRadius: 12))
                                    .overlay(
                                        RoundedRectangle(cornerRadius: 12)
                                            .strokeBorder(
                                                isSelected ? Color(hex: 0x8B5CF6).opacity(0.75) : Color.white.opacity(0.05),
                                                lineWidth: 1
                                            )
                                    )
                                }
                            }
                        }

                        Spacer().frame(height: 18)

                        // Discreet Link Bar
                        Button(action: {
                            UIPasteboard.general.string = bmcURL.absoluteString
                            copiedLink = true
                            DispatchQueue.main.asyncAfter(deadline: .now() + 1.8) {
                                copiedLink = false
                            }
                        }) {
                            HStack {
                                Text(bmcDisplay)
                                    .font(.system(size: 13))
                                    .foregroundColor(Color(hex: 0x9EA2B7))
                                    .lineLimit(1)
                                Spacer()
                                HStack(spacing: 5) {
                                    Image(systemName: copiedLink ? "checkmark" : "doc.on.doc")
                                        .font(.system(size: 11.5, weight: .semibold))
                                    Text(copiedLink ? "Copied" : "Copy")
                                        .font(.system(size: 12, weight: .semibold))
                                }
                                .foregroundColor(copiedLink ? Color(hex: 0x4ADE80) : Color(hex: 0x9EA2B7))
                            }
                            .padding(.horizontal, 14)
                            .padding(.vertical, 12)
                            .background(Color(hex: 0x1B1C27))
                            .clipShape(RoundedRectangle(cornerRadius: 12))
                            .overlay(
                                RoundedRectangle(cornerRadius: 12)
                                    .strokeBorder(Color.white.opacity(0.06), lineWidth: 1)
                            )
                        }

                        Spacer().frame(height: 14)

                        // Clean Primary Action Button
                        Button(action: {
                            UIApplication.shared.open(bmcURL)
                        }) {
                            Text("\(selectedOption.label) for \(selectedOption.price)")
                                .font(.system(size: 15, weight: .bold))
                                .foregroundColor(Color(hex: 0x111111))
                                .frame(maxWidth: .infinity)
                                .frame(height: 50)
                                .background(Color(hex: 0xFFDD00))
                                .clipShape(RoundedRectangle(cornerRadius: 13))
                        }
                    }
                    .padding(.horizontal, 22)
                    .padding(.top, 50)
                    .padding(.bottom, 24)
                    .background(Color(hex: 0x151621))
                    .clipShape(RoundedRectangle(cornerRadius: 24))
                    .overlay(
                        RoundedRectangle(cornerRadius: 24)
                            .strokeBorder(Color.white.opacity(0.09), lineWidth: 1)
                    )
                    .shadow(color: Color.black.opacity(0.55), radius: 20, x: 0, y: 10)
                    .padding(.top, 36)

                    // Overlapping Circular Avatar
                    ZStack {
                        Circle()
                            .fill(Color.black)
                        Image("AuralixAvatar")
                            .resizable()
                            .aspectRatio(contentMode: .fill)
                            .frame(width: 68, height: 68)
                            .clipShape(Circle())
                    }
                    .frame(width: 72, height: 72)
                    .clipShape(Circle())
                    .overlay(
                        Circle().strokeBorder(Color(hex: 0x282A3A), lineWidth: 2.5)
                    )
                    .shadow(color: Color.black.opacity(0.5), radius: 10, x: 0, y: 4)
                }
                .frame(maxWidth: 400)

                Spacer(minLength: 20)
            }
            .padding(.horizontal, 24)
            .padding(.bottom, 110)
            .frame(maxWidth: .infinity, minHeight: max(0, geo.size.height - 90))
            }
        }
    }
}
