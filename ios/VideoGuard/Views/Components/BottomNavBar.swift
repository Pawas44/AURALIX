import SwiftUI

struct LiquidBottomNavBar: View {
    var isMusic: Bool = false
    let isHomeSelected: Bool
    let isSearchSelected: Bool
    let isAccountSelected: Bool
    let isSupportSelected: Bool
    let onHomeClick: () -> Void
    let onSearchClick: () -> Void
    let onAccountClick: () -> Void
    let onSupportClick: () -> Void

    @ObservedObject var accountRepo = AccountRepository.shared

    var body: some View {
        HStack(spacing: 4) {
            // 1. Home
            Button(action: onHomeClick) {
                VStack(spacing: 2) {
                    Image(systemName: isHomeSelected ? "house.fill" : "house")
                        .font(.system(size: 20))
                    Text("Home")
                        .font(.system(size: 10, weight: isHomeSelected ? .bold : .medium))
                }
                .foregroundColor(isHomeSelected ? .white : Color(hex: 0x9E9EAF))
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(isHomeSelected ? Color.white.opacity(0.12) : Color.clear)
                .clipShape(RoundedRectangle(cornerRadius: 18))
            }

            // 2. Search
            Button(action: onSearchClick) {
                VStack(spacing: 2) {
                    Image(systemName: "magnifyingglass")
                        .font(.system(size: 20, weight: isSearchSelected ? .bold : .regular))
                    Text("Search")
                        .font(.system(size: 10, weight: isSearchSelected ? .bold : .medium))
                }
                .foregroundColor(isSearchSelected ? .white : Color(hex: 0x9E9EAF))
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(isSearchSelected ? Color.white.opacity(0.12) : Color.clear)
                .clipShape(RoundedRectangle(cornerRadius: 18))
            }

            // 3. Library (in Music mode) / You (in Video mode)
            Button(action: onAccountClick) {
                VStack(spacing: 2) {
                    if isMusic {
                        Image(systemName: isAccountSelected ? "play.square.stack.fill" : "play.square.stack")
                            .font(.system(size: 19))
                        Text("Library")
                            .font(.system(size: 10, weight: isAccountSelected ? .bold : .medium))
                    } else {
                        accountAvatarView
                            .frame(width: 22, height: 22)
                        Text("You")
                            .font(.system(size: 10, weight: isAccountSelected ? .bold : .medium))
                    }
                }
                .foregroundColor(isAccountSelected ? .white : Color(hex: 0x9E9EAF))
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(isAccountSelected ? Color.white.opacity(0.12) : Color.clear)
                .clipShape(RoundedRectangle(cornerRadius: 18))
            }

            // 4. Support
            Button(action: onSupportClick) {
                VStack(spacing: 2) {
                    Image(systemName: isSupportSelected ? "cup.and.saucer.fill" : "cup.and.saucer")
                        .font(.system(size: 19))
                    Text("Support")
                        .font(.system(size: 10, weight: isSupportSelected ? .bold : .medium))
                }
                .foregroundColor(isSupportSelected ? Color(hex: 0x40DCA5) : Color(hex: 0x9E9EAF))
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(isSupportSelected ? Color(hex: 0x40DCA5).opacity(0.14) : Color.clear)
                .clipShape(RoundedRectangle(cornerRadius: 18))
            }
        }
        .padding(.horizontal, 6)
        .padding(.vertical, 4)
        .frame(height: 58)
        .background(
            LinearGradient(
                colors: [Color(hex: 0x171828, alpha: 0.90), Color(hex: 0x0E0F1A, alpha: 0.85)],
                startPoint: .top,
                endPoint: .bottom
            )
        )
        .clipShape(RoundedRectangle(cornerRadius: 26))
        .overlay(
            RoundedRectangle(cornerRadius: 26)
                .strokeBorder(LiquidGlassDefaults.borderSpecular, lineWidth: 1)
        )
        .shadow(color: Color.black.opacity(0.55), radius: 12, x: 0, y: 6)
        .padding(.horizontal, 12)
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
