import Foundation
import Combine
import WebKit

class AccountRepository: ObservableObject {
    static let shared = AccountRepository()

    private let keyAccount = "vg_current_account_json"

    @Published var currentAccount: UserAccount? = nil
    @Published var isIncognito: Bool = false

    var isSignedIn: Bool {
        currentAccount != nil
    }

    private init() {
        loadAccount()
    }

    private func loadAccount() {
        if let data = UserDefaults.standard.data(forKey: keyAccount),
           let decoded = try? JSONDecoder().decode(UserAccount.self, from: data) {
            if !decoded.name.isEmpty && decoded.name != "YouTube User" && decoded.name != "Google Account" {
                self.currentAccount = decoded
            }
        }
    }

    func syncIfCookiesExist() {
        if YouTubeSyncEngine.shared.hasAuthCookies() {
            if currentAccount == nil {
                DispatchQueue.main.async {
                    self.currentAccount = UserAccount(
                        id: "google_user",
                        name: "YouTube Account",
                        email: "",
                        handle: "@user",
                        initial: "Y",
                        avatarUrl: nil,
                        avatarBgColorHex: 0xFF1976D2,
                        isCurrent: true,
                        isGoogleAuthenticated: true
                    )
                }
            }
            Task {
                await YouTubeSyncEngine.shared.syncAllUserData()
            }
        }
    }

    func setGoogleAccount(name: String, email: String, avatarUrl: String?, handle: String) {
        let trimmedName = name.trimmingCharacters(in: .whitespacesAndNewlines)
        let finalName = trimmedName.isEmpty ? "YouTube Account" : trimmedName
        let initialLetter = String(finalName.prefix(1)).uppercased()
        let finalHandle = handle.hasPrefix("@") ? handle : "@\(handle)"

        let account = UserAccount(
            id: "google_user",
            name: finalName,
            email: email,
            handle: finalHandle,
            initial: initialLetter,
            avatarUrl: avatarUrl,
            avatarBgColorHex: 0xFF1976D2,
            isCurrent: true,
            isGoogleAuthenticated: true,
            isPremium: true
        )

        DispatchQueue.main.async {
            self.isIncognito = false
            self.currentAccount = account
            if let data = try? JSONEncoder().encode(account) {
                UserDefaults.standard.set(data, forKey: self.keyAccount)
            }
        }
    }

    func signOut() {
        DispatchQueue.main.async {
            self.isIncognito = false
            self.currentAccount = nil
            UserDefaults.standard.removeObject(forKey: self.keyAccount)
            UserDataRepository.shared.clearHistory()

            // Clear WebKit cookies & HTTPCookieStorage
            let store = WKWebsiteDataStore.default()
            store.fetchDataRecords(ofTypes: WKWebsiteDataStore.allWebsiteDataTypes()) { records in
                store.removeData(ofTypes: WKWebsiteDataStore.allWebsiteDataTypes(), for: records) {}
            }
            if let cookies = HTTPCookieStorage.shared.cookies {
                for cookie in cookies {
                    HTTPCookieStorage.shared.deleteCookie(cookie)
                }
            }
        }
    }

    func toggleIncognito() {
        DispatchQueue.main.async {
            self.isIncognito.toggle()
        }
    }
}
