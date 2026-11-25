import Foundation
#if canImport(UIKit)
import UIKit
#endif

struct LyricLine: Identifiable, Equatable {
    var id: String { "\(timestampMs)_\(text)" }
    let timestampMs: Double
    let text: String
    var romanizedText: String? = nil
}

struct LyricsResult: Equatable {
    let lines: [LyricLine]
    let plainLyrics: String
    let isSynced: Bool
    var isEstimated: Bool = false
    var confidenceScore: Int = 100
    var source: String = "LRCLIB"
}

struct LyricsSearchResultItem: Identifiable, Equatable {
    let id: Int
    let trackName: String
    let artistName: String
    let durationSeconds: Int
    let isSynced: Bool
    let syncedLyrics: String
    let plainLyrics: String
}

enum LyricsRomanizeMode: String, CaseIterable {
    case both = "BOTH"
    case romanized = "ROMANIZED"
    case native = "NATIVE"

    var displayLabel: String {
        switch self {
        case .both: return "ABC: Both"
        case .romanized: return "ABC: Roman"
        case .native: return "ABC: Off"
        }
    }
}

final class LyricsEngine {
    static let shared = LyricsEngine()
    private var memoryCache: [String: LyricsResult] = [:]
    private let lock = NSLock()

    private init() {}

