import Foundation

struct ImportTrackMeta {
    let title: String
    let artist: String
}

struct ImportProgress {
    let stage: String
    let current: Int
    let total: Int
    var currentTrackName: String = ""
    var isComplete: Boolean = false
    var error: String? = nil
}

typealias Boolean = Bool

final class SpotifyImportEngine {
    static let shared = SpotifyImportEngine()
    private init() {}

    func importPlaylist(
        from urlOrText: String,
        onProgress: @escaping (ImportProgress) -> Void
    ) async -> Result<UserPlaylist, Error> {
        let trimmed = urlOrText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else {
            return .failure(NSError(domain: "SpotifyImport", code: 400, userInfo: [NSLocalizedDescriptionKey: "Input link or text cannot be empty"]))
        }

        onProgress(ImportProgress(stage: "Analyzing link...", current: 0, total: 0))

        let parsed: (title: String, tracks: [ImportTrackMeta])
        if trimmed.contains("spotify.com") {
            parsed = await parseSpotify(url: trimmed)
        } else if trimmed.contains("music.amazon.com") {
            parsed = await parseAmazonMusic(url: trimmed)
        } else {
            parsed = parsePlainText(trimmed)
        }

        guard !parsed.tracks.isEmpty else {
            return .failure(NSError(domain: "SpotifyImport", code: 404, userInfo: [NSLocalizedDescriptionKey: "No tracks found from link"]))
        }

        let total = parsed.tracks.count
        onProgress(ImportProgress(stage: "Matching tracks with YouTube Music...", current: 0, total: total))

        var matchedVideos: [VideoItem] = []

        for (idx, meta) in parsed.tracks.enumerated() {
            let query = meta.artist.isEmpty ? meta.title : "\(meta.title) \(meta.artist)"
            let results = await YouTubeSearchEngine.searchVideos(query: query, isMusicOnly: true)
            if let first = results.first {
                matchedVideos.append(first)
            }
            onProgress(
                ImportProgress(
                    stage: "Matching tracks...",
                    current: idx + 1,
                    total: total,
                    currentTrackName: "\(meta.title) • \(meta.artist)"
                )
            )
        }

        guard !matchedVideos.isEmpty else {
            return .failure(NSError(domain: "SpotifyImport", code: 404, userInfo: [NSLocalizedDescriptionKey: "Could not find matching audio on YouTube Music"]))
        }

        let finalPlaylist = UserPlaylist(
            id: UUID().uuidString,
            title: parsed.title.isEmpty ? "Imported Playlist" : parsed.title,
            description: "Imported into AURALIX (\(matchedVideos.count) tracks)",
            videos: matchedVideos,
            isSystem: false,
            iconType: "playlist",
            coverThumbnailUrl: matchedVideos.first?.thumbnailUrl
        )

        // Save into UserDataRepository
        UserDataRepository.shared.userPlaylists.append(finalPlaylist)
        UserDataRepository.shared.savePlaylists()

        onProgress(ImportProgress(stage: "Complete!", current: total, total: total, isComplete: true))
        return .success(finalPlaylist)
    }

