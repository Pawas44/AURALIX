import Foundation

struct LyricLine: Identifiable, Equatable {
    var id: String { "\(timestampMs)_\(text)" }
    let timestampMs: Double
    let text: String
    var romanizedText: String? = nil
}

struct LyricsResult {
    let lines: [LyricLine]
    let plainLyrics: String
    let isSynced: Bool
}

final class LyricsEngine {
    static let shared = LyricsEngine()
    private var memoryCache: [String: LyricsResult] = [:]
    private let lock = NSLock()

    private init() {}

    private func getCached(key: String) -> LyricsResult? {
        lock.lock()
        defer { lock.unlock() }
        return memoryCache[key]
    }

    private func setCached(key: String, result: LyricsResult?) {
        guard let result = result else { return }
        lock.lock()
        defer { lock.unlock() }
        memoryCache[key] = result
    }

    func getLyrics(
        trackTitle: String,
        artistName: String,
        durationSeconds: Int = 0,
        videoId: String = ""
    ) async -> LyricsResult? {
        let extracted = SongMetadataExtractor.extractSongMetadata(from: trackTitle, uploader: artistName)
        let cleanTitle = !extracted.songTitle.isEmpty ? extracted.songTitle : cleanTrackName(trackTitle)
        let cleanArtist = !extracted.primaryArtist.isEmpty ? extracted.primaryArtist : cleanArtistName(artistName)
        let cacheKey = "\(cleanTitle.lowercased())_\(cleanArtist.lowercased())_\(videoId)"

        if let cached = getCached(key: cacheKey) {
            return cached
        }

        var result: LyricsResult? = nil

        // 1. Direct LRCLib GET endpoint (without strict duration constraint first)
        result = await tryGetLrc(title: cleanTitle, artist: cleanArtist, duration: 0)

        // 1b. If not found or not synced, try with duration if available
        if (result == nil || result?.isSynced == false) && durationSeconds > 0 {
            let durResult = await tryGetLrc(title: cleanTitle, artist: cleanArtist, duration: durationSeconds)
            if durResult?.isSynced == true {
                result = durResult
            }
        }

        // 1c. If YouTube title has "Artist - Track" format
        if (result == nil || result?.isSynced == false) && trackTitle.contains(" - ") {
            let parts = trackTitle.components(separatedBy: " - ")
            if parts.count >= 2 {
                let guessedArtist = cleanArtistName(parts[0])
                let guessedTitle = cleanTrackName(parts[1])
                let alt = await tryGetLrc(title: guessedTitle, artist: guessedArtist, duration: 0)
                if alt?.isSynced == true {
                    result = alt
                } else if result == nil {
                    result = alt
                }
            }
        }

        // 2. LRCLib Search: query = title + artist
        if result == nil || result?.isSynced == false {
            let q = "\(cleanTitle) \(cleanArtist)".trimmingCharacters(in: .whitespacesAndNewlines)
            if !q.isEmpty {
                if let searched = await trySearchLrc(query: q, expectedTitle: cleanTitle, expectedArtist: cleanArtist) {
                    if searched.isSynced {
                        result = searched
                    } else if result == nil {
                        result = searched
                    }
                }
            }
        }

        // 3. Fallback to Official YouTube Music Native Lyrics if LRCLib did not provide synced lyrics
        if (result == nil || result?.isSynced == false) && !videoId.isEmpty {
            if let ytmLyrics = await fetchYouTubeMusicOfficialLyrics(videoId: videoId) {
                result = ytmLyrics
            }
        }

        setCached(key: cacheKey, result: result)
        return result
    }

