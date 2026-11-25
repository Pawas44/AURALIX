import Foundation

final class AlbumArtworkResolver {
    static let shared = AlbumArtworkResolver()

    private var memoryCache: [String: String] = [:]
    private let lock = NSLock()
    private let userDefaultsKey = "auralix_album_artwork_cache"

    private init() {
        if let dict = UserDefaults.standard.dictionary(forKey: userDefaultsKey) as? [String: String] {
            memoryCache = dict
        }
    }

    static func cleanUrl(_ url: String?) -> String {
        let raw = url?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        if raw.isEmpty || raw.lowercased() == "null" { return "" }
        return raw.hasPrefix("//") ? "https:\(raw)" : raw
    }

    static func isValidArtworkUrl(_ url: String?) -> Bool {
        let clean = cleanUrl(url)
        if clean.isEmpty { return false }
        if clean.contains("unsplash.com") && clean.contains("photo-1534528741775") { return false }
        return clean.hasPrefix("http://") || clean.hasPrefix("https://")
    }

    private static func generateAlbumKey(title: String, artist: String) -> String {
        let cleanT = SongTitleNormalizer.cleanPunctuation(SongTitleNormalizer.removeEmojis(title)).trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        let cleanA = SongTitleNormalizer.cleanPunctuation(SongMetadataExtractor.cleanArtistString(artist)).trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        return cleanT.isEmpty ? "" : "\(cleanT):::\(cleanA)"
    }

    func cacheAlbumArtwork(albumId: String, title: String = "", artist: String = "", artworkUrl: String) {
        let clean = Self.cleanUrl(artworkUrl)
        guard Self.isValidArtworkUrl(clean) else { return }

        lock.lock()
        defer { lock.unlock() }

        if !albumId.isEmpty {
            memoryCache["id_\(albumId)"] = clean
        }
        let key = Self.generateAlbumKey(title: title, artist: artist)
        if !key.isEmpty {
            memoryCache["key_\(key)"] = clean
        }
        UserDefaults.standard.set(memoryCache, forKey: userDefaultsKey)
    }

    func resolveArtwork(
        albumId: String = "",
        title: String = "",
        artist: String = "",
        primaryUrl: String? = nil,
        tracks: [VideoItem] = []
    ) -> String {
        let cleanPrimary = Self.cleanUrl(primaryUrl)

        // PRIORITY 1: Existing valid album artwork URL from album metadata
        if Self.isValidArtworkUrl(cleanPrimary) {
            let highRes = ArtworkUrlHelper.getHighResArtworkUrl(cleanPrimary)
            cacheAlbumArtwork(albumId: albumId, title: title, artist: artist, artworkUrl: highRes)
            return highRes
        }

        lock.lock()
        // PRIORITY 2: Cached album artwork (by albumId)
        if !albumId.isEmpty, let fromMem = memoryCache["id_\(albumId)"], Self.isValidArtworkUrl(fromMem) {
            lock.unlock()
            return fromMem
        }

        // PRIORITY 3: Cached album artwork (by title + artist)
        let key = Self.generateAlbumKey(title: title, artist: artist)
        if !key.isEmpty, let fromMem = memoryCache["key_\(key)"], Self.isValidArtworkUrl(fromMem) {
            lock.unlock()
            return fromMem
        }
        lock.unlock()

        // PRIORITY 4: Artwork from another song belonging to the same album
        if !tracks.isEmpty {
            for track in tracks {
                let trackThumb = Self.cleanUrl(track.thumbnailUrl)
                if Self.isValidArtworkUrl(trackThumb) && (trackThumb.contains("scdn.co") || trackThumb.contains("spotify.com")) {
                    let highRes = ArtworkUrlHelper.getHighResArtworkUrl(trackThumb, videoId: track.id)
                    cacheAlbumArtwork(albumId: albumId, title: title, artist: artist, artworkUrl: highRes)
                    return highRes
                }
            }

            for track in tracks {
                let trackThumb = Self.cleanUrl(track.thumbnailUrl)
                if Self.isValidArtworkUrl(trackThumb) {
                    let highRes = ArtworkUrlHelper.getHighResArtworkUrl(trackThumb, videoId: track.id)
                    cacheAlbumArtwork(albumId: albumId, title: title, artist: artist, artworkUrl: highRes)
                    return highRes
                }
            }

            if let firstWithId = tracks.first(where: { $0.id.count == 11 }) {
                let fallbackUrl = "https://i.ytimg.com/vi/\(firstWithId.id)/hqdefault.jpg"
                cacheAlbumArtwork(albumId: albumId, title: title, artist: artist, artworkUrl: fallbackUrl)
                return fallbackUrl
            }
        }

        // PRIORITY 5: Valid YouTube thumbnail as fallback if primary URL was present
        if !cleanPrimary.isEmpty {
            let formatted = ArtworkUrlHelper.getHighResArtworkUrl(cleanPrimary)
            if Self.isValidArtworkUrl(formatted) {
                cacheAlbumArtwork(albumId: albumId, title: title, artist: artist, artworkUrl: formatted)
                return formatted
            }
        }

        return ""
    }
}
