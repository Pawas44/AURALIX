import Foundation
import UIKit

/**
 * PlaylistShareEngine
 * Generates and parses 1-click shareable links (auralix.app/playlist?p=... and auralix://playlist)
 * Compatible across Android and iOS with compressed Base64 payloads and deep link routing.
 */
final class PlaylistShareEngine {
    static let shared = PlaylistShareEngine()
    
    private let baseWebUrl = "https://auralix.app/playlist"
    private let baseSchemeUrl = "auralix://playlist"
    
    private init() {}
    
    /**
     * Generates a 1-click share link and presents the native iOS UIActivityViewController share sheet.
     */
    @MainActor
    func sharePlaylist(playlist: UserPlaylist, from presentingViewController: UIViewController? = nil) {
        let shareLink = generateShareLink(playlist: playlist)
        let items: [Any] = [shareLink]
        let activityVC = UIActivityViewController(activityItems: items, applicationActivities: nil)
        
        let rootVC = presentingViewController ?? UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap { $0.windows }
            .first { $0.isKeyWindow }?
            .rootViewController
        
        if let popover = activityVC.popoverPresentationController {
            popover.sourceView = rootVC?.view
            popover.sourceRect = CGRect(x: UIScreen.main.bounds.midX, y: UIScreen.main.bounds.midY, width: 0, height: 0)
            popover.permittedArrowDirections = []
        }
        
        rootVC?.present(activityVC, animated: true)
    }
    
    /**
     * Generates a direct custom scheme deep link (auralix://playlist?p=...)
     */
    func generateAppSchemeLink(playlist: UserPlaylist) -> String {
        let isRemote = playlist.id.hasPrefix("PL") || playlist.id.hasPrefix("OLAK5uy_") || playlist.id.hasPrefix("RD")
        let encTitle = playlist.title.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? playlist.title
        if isRemote && playlist.videos.isEmpty {
            return "\(baseSchemeUrl)?id=\(playlist.id)&title=\(encTitle)"
        }
        if let payload = buildCompressedPayload(playlist: playlist) {
            return "\(baseSchemeUrl)?p=\(payload)&title=\(encTitle)"
        }
        let vids = playlist.videos.map { $0.id }.joined(separator: ",")
        return "\(baseSchemeUrl)?title=\(encTitle)&v=\(vids)"
    }
    
    /**
     * Creates an ultra-compact 1-click share URL.
     * Uses JSON + Base64 so full song lists pack into a URL that opens instantly.
     */
    func generateShareLink(playlist: UserPlaylist) -> String {
        let isRemote = playlist.id.hasPrefix("PL") || playlist.id.hasPrefix("OLAK5uy_") || playlist.id.hasPrefix("RD")
        let encTitle = playlist.title.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? playlist.title
        if isRemote && playlist.videos.isEmpty {
            return "\(baseWebUrl)/?id=\(playlist.id)&title=\(encTitle)"
        }
        
        if let compressed = buildCompressedPayload(playlist: playlist) {
            return "\(baseWebUrl)/?p=\(compressed)&title=\(encTitle)"
        }
        
        let vids = playlist.videos.map { $0.id }.joined(separator: ",")
        return "\(baseWebUrl)/?title=\(encTitle)&v=\(vids)"
    }
    
    private func buildCompressedPayload(playlist: UserPlaylist) -> String? {
        var videoArray: [[String: Any]] = []
        for v in playlist.videos {
            var vo: [String: Any] = [
                "i": v.id,
                "t": v.title,
                "a": v.artist?.isEmpty == false ? v.artist! : v.channelName,
                "d": v.duration
            ]
            if !v.thumbnailUrl.isEmpty {
                vo["u"] = v.thumbnailUrl
            }
            videoArray.append(vo)
        }
        
        var json: [String: Any] = [
            "i": playlist.id,
            "t": playlist.title,
            "v": videoArray
        ]
        if !playlist.description.isEmpty {
            json["d"] = playlist.description
        }
        if let cover = playlist.customCoverUrl, !cover.isEmpty {
            json["c"] = cover
        }
        
        guard let jsonData = try? JSONSerialization.data(withJSONObject: json) else { return nil }
        // Base64 URL safe encoding
        return jsonData.base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
    }
    
