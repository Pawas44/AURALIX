import Foundation

struct YouTubeSearchEngine {
    static let shared = YouTubeSearchEngine()

    static func fetchSearchSuggestions(query: String) async -> [String] {
        await getSuggestions(query: query)
    }

    func getSuggestions(query: String) async -> [String] {
        await Self.getSuggestions(query: query)
    }

    func fetchSearchSuggestions(query: String) async -> [String] {
        await Self.getSuggestions(query: query)
    }

    func searchVideos(_ query: String, isMusicOnly: Bool = false, page: Int = 0) async -> [VideoItem] {
        await Self.searchVideos(query: query, isMusicOnly: isMusicOnly, page: page)
    }

    func searchVideos(query: String, isMusicOnly: Bool = false, page: Int = 0) async -> [VideoItem] {
        await Self.searchVideos(query: query, isMusicOnly: isMusicOnly, page: page)
    }

    func getSongRadio(videoId: String) async throws -> [VideoItem] {
        if let video = VideoRepository.getMusicTracks().first(where: { $0.id == videoId }) {
            return await Self.getRelatedVideos(video: video, isMusicOnly: true)
        }
        return await Self.searchVideos(query: "\(videoId) radio", isMusicOnly: true)
    }

    static let trendingSearches = [
        "trending music 2024",
        "lofi hip hop",
        "workout motivation",
        "cooking recipes easy",
        "tech review",
        "gaming highlights",
        "travel vlog",
        "meditation music"
    ]

    static func getTrendingSearches() -> [String] {
        return trendingSearches
    }