    func fetchYouTubeMusicOfficialLyrics(videoId: String) async -> LyricsResult? {
        guard !videoId.isEmpty else { return nil }
        guard let nextUrl = URL(string: "https://music.youtube.com/youtubei/v1/next") else { return nil }

        var nextReq = URLRequest(url: nextUrl, timeoutInterval: 8.0)
        nextReq.httpMethod = "POST"
        nextReq.setValue("application/json", forHTTPHeaderField: "Content-Type")
        nextReq.setValue("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36", forHTTPHeaderField: "User-Agent")
        nextReq.setValue("https://music.youtube.com/", forHTTPHeaderField: "Referer")

        let nextPayload: [String: Any] = [
            "context": [
                "client": [
                    "clientName": "WEB_REMIX",
                    "clientVersion": "1.20231214.01.00",
                    "hl": "en",
                    "gl": "US"
                ]
            ],
            "videoId": videoId
        ]
        nextReq.httpBody = try? JSONSerialization.data(withJSONObject: nextPayload)

        guard let (nextData, nextResp) = try? await URLSession.shared.data(for: nextReq),
              let httpNext = nextResp as? HTTPURLResponse, httpNext.statusCode == 200,
              let nextJson = (try? JSONSerialization.jsonObject(with: nextData)) as? [String: Any],
              let contents = nextJson["contents"] as? [String: Any],
              let watchNext = contents["singleColumnMusicWatchNextResultsRenderer"] as? [String: Any],
              let tabbed = watchNext["tabbedRenderer"] as? [String: Any],
              let watchNextTabbed = tabbed["watchNextTabbedResultsRenderer"] as? [String: Any],
              let tabs = watchNextTabbed["tabs"] as? [[String: Any]] else {
            return nil
        }

        var browseId: String? = nil
        var lyricsParams: String? = nil

        for tab in tabs {
            guard let tabRenderer = tab["tabRenderer"] as? [String: Any] else { continue }
            let title = (tabRenderer["title"] as? String)?.lowercased() ?? ""
            if let endpoint = tabRenderer["endpoint"] as? [String: Any],
               let browseEndpoint = endpoint["browseEndpoint"] as? [String: Any],
               let bId = browseEndpoint["browseId"] as? String {
                if bId.hasPrefix("MPLYt") || title.contains("lyrics") {
                    browseId = bId
                    lyricsParams = browseEndpoint["params"] as? String
                    break
                }
            }
        }

        if browseId == nil && tabs.count > 1 {
            if let tabRenderer = tabs[1]["tabRenderer"] as? [String: Any],
               let endpoint = tabRenderer["endpoint"] as? [String: Any],
               let browseEndpoint = endpoint["browseEndpoint"] as? [String: Any],
               let bId = browseEndpoint["browseId"] as? String {
                browseId = bId
                lyricsParams = browseEndpoint["params"] as? String
            }
        }

        guard let validBrowseId = browseId, !validBrowseId.isEmpty else { return nil }

        guard let browseUrl = URL(string: "https://music.youtube.com/youtubei/v1/browse") else { return nil }
        var browseReq = URLRequest(url: browseUrl, timeoutInterval: 8.0)
        browseReq.httpMethod = "POST"
        browseReq.setValue("application/json", forHTTPHeaderField: "Content-Type")
        browseReq.setValue("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36", forHTTPHeaderField: "User-Agent")
        browseReq.setValue("https://music.youtube.com/", forHTTPHeaderField: "Referer")

        var browsePayload: [String: Any] = [
            "context": [
                "client": [
                    "clientName": "WEB_REMIX",
                    "clientVersion": "1.20231214.01.00",
                    "hl": "en",
                    "gl": "US"
                ]
            ],
            "browseId": validBrowseId
        ]
        if let params = lyricsParams, !params.isEmpty {
            browsePayload["params"] = params
        }
        browseReq.httpBody = try? JSONSerialization.data(withJSONObject: browsePayload)

        guard let (browseData, browseResp) = try? await URLSession.shared.data(for: browseReq),
              let httpBrowse = browseResp as? HTTPURLResponse, httpBrowse.statusCode == 200,
              let browseJson = (try? JSONSerialization.jsonObject(with: browseData)) as? [String: Any],
              let bContents = browseJson["contents"] as? [String: Any],
              let sectionList = bContents["sectionListRenderer"] as? [String: Any],
              let sectionItems = sectionList["contents"] as? [[String: Any]] else {
            return nil
        }

        for item in sectionItems {
            guard let shelf = item["musicDescriptionShelfRenderer"] as? [String: Any],
                  let desc = shelf["description"] as? [String: Any],
                  let runs = desc["runs"] as? [[String: Any]] else { continue }

            var plainText = ""
            for run in runs {
                plainText += (run["text"] as? String) ?? ""
            }
            let trimmed = plainText.trimmingCharacters(in: .whitespacesAndNewlines)
            if !trimmed.isEmpty {
                let rawLines = trimmed.components(separatedBy: .newlines)
                let lines = rawLines.enumerated().compactMap { idx, line -> LyricLine? in
                    let l = line.trimmingCharacters(in: .whitespacesAndNewlines)
                    guard !l.isEmpty else { return nil }
                    return LyricLine(timestampMs: Double(idx * 3000), text: l)
                }
                return LyricsResult(lines: lines, plainLyrics: trimmed, isSynced: false)
            }
        }

        return nil
    }