    /**
     * Parses an incoming deep link URL (from WhatsApp, Safari, Telegram, Notes, etc.)
     * Returns a fully reconstructed UserPlaylist ready to view and play.
     */
    func parseIncomingDeepLink(url: URL) -> UserPlaylist? {
        guard let components = URLComponents(url: url, resolvingAgainstBaseURL: true) else { return nil }
        let scheme = components.scheme?.lowercased() ?? ""
        let host = components.host?.lowercased() ?? ""
        
        let isAuralixLink = scheme == "auralix" ||
            host.contains("auralix.app") ||
            host.contains("pawas44.github.io") ||
            components.queryItems?.contains(where: { $0.name == "p" }) == true
        
        if isAuralixLink {
            // A. Check compressed payload `p`
            if let pVal = components.queryItems?.first(where: { $0.name == "p" })?.value, !pVal.isEmpty {
                if let playlist = decodePayload(pVal) {
                    return playlist
                }
            }
            
            // B. Check simple video list `v` and `title`
            if let vidsVal = components.queryItems?.first(where: { $0.name == "v" })?.value, !vidsVal.isEmpty {
                let titleVal = components.queryItems?.first(where: { $0.name == "title" || $0.name == "t" })?.value ?? "Shared Playlist"
                let ids = vidsVal.components(separatedBy: ",").map { $0.trimmingCharacters(in: .whitespaces) }.filter { !$0.isEmpty }
                let items = ids.enumerated().map { idx, vid in
                    let thumb = ArtworkUrlHelper.getHighResArtworkUrl("", videoId: vid)
                    return VideoItem(
                        id: vid,
                        title: "Song \(idx + 1)",
                        channelName: "AURALIX Track",
                        channelAvatarUrl: thumb,
                        thumbnailUrl: thumb,
                        views: "",
                        uploadTime: "",
                        duration: "3:30",
                        isMusicTrack: true
                    )
                }
                return UserPlaylist(
                    id: "shared_\(Int(Date().timeIntervalSince1970 * 1000))",
                    title: titleVal,
                    description: "Shared via AURALIX",
                    videos: items,
                    isSystem: false,
                    iconType: "playlist",
                    customCoverUrl: items.first?.thumbnailUrl
                )
            }
            
            // C. Check remote YouTube playlist ID `id` (e.g. PL...)
            if let idVal = components.queryItems?.first(where: { $0.name == "id" })?.value, !idVal.isEmpty {
                let titleVal = components.queryItems?.first(where: { $0.name == "title" })?.value ?? "YouTube Playlist"
                return UserPlaylist(
                    id: idVal,
                    title: titleVal,
                    description: "Synced via YouTube",
                    isSystem: false,
                    iconType: "playlist"
                )
            }
        }
        
        // 2. Direct YouTube Playlist link
        if host.contains("youtube.com") || host.contains("youtu.be") {
            if let listId = components.queryItems?.first(where: { $0.name == "list" })?.value, !listId.isEmpty {
                return UserPlaylist(
                    id: listId,
                    title: "YouTube Playlist",
                    description: "Imported from YouTube",
                    isSystem: false,
                    iconType: "playlist"
                )
            }
        }
        
        // 3. Direct Spotify Playlist link
        if host.contains("spotify.com") && url.path.contains("/playlist/") {
            let sId = url.pathComponents.last?.components(separatedBy: "?").first ?? ""
            if !sId.isEmpty {
                return UserPlaylist(
                    id: "spotify_\(sId)",
                    title: "Spotify Playlist",
                    description: "Imported from Spotify",
                    isSystem: false,
                    iconType: "playlist"
                )
            }
        }
        
        return nil
    }
    
    /**
     * Robust parser that extracts a playlist from ANY text, whether URL,
     * pasted chat message, or parameters.
     */
    func parseTextForPlaylist(_ rawInput: String) -> UserPlaylist? {
        let text = rawInput.trimmingCharacters(in: .whitespacesAndNewlines)
        if text.isEmpty { return nil }
        
        // 1. Regex match for ?p= or &p=
        if let match = text.range(of: #"[?&]p=([A-Za-z0-9_-]+)"#, options: .regularExpression) {
            let sub = String(text[match])
            if let pIndex = sub.range(of: "p=")?.upperBound {
                let pVal = String(sub[pIndex...])
                if let playlist = decodePayload(pVal) {
                    return playlist
                }
            }
        }
        
        // 2. Direct URL match
        if let detector = try? NSDataDetector(types: NSTextCheckingResult.CheckingType.link.rawValue),
           let firstMatch = detector.firstMatch(in: text, options: [], range: NSRange(location: 0, length: text.utf16.count)),
           let url = firstMatch.url {
            return parseIncomingDeepLink(url: url)
        }
        
        if let directUrl = URL(string: text) {
            return parseIncomingDeepLink(url: directUrl)
        }
        
        return nil
    }
    
    private func decodePayload(_ base64Str: String) -> UserPlaylist? {
        var base64 = base64Str
            .replacingOccurrences(of: "-", with: "+")
            .replacingOccurrences(of: "_", with: "/")
        
        let mod4 = base64.count % 4
        if mod4 > 0 {
            base64.append(String(repeating: "=", count: 4 - mod4))
        }
        
        guard let data = Data(base64Encoded: base64),
              let json = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any] else {
            return nil
        }
        
        let id = (json["i"] as? String) ?? "shared_\(Int(Date().timeIntervalSince1970 * 1000))"
        let title = (json["t"] as? String) ?? "Shared Playlist"
        let desc = (json["d"] as? String) ?? "Shared via AURALIX"
        let cover = json["c"] as? String
        let arr = (json["v"] as? [[String: Any]]) ?? []
        
        var list: [VideoItem] = []
        for (idx, obj) in arr.enumerated() {
            guard let vid = obj["i"] as? String, !vid.isEmpty else { continue }
            let vTitle = (obj["t"] as? String) ?? "Song \(idx + 1)"
            let vArtist = (obj["a"] as? String) ?? "Unknown Artist"
            let vDur = (obj["d"] as? String) ?? "3:30"
            let vThumb = (obj["u"] as? String) ?? ArtworkUrlHelper.getHighResArtworkUrl("", videoId: vid)
            list.append(
                VideoItem(
                    id: vid,
                    title: vTitle,
                    channelName: vArtist,
                    channelAvatarUrl: vThumb,
                    thumbnailUrl: vThumb,
                    views: "",
                    uploadTime: "",
                    duration: vDur,
                    isMusicTrack: true,
                    artist: vArtist
                )
            )
        }
        
        return UserPlaylist(
            id: id.hasPrefix("shared_") ? id : "shared_\(id)",
            title: title,
            description: desc,
            videos: list,
            isSystem: false,
            iconType: "playlist",
            customCoverUrl: (cover?.isEmpty == false ? cover : nil) ?? list.first?.thumbnailUrl
        )
    }
}
