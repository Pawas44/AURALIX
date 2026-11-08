import Foundation

struct UserAccount: Identifiable, Codable {
    var id: String { email }
    let name: String
    let email: String
    let avatarUrl: String
    var isPremium: Bool = false
    var memberSince: String = "2020"
    var channelName: String = ""
    var channelHandle: String = ""
}

