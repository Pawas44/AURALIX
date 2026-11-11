import Foundation

struct UserAccount: Identifiable, Codable, Equatable {
    var id: String
    let name: String
    let email: String
    var handle: String
    var initial: String
    var avatarUrl: String?
    var avatarBgColorHex: UInt = 0xFF1976D2
    var isCurrent: Bool = true
    var isGoogleAuthenticated: Bool = true
    var isPremium: Bool = false
    var memberSince: String = "2020"
    var channelName: String { name }
    var channelHandle: String { handle }
}
