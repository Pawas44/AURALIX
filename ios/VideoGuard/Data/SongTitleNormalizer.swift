import Foundation

struct SongTitleNormalizer {

    private static let videoFluffPatterns: [String] = [
        "(?i)\\b(official music video|official video|music video|lyric video|lyrical video|visualizer|audio video)\\b",
        "(?i)\\b(official audio|original audio|full audio|studio audio)\\b",
        "(?i)\\b(full song|full video|complete song|title track|full track)\\b",
        "(?i)\\b(4k ultra hd|4k uhd|4k video|4k|1080p|720p|hd video|hd|uhd)\\b",
        "(?i)\\b(remastered|remaster|digitally remastered|dolby atmos|spatial audio)\\b",
        "(?i)\\b(lyrics|with lyrics|lyric|lyrics video|song lyrics)\\b",
        "(?i)\\b(best bollywood songs?|romantic songs?|love songs?|trending songs?|hit songs?|sad songs?)\\b",
        "(?i)\\b(best songs?|latest songs?|new songs?|top hits?|playlist|jukebox)\\b",
        "(?i)\\b(prod(?:uced)?\\.?\\s+by\\s+[^|\\-]+)",
        "(?i)\\b(directed by\\s+[^|\\-]+)",
        "(?i)\\b(choreography by\\s+[^|\\-]+)",
        "(?i)\\b(teaser|trailer|promo|glimpse|sneak peek)\\b",
        "(?i)\\b(status|whatsapp status|fullscreen status|shorts|reels)\\b",
        "(?i)#[a-zA-Z0-9_]+",
        "(?i)\\b(20[12][0-9]|19[89][0-9])\\b"
    ]

    static func removeEmojis(_ text: String) -> String {
        return text.unicodeScalars
            .filter { !$0.properties.isEmojiPresentation && !$0.properties.isEmoji }
            .map { String($0) }
            .joined()
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }

    static func removeVideoFluff(_ text: String) -> String {
        var clean = text
        for pattern in videoFluffPatterns {
            if let regex = try? NSRegularExpression(pattern: pattern) {
                let range = NSRange(clean.startIndex..., in: clean)
                clean = regex.stringByReplacingMatches(in: clean, options: [], range: range, withTemplate: " ")
            }
        }
        return clean.replacingOccurrences(of: "\\s+", with: " ", options: .regularExpression).trimmingCharacters(in: .whitespacesAndNewlines)
    }

    static func cleanPunctuation(_ text: String) -> String {
        let pattern = "[^a-zA-Z0-9\\s\\u0900-\\u097F\\u0A00-\\u0A7F\\u0600-\\u06FF]"
        if let regex = try? NSRegularExpression(pattern: pattern) {
            let range = NSRange(text.startIndex..., in: text)
            let cleaned = regex.stringByReplacingMatches(in: text, options: [], range: range, withTemplate: " ")
            return cleaned.replacingOccurrences(of: "\\s+", with: " ", options: .regularExpression).trimmingCharacters(in: .whitespacesAndNewlines)
        }
        return text
    }

    static func normalizeRomanizedPhonetics(_ text: String) -> String {
        var s = text.lowercased()
        s = s.replacingOccurrences(of: "aa+", with: "a", options: .regularExpression)
        s = s.replacingOccurrences(of: "ee+", with: "i", options: .regularExpression)
        s = s.replacingOccurrences(of: "oo+", with: "u", options: .regularExpression)
        s = s.replacingOccurrences(of: "kh", with: "k")
        s = s.replacingOccurrences(of: "gh", with: "g")
        s = s.replacingOccurrences(of: "ph", with: "f")
        s = s.replacingOccurrences(of: "th", with: "t")
        s = s.replacingOccurrences(of: "bh", with: "b")
        s = s.replacingOccurrences(of: "dh", with: "d")
        s = s.replacingOccurrences(of: "sh", with: "s")
        s = s.replacingOccurrences(of: "z", with: "j")
        s = s.replacingOccurrences(of: "v", with: "w")
        return s.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    static func normalizeForSearch(_ text: String) -> String {
        let withoutEmoji = removeEmojis(text)
        let withoutFluff = removeVideoFluff(withoutEmoji)
        var clean = withoutFluff
            .replacingOccurrences(of: "(?i)\\[.*?\\]|\\(.*?\\)", with: " ", options: .regularExpression)
            .replacingOccurrences(of: "(?i)\\b(ft\\.?|feat\\.?|featuring)\\s+[^|\\-]+", with: " ", options: .regularExpression)
            .replacingOccurrences(of: "[|/•&\\-_]+", with: " ", options: .regularExpression)
            .replacingOccurrences(of: "\\s+", with: " ", options: .regularExpression)
            .trimmingCharacters(in: .whitespacesAndNewlines)

        if clean.hasPrefix("\"") && clean.hasSuffix("\"") && clean.count > 1 {
            clean = String(clean.dropFirst().dropLast())
        }
        return clean.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    static func calculateSimilarity(_ a: String, _ b: String) -> Double {
        let sA = cleanPunctuation(removeEmojis(a)).lowercased().trimmingCharacters(in: .whitespacesAndNewlines)
        let sB = cleanPunctuation(removeEmojis(b)).lowercased().trimmingCharacters(in: .whitespacesAndNewlines)
        if sA == sB { return 1.0 }
        if sA.isEmpty || sB.isEmpty { return 0.0 }
        if sA.contains(sB) || sB.contains(sA) { return 0.85 }

        let tokensA = Set(sA.split(separator: " ").map(String.init))
        let tokensB = Set(sB.split(separator: " ").map(String.init))
        let intersection = tokensA.intersection(tokensB).count
        let union = tokensA.union(tokensB).count
        return union > 0 ? Double(intersection) / Double(union) : 0.0
    }
}