    private func tryGetLrc(title: String, artist: String, duration: Int) async -> LyricsResult? {
        guard !title.isEmpty,
              let encTitle = title.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed),
              let encArtist = artist.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) else {
            return nil
        }
        var urlStr = "https://lrclib.net/api/get?track_name=\(encTitle)&artist_name=\(encArtist)"
        if duration > 0 {
            urlStr += "&duration=\(duration)"
        }
        guard let url = URL(string: urlStr) else { return nil }

        var req = URLRequest(url: url, timeoutInterval: 5.0)
        req.setValue("AURALIX-Music/1.0 (https://github.com/Pawas44/AURALIX)", forHTTPHeaderField: "User-Agent")

        guard let (data, response) = try? await URLSession.shared.data(for: req),
              let httpRes = response as? HTTPURLResponse, httpRes.statusCode == 200,
              let json = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any] else {
            return nil
        }
        return parseLrcResponse(json)
    }

    private func trySearchLrc(query: String, expectedTitle: String, expectedArtist: String) async -> LyricsResult? {
        guard !query.isEmpty,
              let encQ = query.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed),
              let url = URL(string: "https://lrclib.net/api/search?q=\(encQ)") else {
            return nil
        }

        var req = URLRequest(url: url, timeoutInterval: 5.0)
        req.setValue("AURALIX-Music/1.0 (https://github.com/Pawas44/AURALIX)", forHTTPHeaderField: "User-Agent")

        guard let (data, response) = try? await URLSession.shared.data(for: req),
              let httpRes = response as? HTTPURLResponse, httpRes.statusCode == 200,
              let arr = (try? JSONSerialization.jsonObject(with: data)) as? [[String: Any]] else {
            return nil
        }

        let expTitleClean = expectedTitle.lowercased().trimmingCharacters(in: .whitespacesAndNewlines)
        let expArtistClean = expectedArtist.lowercased().trimmingCharacters(in: .whitespacesAndNewlines)
        var bestCandidate: [String: Any]? = nil
        var bestScore = -1

        for item in arr {
            let resTrack = ((item["trackName"] as? String) ?? "").lowercased().trimmingCharacters(in: .whitespacesAndNewlines)
            let resArtist = ((item["artistName"] as? String) ?? "").lowercased().trimmingCharacters(in: .whitespacesAndNewlines)

            let titleMatches = resTrack.contains(expTitleClean) || expTitleClean.contains(resTrack) ||
                shareSignificantWord(resTrack, expTitleClean)

            if !titleMatches { continue }

            let isGenericArtist = expArtistClean.isEmpty || expArtistClean.contains("topic") || expArtistClean.contains("vevo")
            var artistMatches = true
            if !isGenericArtist {
                artistMatches = resArtist.contains(expArtistClean) || expArtistClean.contains(resArtist) ||
                    shareSignificantWord(resArtist, expArtistClean)
            }

            if !artistMatches {
                continue
            }

            let hasSynced = !((item["syncedLyrics"] as? String) ?? "").isEmpty
            let hasPlain = !((item["plainLyrics"] as? String) ?? "").isEmpty
            if !hasSynced && !hasPlain { continue }

            var score = 0
            if hasSynced { score += 50 }
            if hasPlain { score += 5 }
            if resArtist == expArtistClean { score += 30 }
            if resTrack == expTitleClean { score += 30 }

            if score > bestScore {
                bestScore = score
                bestCandidate = item
            }
        }

        if let best = bestCandidate {
            return parseLrcResponse(best)
        }
        return nil
    }

    private func shareSignificantWord(_ s1: String, _ s2: String) -> Bool {
        let stopwords: Set<String> = ["the", "and", "official", "audio", "video", "feat", "with", "prod"]
        let delimiters = CharacterSet(charactersIn: " -_./&|()")
        let w1 = s1.components(separatedBy: delimiters).map { $0.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() }.filter { $0.count > 2 && !stopwords.contains($0) }
        let w2 = s2.components(separatedBy: delimiters).map { $0.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() }.filter { $0.count > 2 && !stopwords.contains($0) }
        return w1.contains { w2.contains($0) }
    }

    private func parseLrcResponse(_ json: [String: Any]) -> LyricsResult? {
        let synced = (json["syncedLyrics"] as? String) ?? ""
        let plain = (json["plainLyrics"] as? String) ?? ""

        if !synced.isEmpty {
            let lines = parseLrcText(synced)
            if !lines.isEmpty {
                return LyricsResult(lines: lines, plainLyrics: plain, isSynced: true)
            }
        }

        if !plain.isEmpty {
            let rawLines = plain.components(separatedBy: .newlines)
            let parsedLines = rawLines.enumerated().compactMap { idx, line -> LyricLine? in
                let trimmed = line.trimmingCharacters(in: .whitespacesAndNewlines)
                guard !trimmed.isEmpty else { return nil }
                let romanized = TransliterationEngine.shared.canRomanize(trimmed) ? TransliterationEngine.shared.romanize(trimmed) : nil
                return LyricLine(timestampMs: Double(idx * 3000), text: trimmed, romanizedText: romanized)
            }
            return LyricsResult(lines: parsedLines, plainLyrics: plain, isSynced: false)
        }

        return nil
    }

    private func parseLrcText(_ lrc: String) -> [LyricLine] {
        var lines: [LyricLine] = []
        let pattern = "\\[(\\d{2}):(\\d{2}(?:\\.\\d+)?)\\](.*)"
        guard let regex = try? NSRegularExpression(pattern: pattern) else { return [] }

        for rawLine in lrc.components(separatedBy: .newlines) {
            let trimmed = rawLine.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !trimmed.isEmpty else { continue }
            let nsStr = trimmed as NSString
            let matches = regex.matches(in: trimmed, range: NSRange(location: 0, length: nsStr.length))
            if let m = matches.first, m.numberOfRanges > 3 {
                let minStr = nsStr.substring(with: m.range(at: 1))
                let secStr = nsStr.substring(with: m.range(at: 2))
                let text = nsStr.substring(with: m.range(at: 3)).trimmingCharacters(in: .whitespacesAndNewlines)

                let min = Double(minStr) ?? 0.0
                let sec = Double(secStr) ?? 0.0
                let ms = (min * 60.0 + sec) * 1000.0
                let romanized = TransliterationEngine.shared.canRomanize(text) ? TransliterationEngine.shared.romanize(text) : nil
                lines.append(LyricLine(timestampMs: ms, text: text, romanizedText: romanized))
            }
        }

        return lines.sorted { $0.timestampMs < $1.timestampMs }
    }

    private func cleanTrackName(_ name: String) -> String {
        var res = name
        // Remove bracketed info
        if let regex = try? NSRegularExpression(pattern: "(?i)\\[.*?\\]|\\(.*?\\)") {
            res = regex.stringByReplacingMatches(in: res, range: NSRange(location: 0, length: (res as NSString).length), withTemplate: "")
        }
        // Remove boilerplate keywords
        if let regex = try? NSRegularExpression(pattern: "(?i)\\b(official music video|official video|music video|lyric video|visualizer|official audio|audio|video|4k|hd|remastered|lyrics|prod\\. by.*)\\b") {
            res = regex.stringByReplacingMatches(in: res, range: NSRange(location: 0, length: (res as NSString).length), withTemplate: "")
        }
        // Remove feat.
        if let regex = try? NSRegularExpression(pattern: "(?i)\\b(ft\\.?|feat\\.?|featuring)\\s+[^|\\-]+") {
            res = regex.stringByReplacingMatches(in: res, range: NSRange(location: 0, length: (res as NSString).length), withTemplate: "")
        }
        // Remove pipe
        if let regex = try? NSRegularExpression(pattern: "\\|.*$") {
            res = regex.stringByReplacingMatches(in: res, range: NSRange(location: 0, length: (res as NSString).length), withTemplate: "")
        }
        res = res.trimmingCharacters(in: .whitespacesAndNewlines)
        if res.contains(" - ") {
            let parts = res.components(separatedBy: " - ")
            if parts.count >= 2 {
                res = parts[1].trimmingCharacters(in: .whitespacesAndNewlines)
            }
        }
        return res.trimmingCharacters(in: CharacterSet(charactersIn: "\"' \t\n"))
    }

    private func cleanArtistName(_ name: String) -> String {
        var res = name
        if let regex = try? NSRegularExpression(pattern: "(?i) - Topic|Official|VEVO") {
            res = regex.stringByReplacingMatches(in: res, range: NSRange(location: 0, length: (res as NSString).length), withTemplate: "")
        }
        if let regex = try? NSRegularExpression(pattern: "(?i)\\[.*?\\]|\\(.*?\\)") {
            res = regex.stringByReplacingMatches(in: res, range: NSRange(location: 0, length: (res as NSString).length), withTemplate: "")
        }
        return res.trimmingCharacters(in: .whitespacesAndNewlines)
    }
}
