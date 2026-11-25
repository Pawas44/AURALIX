import Foundation

struct SongIdentity {
    let normalizedTitle: String
    let normalizedArtist: String
    let versionType: SongVersionType
    let canonicalKey: String
}

struct SongDeduplicationEngine {

    static func getCanonicalIdentity(track: VideoItem) -> SongIdentity {
        var spotifyId = ""
        if track.id.hasPrefix("spotify_") {
            spotifyId = String(track.id.dropFirst("spotify_".count))
        } else if track.id.hasPrefix("spotify:") {
            spotifyId = String(track.id.dropFirst("spotify:".count))
        } else if !track.spotifyTrackId.isEmpty {
            spotifyId = track.spotifyTrackId
        }

        let rawTitle = track.title.trimmingCharacters(in: .whitespacesAndNewlines)
        let version = SongVersionClassifier.classify(title: rawTitle, channelName: track.channelName)
        let versionKey: String
        switch version {
        case .remixMashup: versionKey = "remix"
        case .livePerformance: versionKey = "live"
        case .acousticVersion: versionKey = "acoustic"
        case .slowedReverb: versionKey = "slowed"
        case .instrumentalKaraoke: versionKey = "instrumental"
        case .coverVersion: versionKey = "cover"
        default: versionKey = "original"
        }

        let emojiCleaned = SongTitleNormalizer.removeEmojis(rawTitle)
        let fluffRemoved = SongTitleNormalizer.removeVideoFluff(emojiCleaned)
        let unbracketed = fluffRemoved.replacingOccurrences(of: "(?i)\\[.*?\\]|\\(.*?\\)", with: " ", options: .regularExpression)
        let cleanPunct = SongTitleNormalizer.cleanPunctuation(unbracketed)
        let normTitle = SongTitleNormalizer.normalizeRomanizedPhonetics(cleanPunct)
            .lowercased()
            .replacingOccurrences(of: "\\s+", with: " ", options: .regularExpression)
            .trimmingCharacters(in: .whitespacesAndNewlines)

        let rawArtist = track.artist.isEmpty ? track.channelName : track.artist
        let extracted = SongMetadataExtractor.extract(
            title: rawTitle,
            rawArtist: rawArtist,
            channelName: track.channelName
        )
        let effectiveArtist = extracted.primaryArtist.isEmpty ? rawArtist : extracted.primaryArtist
        let cleanArtist = SongMetadataExtractor.cleanArtistString(effectiveArtist)
        let normArtist = SongTitleNormalizer.normalizeRomanizedPhonetics(
            SongTitleNormalizer.cleanPunctuation(cleanArtist)
        ).lowercased()
        .replacingOccurrences(of: "\\s+", with: " ", options: .regularExpression)
        .trimmingCharacters(in: .whitespacesAndNewlines)

        let canonicalKey = !spotifyId.isEmpty
            ? "spotify:\(spotifyId)::\(versionKey)"
            : "\(normTitle):::\(normArtist)::\(versionKey)"

        return SongIdentity(
            normalizedTitle: normTitle,
            normalizedArtist: normArtist,
            versionType: version,
            canonicalKey: canonicalKey
        )
    }

    static func areDuplicateSongs(_ a: VideoItem, _ b: VideoItem) -> Bool {
        if a.id == b.id && !a.id.isEmpty { return true }
        let idA = getCanonicalIdentity(track: a)
        let idB = getCanonicalIdentity(track: b)
        return idA.canonicalKey == idB.canonicalKey
    }

    static func deduplicate(_ tracks: [VideoItem]) -> [VideoItem] {
        var seenKeys = Set<String>()
        var seenVideoIds = Set<String>()
        var result: [VideoItem] = []

        for track in tracks {
            if track.id.isEmpty || seenVideoIds.contains(track.id) { continue }
            let identity = getCanonicalIdentity(track: track)
            if seenKeys.contains(identity.canonicalKey) { continue }

            seenVideoIds.insert(track.id)
            seenKeys.insert(identity.canonicalKey)
            result.append(track)
        }
        return result
    }

    static func deduplicateAndFill(
        primaryTracks: [VideoItem],
        candidatePool: [VideoItem],
        targetCount: Int,
        excludedKeys: Set<String> = []
    ) -> [VideoItem] {
        var seenKeys = excludedKeys
        var seenVideoIds = Set<String>()
        var result: [VideoItem] = []

        for track in primaryTracks {
            if result.count >= targetCount { break }
            if track.id.isEmpty || seenVideoIds.contains(track.id) { continue }
            let identity = getCanonicalIdentity(track: track)
            if seenKeys.contains(identity.canonicalKey) { continue }

            seenVideoIds.insert(track.id)
            seenKeys.insert(identity.canonicalKey)
            result.append(track)
        }

        if result.count < targetCount {
            for candidate in candidatePool {
                if result.count >= targetCount { break }
                if candidate.id.isEmpty || seenVideoIds.contains(candidate.id) { continue }
                let identity = getCanonicalIdentity(track: candidate)
                if seenKeys.contains(identity.canonicalKey) { continue }

                seenVideoIds.insert(candidate.id)
                seenKeys.insert(identity.canonicalKey)
                result.append(candidate)
            }
        }

        return ensureNoConsecutiveDuplicates(result)
    }

    static func ensureNoConsecutiveDuplicates(_ tracks: [VideoItem]) -> [VideoItem] {
        guard tracks.count > 2 else { return tracks }
        var list = tracks

        for i in 0..<(list.count - 1) {
            let curr = getCanonicalIdentity(track: list[i])
            let next = getCanonicalIdentity(track: list[i + 1])

            if curr.normalizedTitle == next.normalizedTitle ||
               (!curr.normalizedArtist.isEmpty && curr.normalizedArtist == next.normalizedArtist) {
                var swapIdx = -1
                for j in (i + 2)..<list.count {
                    let candidate = getCanonicalIdentity(track: list[j])
                    if candidate.normalizedTitle != curr.normalizedTitle &&
                       candidate.normalizedArtist != curr.normalizedArtist {
                        swapIdx = j
                        break
                    }
                }
                if swapIdx != -1 {
                    list.swapAt(i + 1, swapIdx)
                }
            }
        }
        return list
    }
}