    private func parseSpotify(url: String) async -> (title: String, tracks: [ImportTrackMeta]) {
        let cleanUrl = url.components(separatedBy: "?").first ?? url
        let isTrack = cleanUrl.contains("/track/")
        let isAlbum = cleanUrl.contains("/album/")
        let isPlaylist = cleanUrl.contains("/playlist/")

        if isTrack {
            if let enc = cleanUrl.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed),
               let oembedUrl = URL(string: "https://open.spotify.com/oembed?url=\(enc)"),
               let (data, _) = try? await URLSession.shared.data(from: oembedUrl),
               let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] {
                let title = (json["title"] as? String) ?? "Spotify Track"
                let author = (json["author_name"] as? String) ?? ""
                return (title, [ImportTrackMeta(title: title, artist: author)])
            }
        }

        let id = cleanUrl.components(separatedBy: "/").last ?? ""
        let embedUrlString: String
        if isPlaylist {
            embedUrlString = "https://open.spotify.com/embed/playlist/\(id)"
        } else if isAlbum {
            embedUrlString = "https://open.spotify.com/embed/album/\(id)"
        } else {
            embedUrlString = cleanUrl
        }

        guard let embedUrl = URL(string: embedUrlString) else { return ("Spotify Playlist", []) }

        var req = URLRequest(url: embedUrl)
        req.setValue("Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15", forHTTPHeaderField: "User-Agent")
        req.setValue("text/html,application/json", forHTTPHeaderField: "Accept")

        guard let (data, _) = try? await URLSession.shared.data(for: req),
              let html = String(data: data, encoding: .utf8) else {
            return ("Spotify Playlist", [])
        }

        var playlistTitle = "Spotify Playlist"
        var tracks: [ImportTrackMeta] = []

        // Extract __NEXT_DATA__
        if let startRange = html.range(of: "<script id=\"__NEXT_DATA__\" type=\"application/json\">"),
           let endRange = html[startRange.upperBound...].range(of: "</script>") {
            let jsonString = String(html[startRange.upperBound..<endRange.lowerBound])
            if let jsonData = jsonString.data(using: .utf8),
               let root = try? JSONSerialization.jsonObject(with: jsonData) as? [String: Any],
               let props = root["props"] as? [String: Any],
               let pageProps = props["pageProps"] as? [String: Any],
               let state = pageProps["state"] as? [String: Any],
               let dataObj = state["data"] as? [String: Any],
               let entity = dataObj["entity"] as? [String: Any] {
                if let name = entity["name"] as? String, !name.isEmpty {
                    playlistTitle = name
                }
                if let trackList = entity["trackList"] as? [[String: Any]] {
                    for item in trackList {
                        let t = (item["title"] as? String) ?? ""
                        let s = (item["subtitle"] as? String) ?? ""
                        if !t.isEmpty {
                            tracks.append(ImportTrackMeta(title: t, artist: s))
                        }
                    }
                }
            }
        }

        return (playlistTitle, tracks)
    }

    private func parseAmazonMusic(url: String) async -> (title: String, tracks: [ImportTrackMeta]) {
        guard let u = URL(string: url),
              let (data, _) = try? await URLSession.shared.data(from: u),
              let html = String(data: data, encoding: .utf8) else {
            return ("Amazon Music Playlist", [])
        }

        var title = "Amazon Music Playlist"
        if let tStart = html.range(of: "<title>"),
           let tEnd = html[tStart.upperBound...].range(of: "</title>") {
            let rawTitle = String(html[tStart.upperBound..<tEnd.lowerBound])
            title = rawTitle.replacingOccurrences(of: "on Amazon Music", with: "").trimmingCharacters(in: .whitespacesAndNewlines)
        }

        var tracks: [ImportTrackMeta] = []
        let pattern = "meta property=\"music:song\" content=\"([^\"]+)\""
        if let regex = try? NSRegularExpression(pattern: pattern) {
            let nsString = html as NSString
            let results = regex.matches(in: html, range: NSRange(location: 0, length: nsString.length))
            for match in results {
                if match.numberOfRanges > 1 {
                    let songUrl = nsString.substring(with: match.range(at: 1))
                    let songName = (songUrl.components(separatedBy: "/").last ?? "").replacingOccurrences(of: "-", with: " ")
                    if !songName.isEmpty {
                        tracks.append(ImportTrackMeta(title: songName, artist: ""))
                    }
                }
            }
        }

        if tracks.isEmpty {
            tracks.append(ImportTrackMeta(title: title, artist: ""))
        }

        return (title, tracks)
    }

    private func parsePlainText(_ text: String) -> (title: String, tracks: [ImportTrackMeta]) {
        let lines = text.components(separatedBy: .newlines).map { $0.trimmingCharacters(in: .whitespaces) }.filter { !$0.isEmpty }
        var tracks: [ImportTrackMeta] = []

        for line in lines {
            let clean = line.replacingOccurrences(of: "^\\d+[\\.\\)\\-]\\s*", with: "", options: .regularExpression)
            if clean.contains(" - ") {
                let parts = clean.components(separatedBy: " - ")
                tracks.append(ImportTrackMeta(title: parts[0].trimmingCharacters(in: .whitespaces), artist: parts[1].trimmingCharacters(in: .whitespaces)))
            } else if clean.contains(" by ") {
                let parts = clean.components(separatedBy: " by ")
                tracks.append(ImportTrackMeta(title: parts[0].trimmingCharacters(in: .whitespaces), artist: parts[1].trimmingCharacters(in: .whitespaces)))
            } else {
                tracks.append(ImportTrackMeta(title: clean, artist: ""))
            }
        }

        return ("My Custom Playlist", tracks)
    }
}