    static func getSuggestions(query: String) async -> [String] {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return [] }
        guard let encoded = trimmed.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed),
              let url = URL(string: "https://suggestqueries.google.com/complete/search?client=firefox&ds=yt&q=\(encoded)") else {
            return []
        }

        var request = URLRequest(url: url, timeoutInterval: 4.0)
        request.setValue("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36", forHTTPHeaderField: "User-Agent")

        do {
            let (data, response) = try await URLSession.shared.data(for: request)
            if let http = response as? HTTPURLResponse, http.statusCode == 200 {
                if let jsonArray = try JSONSerialization.jsonObject(with: data) as? [Any],
                   jsonArray.count > 1,
                   let suggestions = jsonArray[1] as? [String] {
                    return suggestions
                }
            }
        } catch {
            // Fall back to empty
        }
        return []
    }

    static func searchVideos(query: String, isMusicOnly: Bool = false, page: Int = 0) async -> [VideoItem] {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else {
            return isMusicOnly ? VideoRepository.getMusicTracks() : VideoRepository.sampleVideos
        }

        // When in Music Mode, prioritize genuine Songs (Audio tracks) via YouTube Music Songs API
        // This avoids Music Videos, Live stage performances, skits, dialogues and cover versions.
        if isMusicOnly {
            let musicSongs = await searchYouTubeMusicSongs(query: trimmed)
            if !musicSongs.isEmpty {
                return musicSongs
            }
        }

        let searchQuery: String
        if isMusicOnly && !trimmed.lowercased().contains("song") && !trimmed.lowercased().contains("music") {
            searchQuery = "\(trimmed) song"
        } else {
            searchQuery = trimmed
        }

        guard let encoded = searchQuery.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) else {
            return isMusicOnly ? VideoRepository.searchMusic(query: trimmed) : VideoRepository.searchVideos(query: trimmed)
        }

        let pageParam: String
        switch page {
        case 0: pageParam = ""
        case 1: pageParam = "&sp=EgIQAQ%3D%3D"
        case 2: pageParam = "&sp=EgIQAw%3D%3D"
        default: pageParam = "&sp=EgIQAQ%3D%3D&page=\(page)"
        }

        if let url = URL(string: "https://www.youtube.com/results?search_query=\(encoded)\(pageParam)") {
            var request = URLRequest(url: url, timeoutInterval: 7.0)
            request.setValue("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36", forHTTPHeaderField: "User-Agent")
            request.setValue("en-US,en;q=0.9", forHTTPHeaderField: "Accept-Language")

            do {
                let (data, response) = try await URLSession.shared.data(for: request)
                if let http = response as? HTTPURLResponse, http.statusCode == 200,
                   let html = String(data: data, encoding: .utf8) {
                    let parsed = parseSearchResults(html: html, isMusic: isMusicOnly, query: trimmed)
                    if !parsed.isEmpty {
                        return parsed
                    }
                }
            } catch {
                // Fallback below
            }
        }

        return isMusicOnly ? VideoRepository.searchMusic(query: trimmed) : VideoRepository.searchVideos(query: trimmed)
    }

    static func searchYouTubeMusicSongs(query: String) async -> [VideoItem] {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return [] }

        guard let url = URL(string: "https://music.youtube.com/youtubei/v1/search?key=\(YouTubeSyncEngine.innerTubeApiKey)") else {
            return []
        }

        var req = URLRequest(url: url, timeoutInterval: 8.0)
        req.httpMethod = "POST"
        req.setValue("application/json; charset=UTF-8", forHTTPHeaderField: "Content-Type")
        req.setValue(YouTubeSyncEngine.desktopUA, forHTTPHeaderField: "User-Agent")
        req.setValue("https://music.youtube.com", forHTTPHeaderField: "Origin")
        req.setValue("https://music.youtube.com/", forHTTPHeaderField: "Referer")
        req.setValue("67", forHTTPHeaderField: "X-YouTube-Client-Name")
        req.setValue("1.20240101.01.00", forHTTPHeaderField: "X-YouTube-Client-Version")

        // Search YouTube Music without restrictive params so that official music videos,
        // singles, hip hop releases, and songs are all returned
        let payload: [String: Any] = [
            "context": [
                "client": [
                    "clientName": "WEB_REMIX",
                    "clientVersion": "1.20240101.01.00",
                    "hl": "en",
                    "gl": "IN"
                ]
            ],
            "query": trimmed
        ]
        req.httpBody = try? JSONSerialization.data(withJSONObject: payload)

        do {
            let (data, resp) = try await URLSession.shared.data(for: req)
            if let http = resp as? HTTPURLResponse, http.statusCode == 200,
               let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] {
                return parseMusicSearchResponse(json: json, query: trimmed)
            }
        } catch {}
        return []
    }

    private static func parseMusicSearchResponse(json: [String: Any], query: String) -> [VideoItem] {
        var outList: [VideoItem] = []
        var seenIds = Set<String>()
        guard let contents = json["contents"] as? [String: Any],
              let tabbed = contents["tabbedSearchResultsRenderer"] as? [String: Any],
              let tabs = tabbed["tabs"] as? [[String: Any]],
              let tab0 = tabs.first?["tabRenderer"] as? [String: Any],
              let tabContent = tab0["content"] as? [String: Any],
              let shelves = (tabContent["sectionListRenderer"] as? [String: Any])?["contents"] as? [[String: Any]] else {
            return []
        }

        for shelf in shelves {
            // 1. Top Result Card (musicCardShelfRenderer)
            if let card = shelf["musicCardShelfRenderer"] as? [String: Any] {
                var vid = ((card["onTap"] as? [String: Any])?["watchEndpoint"] as? [String: Any])?["videoId"] as? String ?? ""
                if vid.isEmpty {
                    let runs0 = (card["title"] as? [String: Any])?["runs"] as? [[String: Any]]
                    vid = ((runs0?.first?["navigationEndpoint"] as? [String: Any])?["watchEndpoint"] as? [String: Any])?["videoId"] as? String ?? ""
                }
                var isAlbum = false
                if vid.isEmpty {
                    var bId = ((card["onTap"] as? [String: Any])?["browseEndpoint"] as? [String: Any])?["browseId"] as? String ?? ""
                    if bId.isEmpty {
                        let runs0 = (card["title"] as? [String: Any])?["runs"] as? [[String: Any]]
                        let nav = runs0?.first?["navigationEndpoint"] as? [String: Any]
                        bId = (nav?["browseEndpoint"] as? [String: Any])?["browseId"] as? String ?? ""
                    }
                    if bId.hasPrefix("MPREb_") || bId.hasPrefix("OLAK5uy_") || bId.hasPrefix("FEmusic_library_album_") {
                        vid = bId
                        isAlbum = true
                    }
                }
                if !vid.isEmpty, (vid.count == 11 || isAlbum), seenIds.insert(vid).inserted {
                    let rawTitle = (((card["title"] as? [String: Any])?["runs"] as? [[String: Any]])?.first?["text"] as? String ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
                    if !rawTitle.isEmpty {
                        var artist = "YouTube Music"
                        var duration = isAlbum ? "Album" : "3:30"
                        var isCardVideo = false
                        if let subRuns = (card["subtitle"] as? [String: Any])?["runs"] as? [[String: Any]] {
                            var parts: [String] = []
                            var cur = ""
                            for r in subRuns {
                                let txt = (r["text"] as? String ?? "")
                                if txt == " • " || txt == "•" {
                                    if !cur.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                                        parts.append(cur.trimmingCharacters(in: .whitespacesAndNewlines))
                                        cur = ""
                                    }
                                } else {
                                    cur += txt
                                }
                            }
                            if !cur.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                                parts.append(cur.trimmingCharacters(in: .whitespacesAndNewlines))
                            }
                            if !parts.isEmpty {
                                isCardVideo = parts[0].lowercased() == "video"
                                if parts[0].lowercased() == "video" || parts[0].lowercased() == "song" || parts[0].lowercased() == "album" {
                                    if parts.count > 1 { artist = parts[1] }
                                } else {
                                    artist = parts[0]
                                }
                                if let last = parts.last, last.range(of: #"^\d{1,2}:\d{2}"#, options: .regularExpression) != nil {
                                    duration = last
                                }
                            }
                        }

                        var thumb = isAlbum ? "" : "https://i.ytimg.com/vi/\(vid)/hqdefault.jpg"
                        if let thumbs = (((card["thumbnail"] as? [String: Any])?["musicThumbnailRenderer"] as? [String: Any])?["thumbnail"] as? [String: Any])?["thumbnails"] as? [[String: Any]],
                           let u = thumbs.last?["url"] as? String, !u.isEmpty {
                            thumb = u.hasPrefix("//") ? "https:\(u)" : u
                        }
                        thumb = ArtworkUrlHelper.getHighResArtworkUrl(thumb, videoId: isAlbum ? "" : vid)

                        outList.append(
                            VideoItem(
                                id: vid,
                                title: rawTitle,
                                channelName: artist,
                                channelAvatarUrl: thumb,
                                subscriberCount: "Top Result",
                                thumbnailUrl: thumb,
                                views: isAlbum ? "Album" : (isCardVideo ? "Video" : "Song"),
                                uploadTime: "YouTube Music",
                                duration: duration,
                                description: "\(rawTitle) - \(artist)",
                                likes: "100K",
                                isMusicTrack: !isAlbum,
                                artist: artist,
                                album: isAlbum ? rawTitle : "Single",
                                lyrics: "",
                                category: isAlbum ? "Album" : "Music"
                            )
                        )
                    }
                }
            }

            // 2. Shelves of responsive list items
            let items: [[String: Any]]
            if let msr = shelf["musicShelfRenderer"] as? [String: Any],
               let c = msr["contents"] as? [[String: Any]] {
                items = c
            } else if let isr = shelf["itemSectionRenderer"] as? [String: Any],
                      let c = isr["contents"] as? [[String: Any]] {
                items = c
            } else {
                continue
            }

            for item in items {
                guard let resp = item["musicResponsiveListItemRenderer"] as? [String: Any] else { continue }
                var vid = (resp["playlistItemData"] as? [String: Any])?["videoId"] as? String ?? ""
                let flexCols = resp["flexColumns"] as? [[String: Any]] ?? []

                if vid.isEmpty, let firstCol = flexCols.first,
                   let runs = ((firstCol["musicResponsiveListItemFlexColumnRenderer"] as? [String: Any])?["text"] as? [String: Any])?["runs"] as? [[String: Any]] {
                    vid = ((runs.first?["navigationEndpoint"] as? [String: Any])?["watchEndpoint"] as? [String: Any])?["videoId"] as? String ?? ""
                }
                guard !vid.isEmpty, vid.count == 11, seenIds.insert(vid).inserted else { continue }

                var title = ""
                if let firstCol = flexCols.first,
                   let runs = ((firstCol["musicResponsiveListItemFlexColumnRenderer"] as? [String: Any])?["text"] as? [String: Any])?["runs"] as? [[String: Any]] {
                    title = (runs.first?["text"] as? String ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
                }
                guard !title.isEmpty else { continue }

                var artist = "YouTube Music"
                var album = "Single"
                var duration = "3:30"
                var isItemVideo = false

                if flexCols.count > 1,
                   let runs = ((flexCols[1]["musicResponsiveListItemFlexColumnRenderer"] as? [String: Any])?["text"] as? [String: Any])?["runs"] as? [[String: Any]] {
                    var parts: [String] = []
                    var curPart = ""
                    for r in runs {
                        let txt = (r["text"] as? String ?? "")
                        if txt == " • " || txt == "•" {
                            if !curPart.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                                parts.append(curPart.trimmingCharacters(in: .whitespacesAndNewlines))
                                curPart = ""
                            }
                        } else {
                            curPart += txt
                        }
                    }
                    if !curPart.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                        parts.append(curPart.trimmingCharacters(in: .whitespacesAndNewlines))
                    }

                    if parts.first?.lowercased() == "artist" { continue }

                    isItemVideo = parts.first?.lowercased() == "video"

                    if parts.count >= 3 {
                        artist = (parts[0] == "Song" || parts[0] == "Video") ? parts[1] : parts[0]
                        album = parts[1]
                        if let last = parts.last, last.range(of: #"^\d{1,2}:\d{2}"#, options: .regularExpression) != nil {
                            duration = last
                        }
                    } else if parts.count == 2 {
                        artist = (parts[0] == "Song" || parts[0] == "Video") ? parts[1] : parts[0]
                        if let last = parts.last, last.range(of: #"^\d{1,2}:\d{2}"#, options: .regularExpression) != nil {
                            duration = last
                        }
                    } else if parts.count == 1 {
                        artist = parts[0]
                    }
                }

                var thumb = "https://i.ytimg.com/vi/\(vid)/hqdefault.jpg"
                if let thumbs = (((resp["thumbnail"] as? [String: Any])?["musicThumbnailRenderer"] as? [String: Any])?["thumbnail"] as? [String: Any])?["thumbnails"] as? [[String: Any]],
                   let u = thumbs.last?["url"] as? String, !u.isEmpty {
                    thumb = u.hasPrefix("//") ? "https:\(u)" : u
                }
                thumb = ArtworkUrlHelper.getHighResArtworkUrl(thumb, videoId: vid)
                outList.append(
                    VideoItem(
                        id: vid,
                        title: title,
                        channelName: artist,
                        channelAvatarUrl: thumb,
                        subscriberCount: isItemVideo ? "Music Video" : "Song Track",
                        thumbnailUrl: thumb,
                        views: isItemVideo ? "Video" : "Song",
                        uploadTime: "YouTube Music",
                        duration: duration,
                        description: "\(title) - \(artist)",
                        likes: "100K",
                        isMusicTrack: true,
                        artist: artist,
                        album: album,
                        lyrics: "",
                        category: "Music"
                    )
                )
            }
        }

        let qLower = query.lowercased()
        let hasLiveInQuery = qLower.contains("live")
        let livePattern = #"(?i)\b(live\s+from|live\s+concert|live\s+performance|stage\s+performance|performing\s+live|on\s+stage|in\s+concert|live\s+at)\b|\((live)\)|\[(live)\]"#
        let disqualifiedPattern = #"(?i)\b(reaction|reacting|interview|podcast|review|vlog|behind the scenes|making of|teaser|trailer|promo|glimpse)\b"#

        let cleaned = outList.filter { item in
            let t = item.title.lowercased()
            if !qLower.contains("reaction") && !qLower.contains("interview") && !qLower.contains("podcast") {
                if let r = try? NSRegularExpression(pattern: disqualifiedPattern),
                   r.firstMatch(in: t, range: NSRange(t.startIndex..., in: t)) != nil {
                    return false
                }
            }
            return true
        }

        var baseList = cleaned
        if !hasLiveInQuery, let liveRegex = try? NSRegularExpression(pattern: livePattern) {
            let nonLive = cleaned.filter { item in
                let t = item.title.lowercased()
                return liveRegex.firstMatch(in: t, range: NSRange(t.startIndex..., in: t)) == nil
            }
            let live = cleaned.filter { item in
                let t = item.title.lowercased()
                return liveRegex.firstMatch(in: t, range: NSRange(t.startIndex..., in: t)) != nil
            }
            baseList = nonLive + live
        }

        // In Music Mode: If Top Result is a Video and a clean audio Song track exists ("bina video wala"),
        // promote the clean audio Song track to index 0 so Top Result displays the official studio song.
        let videoPattern = #"(?i)\b(official\s+video|official\s+music\s+video|music\s+video|video\s+song|full\s+video)\b|\((official\s+video|music\s+video)\)|\[(official\s+video|music\s+video)\]"#
        if baseList.count > 1 {
            let first = baseList[0]
            let firstIsVideo = first.views.lowercased() == "video" ||
                first.title.range(of: videoPattern, options: .regularExpression) != nil

            if firstIsVideo {
                let cleanFirstTitle = first.title.replacingOccurrences(of: videoPattern, with: "", options: .regularExpression)
                    .lowercased()
                let cleanWords = cleanFirstTitle.components(separatedBy: CharacterSet.alphanumerics.inverted).filter { $0.count > 2 }

                if let betterIdx = baseList.indices.dropFirst().first(where: { idx in
                    let cand = baseList[idx]
                    let isAudio = cand.views.lowercased() == "song" &&
                        cand.category != "Channel" &&
                        cand.title.range(of: videoPattern, options: .regularExpression) == nil
                    guard isAudio else { return false }
                    let candLower = cand.title.lowercased()
                    let matchesTitle = cleanWords.contains { candLower.contains($0) } || candLower.contains(cleanFirstTitle)
                    let matchesArtist = !cand.channelName.isEmpty && first.channelName.lowercased().contains(cand.channelName.lowercased())
                    return matchesTitle || matchesArtist
                }) {
                    var audioTrack = baseList.remove(at: betterIdx)
                    audioTrack.subscriberCount = "Top Result"
                    audioTrack.views = "Top Result"
                    baseList.insert(audioTrack, at: 0)
                }
            }
        }

        return baseList
    }

    static func getRelatedVideos(video: VideoItem, isMusicOnly: Bool = false) async -> [VideoItem] {
        // Strip bracketed tags like [Official Video] or (Lyrics)
        let cleanTitle = video.title.replacingOccurrences(of: #"[\[\(].*?[\]\)]"#, with: "", options: .regularExpression)
            .trimmingCharacters(in: .whitespacesAndNewlines)

        let query: String
        if !video.artist.isEmpty && !video.title.localizedCaseInsensitiveContains(video.artist) {
            query = "\(video.artist) \(cleanTitle)"
        } else if !video.channelName.isEmpty && !video.channelName.hasPrefix("@") && !video.title.localizedCaseInsensitiveContains(video.channelName) {
            query = "\(video.channelName) \(cleanTitle)"
        } else {
            query = cleanTitle.isEmpty ? video.title : cleanTitle
        }

        let res = await searchVideos(query: query, isMusicOnly: isMusicOnly, page: 0).filter { $0.id != video.id }
        if !res.isEmpty { return res }

        let secondaryQuery = video.artist.isEmpty ? video.channelName : video.artist
        if !secondaryQuery.isEmpty {
            let sec = await searchVideos(query: secondaryQuery, isMusicOnly: isMusicOnly, page: 0).filter { $0.id != video.id }
            if !sec.isEmpty { return sec }
        }

        if isMusicOnly {
            return VideoRepository.getMusicTracks().filter { $0.id != video.id }
        } else {
            return VideoRepository.sampleVideos.filter { $0.id != video.id }
        }
    }

    // MARK: - Parsing HTML ytInitialData
    private static func parseSearchResults(html: String, isMusic: Bool, query: String = "") -> [VideoItem] {
        var results: [VideoItem] = []

        let markers = ["var ytInitialData = ", "ytInitialData = ", "window[\"ytInitialData\"] = "]
        var jsonStr: String? = nil

        for marker in markers {
            if let startRange = html.range(of: marker) {
                let startPos = startRange.upperBound
                var endPos: String.Index? = nil
                if let endRange = html.range(of: ";</script>", range: startPos..<html.endIndex) {
                    endPos = endRange.lowerBound
                } else if let endRange2 = html.range(of: "</script>", range: startPos..<html.endIndex) {
                    endPos = endRange2.lowerBound
                }
                if let endPos = endPos {
                    jsonStr = String(html[startPos..<endPos]).trimmingCharacters(in: .whitespacesAndNewlines)
                    if jsonStr?.hasSuffix(";") == true {
                        jsonStr?.removeLast()
                    }
                    break
                }
            }
        }

        if let jsonStr = jsonStr,
           let data = jsonStr.data(using: .utf8),
           let root = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
           let contents = (((root["contents"] as? [String: Any])?["twoColumnSearchResultsRenderer"] as? [String: Any])?["primaryContents"] as? [String: Any])?["sectionListRenderer"] as? [String: Any],
           let sections = contents["contents"] as? [[String: Any]] {

            for section in sections {
                if let itemSection = (section["itemSectionRenderer"] as? [String: Any])?["contents"] as? [[String: Any]] {
                    for item in itemSection {
                        if !isMusic, let cr = item["channelRenderer"] as? [String: Any],
                           let parsed = YouTubeSyncEngine.parseChannelRenderer(cr) {
                            results.append(parsed)
                        }
                        if let vr = item["videoRenderer"] as? [String: Any],
                           var parsed = YouTubeSyncEngine.parseVideoRenderer(vr) {
                            if isMusic {
                                parsed.isMusicTrack = true
                                parsed.category = "Music"
                            }
                            results.append(parsed)
                        }
                        if let cvr = item["compactVideoRenderer"] as? [String: Any],
                           var parsed = YouTubeSyncEngine.parseVideoRenderer(cvr) {
                            if isMusic {
                                parsed.isMusicTrack = true
                                parsed.category = "Music"
                            }
                            results.append(parsed)
                        }
                        if let lvm = item["lockupViewModel"] as? [String: Any],
                           var parsed = YouTubeSyncEngine.parseLockupViewModel(lvm) {
                            if isMusic {
                                parsed.isMusicTrack = true
                                parsed.category = "Music"
                            }
                            results.append(parsed)
                        }
                    }
                }
            }
        }

        // Secondary regex fallback if JSON format differed
        if results.isEmpty {
            let pattern = #""videoId":"([a-zA-Z0-9_-]{11})""#
            if let regex = try? NSRegularExpression(pattern: pattern) {
                let nsHtml = html as NSString
                let matches = regex.matches(in: html, range: NSRange(location: 0, length: nsHtml.length))
                var seen = Set<String>()
                var cleanTitle = query.replacingOccurrences(of: " song", with: "", options: .caseInsensitive).trimmingCharacters(in: .whitespacesAndNewlines)
                if cleanTitle.isEmpty { cleanTitle = "Song Track" }
                let cleanArtist = "YouTube Music"

                for match in matches {
                    if match.numberOfRanges > 1 {
                        let vid = nsHtml.substring(with: match.range(at: 1))
                        if !seen.contains(vid) {
                            seen.insert(vid)
                            results.append(VideoItem(
                                id: vid,
                                title: cleanTitle,
                                channelName: cleanArtist,
                                channelAvatarUrl: "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=150",
                                subscriberCount: "1M subscribers",
                                thumbnailUrl: "https://img.youtube.com/vi/\(vid)/hqdefault.jpg",
                                views: "500K views",
                                uploadTime: "Recently",
                                duration: "3:45",
                                description: cleanTitle,
                                likes: "50K",
                                isMusicTrack: isMusic,
                                artist: cleanArtist,
                                album: "Single",
                                lyrics: "",
                                category: isMusic ? "Music" : "YouTube"
                            ))
                            if results.count >= 15 { break }
                        }
                    }
                }
            }
        }

        if isMusic && !results.isEmpty {
            let qLower = query.lowercased()
            let hasLiveInQuery = qLower.contains("live")
            let livePattern = #"(?i)\b(live\s+from|live\s+concert|live\s+performance|stage\s+performance|performing\s+live|on\s+stage|in\s+concert|live\s+at)\b|\((live)\)|\[(live)\]"#
            let disqualifiedPattern = #"(?i)\b(reaction|reacting|interview|podcast|review|vlog|behind the scenes|making of|teaser|trailer|promo|glimpse)\b"#

            let filtered = results.filter { item in
                let t = item.title.lowercased()
                if !qLower.contains("reaction") && !qLower.contains("interview") && !qLower.contains("podcast") {
                    if let r = try? NSRegularExpression(pattern: disqualifiedPattern),
                       r.firstMatch(in: t, range: NSRange(t.startIndex..., in: t)) != nil {
                        return false
                    }
                }
                return true
            }

            if !hasLiveInQuery, let liveRegex = try? NSRegularExpression(pattern: livePattern) {
                let nonLive = filtered.filter { item in
                    let t = item.title.lowercased()
                    return liveRegex.firstMatch(in: t, range: NSRange(t.startIndex..., in: t)) == nil
                }
                let live = filtered.filter { item in
                    let t = item.title.lowercased()
                    return liveRegex.firstMatch(in: t, range: NSRange(t.startIndex..., in: t)) != nil
                }
                if !nonLive.isEmpty {
                    return nonLive + live
                }
            }
            return filtered
        }

        return results
    }
}
