import Foundation

struct ExtractedSongMetadata {
    let songTitle: String
    let primaryArtist: String
    let featuredArtists: [String]
    let albumOrMovie: String
    let version: String
    let isArtistConfident: Bool
}

struct SongMetadataExtractor {

    private static let recordLabelsAndChannels: Set<String> = [
        "t-series", "tseries", "t series", "t-series regional", "t-series bhakti",
        "zee music company", "zee music", "zee cine", "sony music india", "sony music",
        "tips official", "tips music", "tips", "speed records", "white hill music",
        "saregama", "saregama music", "yrf", "yash raj films", "eros now", "eros music",
        "desi melodies", "desi music factory", "geet mp3", "nav haryanvi", "gringo entertainments",
        "single track studios", "jass records", "saga hits", "times music", "bhojpuri wave",
        "goldmines telefilms", "goldmines", "venus", "ultra bollywood", "ultra music", "pen music",
        "dharma productions", "universal music india", "universal music", "warner music india",
        "warner music", "vevo", "topic", "official channel", "records", "music company",
        "aditya music", "lahari music", "think music india", "think music", "anand audio",
        "muzik247", "svf music", "svf", "red chillies entertainment", "shemaroo",
        "wave music", "t-series apna punjab", "speed punjabi", "dm - desi melodies",
        "lofi vibes", "chill beats", "vibe music", "romantic playlist", "best bollywood songs",
        "bollywood classics", "melody records", "audio junction", "bass boosted", "trap nation",
        "music hub", "top hits", "soundtrack records", "all bollywood", "lofi songs",
        "lofi music", "midnight vibes", "soulful melodies", "bollywood songs", "hindi songs"
    ]

    static func isLabelOrChannel(_ name: String) -> Bool {
        let lower = name.lowercased().trimmingCharacters(in: .whitespacesAndNewlines)
        if lower.isEmpty || lower.count <= 1 { return true }
        return recordLabelsAndChannels.contains { lower.contains($0) || $0.contains(lower) }
    }

    static func cleanArtistString(_ raw: String) -> String {
        var s = raw
            .replacingOccurrences(of: "(?i)\\s*-\\s*topic$", with: "", options: .regularExpression)
            .replacingOccurrences(of: "(?i)\\s*vevo$", with: "", options: .regularExpression)
            .replacingOccurrences(of: "(?i)\\s*official$", with: "", options: .regularExpression)
            .replacingOccurrences(of: "(?i)\\[.*?\\]|\\(.*?\\)", with: "", options: .regularExpression)
            .trimmingCharacters(in: .whitespacesAndNewlines)

        if s.hasPrefix("\"") && s.hasSuffix("\"") && s.count > 1 {
            s = String(s.dropFirst().dropLast())
        }
        if s.hasPrefix("'") && s.hasSuffix("'") && s.count > 1 {
            s = String(s.dropFirst().dropLast())
        }
        return s.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    static func splitArtists(_ raw: String) -> [String] {
        let cleaned = cleanArtistString(raw)
        let pattern = "(?i)[,&/|•]|\\band\\b|\\bfeat\\.?\\b|\\bft\\.?\\b|\\bwith\\b|\\bx\\b"
        guard let regex = try? NSRegularExpression(pattern: pattern) else { return [cleaned] }
        let range = NSRange(cleaned.startIndex..., in: cleaned)
        let parts = regex.stringByReplacingMatches(in: cleaned, options: [], range: range, withTemplate: ":::").components(separatedBy: ":::")

        return parts
            .map { cleanArtistString($0) }
            .filter { !$0.isEmpty && !isLabelOrChannel($0) && $0.count >= 2 && $0.count <= 50 }
    }

    static func extract(
        title: String,
        rawArtist: String = "",
        description: String = "",
        album: String = "",
        channelName: String = ""
    ) -> ExtractedSongMetadata {
        let cleanTitle = SongTitleNormalizer.removeVideoFluff(SongTitleNormalizer.removeEmojis(title))
        var songTitle = cleanTitle
        var primaryArtist = cleanArtistString(rawArtist)

        if title.contains(" - ") {
            let split = title.components(separatedBy: " - ")
            if split.count >= 2 {
                let firstPart = cleanArtistString(split[0])
                let secondPart = SongTitleNormalizer.removeVideoFluff(SongTitleNormalizer.removeEmojis(split[1]))

                if !isLabelOrChannel(firstPart) && primaryArtist.isEmpty {
                    primaryArtist = firstPart
                    songTitle = secondPart
                } else if isLabelOrChannel(firstPart) {
                    songTitle = secondPart
                }
            }
        }

        if primaryArtist.isEmpty && !channelName.isEmpty && !isLabelOrChannel(channelName) {
            primaryArtist = cleanArtistString(channelName)
        }

        let featured = splitArtists(rawArtist).filter { $0.lowercased() != primaryArtist.lowercased() }

        return ExtractedSongMetadata(
            songTitle: songTitle.trimmingCharacters(in: .whitespacesAndNewlines),
            primaryArtist: primaryArtist.trimmingCharacters(in: .whitespacesAndNewlines),
            featuredArtists: featured,
            albumOrMovie: album,
            version: "",
            isArtistConfident: !primaryArtist.isEmpty && !isLabelOrChannel(primaryArtist)
        )
    }
}
