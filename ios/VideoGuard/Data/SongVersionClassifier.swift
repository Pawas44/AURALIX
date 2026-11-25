import Foundation

enum SongVersionType: String, Codable {
    case officialAudio = "OFFICIAL_AUDIO"
    case officialMusicVideo = "OFFICIAL_MUSIC_VIDEO"
    case labelUpload = "LABEL_UPLOAD"
    case standardUpload = "STANDARD_UPLOAD"
    case lyricsVideo = "LYRICS_VIDEO"
    case livePerformance = "LIVE_PERFORMANCE"
    case acousticVersion = "ACOUSTIC_VERSION"
    case coverVersion = "COVER_VERSION"
    case remixMashup = "REMIX_MASHUP"
    case slowedReverb = "SLOWED_REVERB"
    case instrumentalKaraoke = "INSTRUMENTAL_KARAOKE"
    case disqualified = "DISQUALIFIED"
}

struct SongVersionClassifier {

    private static let disqualifiedPattern = "(?i)\\b(reaction|reacting|interview|podcast|review|vlog|behind the scenes|making of|teaser|trailer|promo|glimpse)\\b"
    private static let livePattern = "(?i)\\b(live|concert|tour|live performance|live from|unplugged|stage show|performing live|on stage|in concert)\\b"
    private static let coverPattern = "(?i)\\b(cover|tribute|rendition|covered by|cover song)\\b"
    private static let remixPattern = "(?i)\\b(remix|club mix|mashup|dj remix|dance mix|extended mix|reprise|electronic mix)\\b"
    private static let slowedReverbPattern = "(?i)\\b(slowed|reverb|slowed\\s*[+&]\\s*reverb|lo-?fi|sped up|speed up|nightcore)\\b"
    private static let instrumentalPattern = "(?i)\\b(instrumental|karaoke|backing track|minus one)\\b"
    private static let officialAudioPattern = "(?i)\\b(official audio|original audio|full audio|studio audio|studio version|title track)\\b"
    private static let officialVideoPattern = "(?i)\\b(official video|official music video|music video)\\b"
    private static let lyricsPattern = "(?i)\\b(lyrics|lyric video|lyrical video|with lyrics|song lyrics)\\b"
    private static let acousticPattern = "(?i)\\b(acoustic|stripped|acoustic version)\\b"

    static func classify(title: String, channelName: String = "") -> SongVersionType {
        let combined = "\(title) \(channelName)".lowercased()

        if matches(pattern: disqualifiedPattern, in: combined) {
            return .disqualified
        }
        if matches(pattern: livePattern, in: combined) {
            return .livePerformance
        }
        if matches(pattern: coverPattern, in: combined) {
            return .coverVersion
        }
        if matches(pattern: remixPattern, in: combined) {
            return .remixMashup
        }
        if matches(pattern: slowedReverbPattern, in: combined) {
            return .slowedReverb
        }
        if matches(pattern: instrumentalPattern, in: combined) {
            return .instrumentalKaraoke
        }
        if matches(pattern: officialAudioPattern, in: combined) {
            return .officialAudio
        }
        if matches(pattern: officialVideoPattern, in: combined) {
            return .officialMusicVideo
        }
        if matches(pattern: lyricsPattern, in: combined) {
            return .lyricsVideo
        }
        if matches(pattern: acousticPattern, in: combined) {
            return .acousticVersion
        }
        return .standardUpload
    }

    private static func matches(pattern: String, in text: String) -> Bool {
        guard let regex = try? NSRegularExpression(pattern: pattern) else { return false }
        let range = NSRange(text.startIndex..., in: text)
        return regex.firstMatch(in: text, options: [], range: range) != nil
    }
}
