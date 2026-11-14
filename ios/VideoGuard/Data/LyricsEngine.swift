import Foundation

struct LyricLine: Identifiable, Equatable {
    var id: String { "\(timestampMs)_\(text)" }
    let timestampMs: Double
    let text: String
}

struct LyricsResult {
    let lines: [LyricLine]
    let plainLyrics: String
    let isSynced: Bool
}

final class LyricsEngine {
    static let shared = LyricsEngine()
    private var memoryCache: [String: LyricsResult?] = [:]
    private let lock = NSLock()

    private init() {}

    func getLyrics(
        trackTitle: String,
        artistName: String,
        durationSeconds: Int = 0
    ) async -> LyricsResult? {
        let cleanTitle = cleanTrackName(trackTitle)
        let cleanArtist = cleanArtistName(artistName)
        let cacheKey = "\(cleanTitle.lowercased())_\(cleanArtist.lowercased())"

        lock.lock()
        if let cached = memoryCache[cacheKey] {
            lock.unlock()
            return cached
        }
        lock.unlock()

        var result: LyricsResult? = nil

        // 1. Direct LRCLib GET endpoint
        result = await tryGetLrc(title: cleanTitle, artist: cleanArtist, duration: durationSeconds)

        // 1b. If YouTube title has "Artist - Track" format
        if (result == nil || result?.lines.isEmpty == true) && trackTitle.contains(" - ") {
            let parts = trackTitle.components(separatedBy: " - ")
            if parts.count >= 2 {
                let guessedArtist = cleanArtistName(parts[0])
                let guessedTitle = cleanTrackName(parts[1])
                result = await tryGetLrc(title: guessedTitle, artist: guessedArtist, duration: durationSeconds)
            }
        }

        // 2. LRCLib Search: query = title + artist
        if result == nil || result?.lines.isEmpty == true {
            let q = "\(cleanTitle) \(cleanArtist)".trimmingCharacters(in: .whitespacesAndNewlines)
            if !q.isEmpty {
                result = await trySearchLrc(query: q, expectedTitle: cleanTitle, expectedArtist: cleanArtist)
            }
        }

        // 3. LRCLib Search: query = title alone (Strictly verified against expected artist/title)
        if result == nil || result?.lines.isEmpty == true {
            if !cleanTitle.isEmpty {
                result = await trySearchLrc(query: cleanTitle, expectedTitle: cleanTitle, expectedArtist: cleanArtist)
            }
        }

        lock.lock()
        memoryCache[cacheKey] = result
        lock.unlock()

        return result
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
            if hasSynced { score += 20 }
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
                return LyricLine(timestampMs: Double(idx * 3000), text: trimmed)
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
                lines.append(LyricLine(timestampMs: ms, text: text))
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
