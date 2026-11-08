import Foundation

struct AccountRepository {
    static let currentUser = UserAccount(
        name: "Alex Johnson",
        email: "alex.johnson@gmail.com",
        avatarUrl: "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=150",
        isPremium: true,
        memberSince: "2019",
        channelName: "Alex's Channel",
        channelHandle: "@alexjohnson"
    )

    static func getCurrentUser() -> UserAccount {
        return currentUser
    }
}

