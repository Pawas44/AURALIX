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

        // 1. Try LRCLib direct get endpoint
        if let encTitle = cleanTitle.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed),
           let encArtist = cleanArtist.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) {
            var urlStr = "https://lrclib.net/api/get?track_name=\(encTitle)&artist_name=\(encArtist)"
            if durationSeconds > 0 {
                urlStr += "&duration=\(durationSeconds)"
            }
            if let url = URL(string: urlStr) {
                var req = URLRequest(url: url, timeoutInterval: 5.0)
                req.setValue("AURALIX-Music/1.0 (https://github.com/Pawas44/AURALIX)", forHTTPHeaderField: "User-Agent")
                if let (data, response) = try? await URLSession.shared.data(for: req),
                   let httpRes = response as? HTTPURLResponse, httpRes.statusCode == 200,
                   let json = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any] {
                    result = parseLrcResponse(json)
                }
            }
        }

        // 2. Fallback to LRCLib search endpoint
        if result == nil || result?.lines.isEmpty == true {
            let query = "\(cleanTitle) \(cleanArtist)".trimmingCharacters(in: .whitespacesAndNewlines)
            if let encQ = query.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed),
               let searchUrl = URL(string: "https://lrclib.net/api/search?q=\(encQ)") {
                var req = URLRequest(url: searchUrl, timeoutInterval: 5.0)
                req.setValue("AURALIX-Music/1.0 (https://github.com/Pawas44/AURALIX)", forHTTPHeaderField: "User-Agent")
                if let (data, response) = try? await URLSession.shared.data(for: req),
                   let httpRes = response as? HTTPURLResponse, httpRes.statusCode == 200,
                   let arr = (try? JSONSerialization.jsonObject(with: data)) as? [[String: Any]] {
                    for item in arr {
                        if let parsed = parseLrcResponse(item), (parsed.isSynced || !parsed.plainLyrics.isEmpty) {
                            result = parsed
                            break
                        }
                    }
                }
            }
        }

        lock.lock()
        memoryCache[cacheKey] = result
        lock.unlock()

        return result
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
        // Remove bracketed info like [Official Video], (Remix), etc.
        if let regex = try? NSRegularExpression(pattern: "(?i)\\[.*?\\]|\\(.*?\\)") {
            res = regex.stringByReplacingMatches(in: res, range: NSRange(location: 0, length: (res as NSString).length), withTemplate: "")
        }
        // Remove ft. / feat.
        if let regex = try? NSRegularExpression(pattern: "(?i)ft\\..*|feat\\..*") {
            res = regex.stringByReplacingMatches(in: res, range: NSRange(location: 0, length: (res as NSString).length), withTemplate: "")
        }
        // Remove boilerplate keywords
        if let regex = try? NSRegularExpression(pattern: "(?i)official video|music video|lyric video|audio") {
            res = regex.stringByReplacingMatches(in: res, range: NSRange(location: 0, length: (res as NSString).length), withTemplate: "")
        }
        return res.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private func cleanArtistName(_ name: String) -> String {
        var res = name
        if let regex = try? NSRegularExpression(pattern: "(?i) - Topic|Official|VEVO") {
            res = regex.stringByReplacingMatches(in: res, range: NSRange(location: 0, length: (res as NSString).length), withTemplate: "")
        }
        return res.trimmingCharacters(in: .whitespacesAndNewlines)
    }
}
