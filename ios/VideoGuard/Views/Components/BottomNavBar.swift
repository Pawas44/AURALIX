import SwiftUI
import UIKit

struct VisualEffectBlurView: UIViewRepresentable {
    var style: UIBlurEffect.Style = .systemUltraThinMaterialDark

    func makeUIView(context: Context) -> UIVisualEffectView {
        return UIVisualEffectView(effect: UIBlurEffect(style: style))
    }

    func updateUIView(_ uiView: UIVisualEffectView, context: Context) {
        uiView.effect = UIBlurEffect(style: style)
    }
}

struct LiquidBottomNavBar: View {
    let isHomeSelected: Bool
    let isSearchSelected: Bool
    let isLibrarySelected: Bool
    let isAccountSelected: Bool
    let isSupportSelected: Bool
    let onHomeClick: () -> Void
    let onSearchClick: () -> Void
    let onLibraryClick: () -> Void
    let onAccountClick: () -> Void
    let onSupportClick: () -> Void

    @ObservedObject var accountRepo = AccountRepository.shared

    var body: some View {
        HStack(spacing: 4) {
            // 1. Home
            Button(action: onHomeClick) {
                VStack(spacing: 3) {
                    Image(systemName: isHomeSelected ? "house.fill" : "house")
                        .font(.system(size: 19))
                    Text("Home")
                        .font(.system(size: 10, weight: isHomeSelected ? .bold : .medium))
                        .lineLimit(1)
                        .fixedSize(horizontal: true, vertical: false)
                }
                .foregroundColor(isHomeSelected ? .white : Color(hex: 0x9E9EAF))
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(isHomeSelected ? Color.white.opacity(0.12) : Color.clear)
                .clipShape(RoundedRectangle(cornerRadius: 16))
            }

            // 2. Search
            Button(action: onSearchClick) {
                VStack(spacing: 3) {
                    Image(systemName: "magnifyingglass")
                        .font(.system(size: 19, weight: isSearchSelected ? .bold : .regular))
                    Text("Search")
                        .font(.system(size: 10, weight: isSearchSelected ? .bold : .medium))
                        .lineLimit(1)
                        .fixedSize(horizontal: true, vertical: false)
                }
                .foregroundColor(isSearchSelected ? .white : Color(hex: 0x9E9EAF))
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(isSearchSelected ? Color.white.opacity(0.12) : Color.clear)
                .clipShape(RoundedRectangle(cornerRadius: 16))
            }

            // 3. Library (Dedicated Tab)
            Button(action: onLibraryClick) {
                VStack(spacing: 3) {
                    Image(systemName: isLibrarySelected ? "play.square.stack.fill" : "play.square.stack")
                        .font(.system(size: 19))
                    Text("Library")
                        .font(.system(size: 10, weight: isLibrarySelected ? .bold : .medium))
                        .lineLimit(1)
                        .fixedSize(horizontal: true, vertical: false)
                }
                .foregroundColor(isLibrarySelected ? .white : Color(hex: 0x9E9EAF))
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(isLibrarySelected ? Color.white.opacity(0.12) : Color.clear)
                .clipShape(RoundedRectangle(cornerRadius: 16))
            }

            // 4. Account (Dedicated Tab)
            Button(action: onAccountClick) {
                VStack(spacing: 3) {
                    accountAvatarView
                        .frame(width: 22, height: 22)
                    Text("Account")
                        .font(.system(size: 10, weight: isAccountSelected ? .bold : .medium))
                        .lineLimit(1)
                        .fixedSize(horizontal: true, vertical: false)
                }
                .foregroundColor(isAccountSelected ? .white : Color(hex: 0x9E9EAF))
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(isAccountSelected ? Color.white.opacity(0.12) : Color.clear)
                .clipShape(RoundedRectangle(cornerRadius: 16))
            }

            // 5. Support
            Button(action: onSupportClick) {
                VStack(spacing: 3) {
                    Image(systemName: isSupportSelected ? "cup.and.saucer.fill" : "cup.and.saucer")
                        .font(.system(size: 18))
                    Text("Support")
                        .font(.system(size: 10, weight: isSupportSelected ? .bold : .medium))
                        .lineLimit(1)
                        .fixedSize(horizontal: true, vertical: false)
                }
                .foregroundColor(isSupportSelected ? Color(hex: 0x40DCA5) : Color(hex: 0x9E9EAF))
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(isSupportSelected ? Color(hex: 0x40DCA5).opacity(0.14) : Color.clear)
                .clipShape(RoundedRectangle(cornerRadius: 16))
            }
        }
        .padding(.horizontal, 6)
        .padding(.vertical, 4)
        .frame(height: 56)
        .background(
            ZStack {
                VisualEffectBlurView(style: .systemUltraThinMaterialDark)
                Color(hex: 0x0A0B14, alpha: 0.94)
            }
        )
        .clipShape(RoundedRectangle(cornerRadius: 24))
        .overlay(
            RoundedRectangle(cornerRadius: 24)
                .strokeBorder(LiquidGlassDefaults.borderSpecular, lineWidth: 1)
        )
        .shadow(color: Color.black.opacity(0.65), radius: 14, x: 0, y: 6)
        .padding(.horizontal, 14)
        .padding(.bottom, 6)
    }

    @ViewBuilder
    private var accountAvatarView: some View {
        if accountRepo.isIncognito {
            Image(systemName: "eye.slash.fill")
                .font(.system(size: 17))
                .foregroundColor(isAccountSelected ? .white : Color(hex: 0x9E9EAF))
        } else if let acc = accountRepo.currentAccount {
            if let urlStr = acc.avatarUrl, let url = URL(string: urlStr) {
                AsyncImage(url: url) { img in
                    img.resizable().aspectRatio(contentMode: .fill)
                } placeholder: {
                    Circle().fill(Color(argbHex: acc.avatarBgColorHex))
                }
                .frame(width: 22, height: 22)
                .clipShape(Circle())
                .overlay(Circle().strokeBorder(Color.white.opacity(isAccountSelected ? 1.0 : 0.35), lineWidth: isAccountSelected ? 1.5 : 1))
            } else {
                Text(acc.initial)
                    .font(.system(size: 10, weight: .bold))
                    .foregroundColor(.white)
                    .frame(width: 22, height: 22)
                    .background(Color(argbHex: acc.avatarBgColorHex))
                    .clipShape(Circle())
                    .overlay(Circle().strokeBorder(Color.white.opacity(isAccountSelected ? 1.0 : 0.35), lineWidth: isAccountSelected ? 1.5 : 1))
            }
        } else {
            Image(systemName: isAccountSelected ? "person.crop.circle.fill" : "person.crop.circle")
                .font(.system(size: 20))
                .foregroundColor(isAccountSelected ? .white : Color(hex: 0x9E9EAF))
        }
    }
}