    private var cacheDirectory: URL? {
        let urls = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)
        guard let dir = urls.first?.appendingPathComponent("lyrics_cache") else { return nil }
        if !FileManager.default.fileExists(atPath: dir.path) {
            try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        }
        return dir
    }

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

    // MARK: - Timing Offsets (UserDefaults)

    func getOffsetMs(videoId: String) -> Double {
        UserDefaults.standard.double(forKey: "lyrics_offset_\(videoId)")
    }

    func setOffsetMs(videoId: String, offsetMs: Double) {
        UserDefaults.standard.set(offsetMs, forKey: "lyrics_offset_\(videoId)")
    }

    func adjustOffsetMs(videoId: String, deltaMs: Double) -> Double {
        let current = getOffsetMs(videoId: videoId)
        let updated = min(max(current + deltaMs, -10000.0), 10000.0)
        setOffsetMs(videoId: videoId, offsetMs: updated)
        return updated
    }

    func resetOffset(videoId: String) {
        UserDefaults.standard.removeObject(forKey: "lyrics_offset_\(videoId)")
    }

    func getRomanizeMode() -> LyricsRomanizeMode {
        let str = UserDefaults.standard.string(forKey: "lyrics_romanize_mode") ?? LyricsRomanizeMode.both.rawValue
        return LyricsRomanizeMode(rawValue: str) ?? .both
    }

    func setRomanizeMode(_ mode: LyricsRomanizeMode) {
        UserDefaults.standard.set(mode.rawValue, forKey: "lyrics_romanize_mode")
    }

    // MARK: - Clipboard & User Custom Lyrics

    func copyLyricsToClipboard(lyrics: LyricsResult?, trackTitle: String, artist: String) -> Bool {
        guard let lyrics = lyrics else { return false }
        let textToCopy: String
        if !lyrics.lines.isEmpty {
            textToCopy = lyrics.lines.map { $0.text }.joined(separator: "\n")
        } else {
            textToCopy = lyrics.plainLyrics
        }
        guard !textToCopy.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return false }
        #if canImport(UIKit)
        UIPasteboard.general.string = textToCopy
        return true
        #else
        return false
        #endif
    }

    func saveUserCustomLyrics(videoId: String, rawText: String, durationSeconds: Int) -> LyricsResult {
        let parsedLines = parseLrcText(rawText)
        let res: LyricsResult
        if !parsedLines.isEmpty {
            res = LyricsResult(
                lines: parsedLines,
                plainLyrics: rawText,
                isSynced: true,
                isEstimated: false,
                confidenceScore: 100,
                source: "Custom (Synced)"
            )
        } else {
            let estimated = calculateEstimatedSync(plain: rawText, durationSeconds: durationSeconds)
            res = LyricsResult(
                lines: estimated.lines,
                plainLyrics: rawText,
                isSynced: true,
                isEstimated: true,
                confidenceScore: 100,
                source: "Custom (Paced)"
            )
        }
        setCached(key: videoId, result: res)
        saveLyricsToDisk(videoId: videoId, result: res)
        return res
    }

    func parseCustomLrc(synced: String, plain: String) -> LyricsResult {
        if !synced.isEmpty {
            let lines = parseLrcText(synced)
            if !lines.isEmpty {
                return LyricsResult(
                    lines: lines,
                    plainLyrics: plain,
                    isSynced: true,
                    isEstimated: false,
                    confidenceScore: 100,
                    source: "LRCLIB (Synced)"
                )
            }
        }
        let estimated = calculateEstimatedSync(plain: plain, durationSeconds: 0)
        return LyricsResult(
            lines: estimated.lines,
            plainLyrics: plain,
            isSynced: false,
            isEstimated: false,
            confidenceScore: 70,
            source: "LRCLIB (Plain)"
        )
    }

    // MARK: - Search Alternatives

    func searchAlternativeLyrics(query: String) async -> [LyricsSearchResultItem] {
        let q = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !q.isEmpty,
              let encQ = q.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed),
              let url = URL(string: "https://lrclib.net/api/search?q=\(encQ)") else {
            return []
        }
        var req = URLRequest(url: url, timeoutInterval: 6.0)
        req.setValue("AURALIX-Music/1.0 (iOS)", forHTTPHeaderField: "User-Agent")

        guard let (data, response) = try? await URLSession.shared.data(for: req),
              let httpRes = response as? HTTPURLResponse, httpRes.statusCode == 200,
              let arr = (try? JSONSerialization.jsonObject(with: data)) as? [[String: Any]] else {
            return []
        }

        var results: [LyricsSearchResultItem] = []
        for (idx, item) in arr.prefix(12).enumerated() {
            let tName = (item["trackName"] as? String) ?? ""
            let aName = (item["artistName"] as? String) ?? ""
            let dur = Int((item["duration"] as? Double) ?? 0)
            let syn = (item["syncedLyrics"] as? String) ?? ""
            let pl = (item["plainLyrics"] as? String) ?? ""
            if !syn.isEmpty || !pl.isEmpty {
                results.append(
                    LyricsSearchResultItem(
                        id: (item["id"] as? Int) ?? idx,
                        trackName: tName,
                        artistName: aName,
                        durationSeconds: dur,
                        isSynced: !syn.isEmpty,
                        syncedLyrics: syn,
                        plainLyrics: pl
                    )
                )
            }
        }
        return results
    }

    // MARK: - Disk Caching

    func getLyricsFromDisk(videoId: String) -> LyricsResult? {
        guard !videoId.isEmpty, let dir = cacheDirectory else { return nil }
        let file = dir.appendingPathComponent("\(videoId).json")
        guard let data = try? Data(contentsOf: file),
              let json = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any] else { return nil }
        let isSynced = (json["isSynced"] as? Bool) ?? false
        let isEstimated = (json["isEstimated"] as? Bool) ?? false
        let confidenceScore = (json["confidenceScore"] as? Int) ?? 100
        let source = (json["source"] as? String) ?? (isEstimated ? "Estimated Sync" : "LRCLIB")
        let plainLyrics = (json["plainLyrics"] as? String) ?? ""
        let rawLines = (json["lines"] as? [[String: Any]]) ?? []
        let lines = rawLines.compactMap { d -> LyricLine? in
            guard let text = d["text"] as? String else { return nil }
            let ts = (d["timestampMs"] as? Double) ?? 0.0
            let rom = d["romanizedText"] as? String
            return LyricLine(timestampMs: ts, text: text, romanizedText: rom)
        }
        if !lines.isEmpty || !plainLyrics.isEmpty {
            return LyricsResult(
                lines: lines,
                plainLyrics: plainLyrics,
                isSynced: isSynced,
                isEstimated: isEstimated,
                confidenceScore: confidenceScore,
                source: source
            )
        }
        return nil
    }

    func saveLyricsToDisk(videoId: String, result: LyricsResult) {
        guard !videoId.isEmpty, let dir = cacheDirectory else { return }
        let file = dir.appendingPathComponent("\(videoId).json")
        let linesArray = result.lines.map { l -> [String: Any] in
            var d: [String: Any] = ["timestampMs": l.timestampMs, "text": l.text]
            if let r = l.romanizedText { d["romanizedText"] = r }
            return d
        }
        let json: [String: Any] = [
            "isSynced": result.isSynced,
            "isEstimated": result.isEstimated,
            "confidenceScore": result.confidenceScore,
            "source": result.source,
            "plainLyrics": result.plainLyrics,
            "lines": linesArray
        ]
        if let data = try? JSONSerialization.data(withJSONObject: json, options: []) {
            try? data.write(to: file)
        }
    }

    // MARK: - Main Lyrics Fetching with Fallbacks

    func getLyrics(
        trackTitle: String,
        artistName: String,
        durationSeconds: Int = 0,
        videoId: String = ""
    ) async -> LyricsResult? {
        let extracted = SongMetadataExtractor.extract(title: trackTitle, rawArtist: artistName)
        let cleanTitle = !extracted.songTitle.isEmpty ? extracted.songTitle : cleanTrackName(trackTitle)
        let cleanArtist = !extracted.primaryArtist.isEmpty ? extracted.primaryArtist : cleanArtistName(artistName)
        let cacheKey = "\(cleanTitle.lowercased())_\(cleanArtist.lowercased())_\(videoId)"

        // 1. In-memory Cache check
        if let cached = getCached(key: cacheKey) {
            return cached
        }

        // 2. Persistent Disk Cache check (offline songs)
        if !videoId.isEmpty, let fromDisk = getLyricsFromDisk(videoId: videoId) {
            setCached(key: cacheKey, result: fromDisk)
            return fromDisk
        }

        let cacheAndReturn: (LyricsResult) -> LyricsResult = { res in
            self.setCached(key: cacheKey, result: res)
            if !videoId.isEmpty {
                self.saveLyricsToDisk(videoId: videoId, result: res)
            }
            return res
        }

        var result: LyricsResult? = nil

        // Priority 1: Direct LRCLib GET endpoint
        result = await tryGetLrc(title: cleanTitle, artist: cleanArtist, duration: 0)

        // Priority 1b: If not synced, try with duration
        if (result == nil || result?.isSynced == false) && durationSeconds > 0 {
            let durResult = await tryGetLrc(title: cleanTitle, artist: cleanArtist, duration: durationSeconds)
            if durResult?.isSynced == true {
                result = durResult
            }
        }

        // Priority 2: LRCLib Search: query = title + artist
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

        // Priority 3: Fallback to YouTube Music Official Native Lyrics
        if (result == nil || result?.isSynced == false) && !videoId.isEmpty {
            if let ytmLyrics = await fetchYouTubeMusicOfficialLyrics(videoId: videoId, durationSeconds: durationSeconds) {
                result = ytmLyrics
            }
        }

        // If only plain lyrics were found, calculate musical pacing
        if let candidate = result {
            if !candidate.isSynced && !candidate.plainLyrics.isEmpty {
                let paced = calculateEstimatedSync(plain: candidate.plainLyrics, durationSeconds: durationSeconds)
                return cacheAndReturn(paced)
            } else {
                return cacheAndReturn(candidate)
            }
        }

        return nil
    }

    func calculateEstimatedSync(plain: String, durationSeconds: Int) -> LyricsResult {
        let rawLines = plain.components(separatedBy: .newlines)
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty && !$0.hasPrefix("[") && !$0.hasPrefix("Source:") }

        guard !rawLines.isEmpty else {
            return LyricsResult(lines: [], plainLyrics: plain, isSynced: false, isEstimated: false, confidenceScore: 0, source: "Empty")
        }

        guard durationSeconds > 30 else {
            let lines = rawLines.map { text in
                let rom = TransliterationEngine.shared.canRomanize(text) ? TransliterationEngine.shared.romanize(text) : nil
                return LyricLine(timestampMs: 0, text: text, romanizedText: rom)
            }
            return LyricsResult(lines: lines, plainLyrics: plain, isSynced: false, isEstimated: false, confidenceScore: 50, source: "Plain Text")
        }

        let totalMs = Double(durationSeconds * 1000)
        let introMs = min(max(totalMs * 0.07, 6000), 18000)
        let outroMs = min(max(totalMs * 0.06, 5000), 15000)
        let availableMs = max(totalMs - introMs - outroMs, 10000)

        let lineWeights = rawLines.map { line -> Double in
            let charWeight = Double(line.count)
            let wordCount = Double(line.components(separatedBy: .whitespaces).count)
            return max(charWeight + (wordCount * 3.0), 10.0)
        }
        let totalWeight = lineWeights.reduce(0, +)

        var currentMs = introMs
        var lines: [LyricLine] = []

        for (idx, text) in rawLines.enumerated() {
            let fraction = lineWeights[idx] / totalWeight
            let durationForLine = fraction * availableMs
            let rom = TransliterationEngine.shared.canRomanize(text) ? TransliterationEngine.shared.romanize(text) : nil
            lines.append(LyricLine(timestampMs: currentMs, text: text, romanizedText: rom))
            currentMs += durationForLine
        }

        return LyricsResult(
            lines: lines,
            plainLyrics: plain,
            isSynced: true,
            isEstimated: true,
            confidenceScore: 75,
            source: "Estimated Pacing"
        )
    }

    func fetchYouTubeMusicOfficialLyrics(videoId: String, durationSeconds: Int = 0) async -> LyricsResult? {
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
                let paced = calculateEstimatedSync(plain: trimmed, durationSeconds: durationSeconds)
                return LyricsResult(
                    lines: paced.lines,
                    plainLyrics: trimmed,
                    isSynced: true,
                    isEstimated: true,
                    confidenceScore: 80,
                    source: "YouTube Music"
                )
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
        return parseLrcResponse(json, source: "LRCLIB")
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

            if !artistMatches { continue }

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
            return parseLrcResponse(best, source: "LRCLIB")
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

    private func parseLrcResponse(_ json: [String: Any], source: String = "LRCLIB") -> LyricsResult? {
        let synced = (json["syncedLyrics"] as? String) ?? ""
        let plain = (json["plainLyrics"] as? String) ?? ""

        if !synced.isEmpty {
            let lines = parseLrcText(synced)
            if !lines.isEmpty {
                return LyricsResult(
                    lines: lines,
                    plainLyrics: plain,
                    isSynced: true,
                    isEstimated: false,
                    confidenceScore: 100,
                    source: source
                )
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
            return LyricsResult(
                lines: parsedLines,
                plainLyrics: plain,
                isSynced: false,
                isEstimated: false,
                confidenceScore: 70,
                source: "\(source) (Plain)"
            )
        }

        return nil
    }

    func parseLrcText(_ lrc: String) -> [LyricLine] {
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

                // Skip header tags
                if text.hasPrefix("[") && text.hasSuffix("]") { continue }

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
        if let regex = try? NSRegularExpression(pattern: "(?i)\\[.*?\\]|\\(.*?\\)") {
            res = regex.stringByReplacingMatches(in: res, range: NSRange(location: 0, length: (res as NSString).length), withTemplate: "")
        }
        if let regex = try? NSRegularExpression(pattern: "(?i)\\b(official music video|official video|music video|lyric video|visualizer|official audio|audio|video|4k|hd|remastered|lyrics|prod\\. by.*)\\b") {
            res = regex.stringByReplacingMatches(in: res, range: NSRange(location: 0, length: (res as NSString).length), withTemplate: "")
        }
        if let regex = try? NSRegularExpression(pattern: "(?i)\\b(ft\\.?|feat\\.?|featuring)\\s+[^|\\-]+") {
            res = regex.stringByReplacingMatches(in: res, range: NSRange(location: 0, length: (res as NSString).length), withTemplate: "")
        }
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
