import Foundation

struct ScoredTrack {
    let track: VideoItem
    let score: Double
    let primaryReason: String
}

struct RecommendationRanker {
    static let shared = RecommendationRanker()

    func rank(
        candidates: [VideoItem],
        recentHistory: [VideoItem],
        likedTracks: [VideoItem],
        discoveryFactor: Double = 0.25,
        maxPerArtist: Int = 2
    ) -> [ScoredTrack] {
        Self.rank(
            candidates: candidates,
            recentHistory: recentHistory,
            likedTracks: likedTracks,
            discoveryFactor: discoveryFactor,
            maxPerArtist: maxPerArtist
        )
    }

    static func rank(
        candidates: [VideoItem],
        recentHistory: [VideoItem],
        likedTracks: [VideoItem],
        discoveryFactor: Double = 0.25,
        maxPerArtist: Int = 2
    ) -> [ScoredTrack] {
        let profile = UserTasteProfile.shared
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        let oneDayMs: Int64 = 24 * 60 * 60 * 1000

        var weightedUserTracks: [(VideoItem, Double)] = []
        for track in likedTracks {
            weightedUserTracks.append((track, 3.0))
        }
        for (idx, track) in recentHistory.prefix(20).enumerated() {
            let w = 2.0 * (1.0 - (Double(idx) * 0.04))
            weightedUserTracks.append((track, max(0.5, w)))
        }
        let userCentroidVector = SongSimilarityEngine.computeProfileCentroid(weightedUserTracks)
        let recentIds = Set(recentHistory.map { $0.id })

        var scoredList: [ScoredTrack] = []

        for candidate in candidates {
            let artist = profile.extractArtist(candidate)
            let genre = profile.extractGenre(candidate)
            if profile.isTrackHidden(candidate.id) || profile.isArtistHidden(artist) {
                continue
            }

            let artistScore = profile.getArtistAffinity(artist)
            let genreScore = profile.getGenreAffinity(genre)
            let normArtistScore = min(1.0, max(0.0, artistScore / 25.0))
            let normGenreScore = min(1.0, max(0.0, genreScore / 20.0))
            let tasteAffinity = (normArtistScore * 0.60) + (normGenreScore * 0.40)

            let candidateVec = SongSimilarityEngine.extractFeatureVector(candidate)
            let centroidCosineSim = SongSimilarityEngine.cosineSimilarity(candidateVec, userCentroidVector)
            let similarityToLiked = SongSimilarityEngine.maxSimilarityAgainstCollection(candidate, likedTracks)
            let similarityToHistory = SongSimilarityEngine.maxSimilarityAgainstCollection(candidate, recentHistory)
            let contentSimilarity = (centroidCosineSim * 0.50) + (similarityToLiked * 0.30) + (similarityToHistory * 0.20)

            let isUnfamiliarArtist = !profile.discoveredArtists.contains(artist) && artistScore < 2.0
            let discoveryBonus = (isUnfamiliarArtist && (genreScore > 1.0 || centroidCosineSim > 0.65)) ? (0.35 * discoveryFactor) : 0.0

            let trackAffinity = profile.trackAffinities[candidate.id]
            let lastPlayedMs = trackAffinity?.lastPlayedMs ?? 0
            let repetitionMultiplier = (recentIds.contains(candidate.id) || (now - lastPlayedMs < oneDayMs)) ? 0.35 : 1.0

            let rawScore = ((tasteAffinity * 0.35) + (contentSimilarity * 0.45) + discoveryBonus) * repetitionMultiplier

            let reason: String
            if artistScore > 8.0 {
                reason = "Because you listen to \(artist) frequently"
            } else if likedTracks.contains(where: { $0.id == candidate.id }) {
                reason = "Because you liked this track"
            } else if centroidCosineSim > 0.80 {
                reason = "Acoustically matches your favorite music"
            } else if isUnfamiliarArtist && genreScore > 3.0 {
                reason = "New discovery in \(genre)"
            } else {
                reason = "Recommended for you"
            }

            scoredList.append(ScoredTrack(track: candidate, score: rawScore, primaryReason: reason))
        }

        let sorted = scoredList.sorted { $0.score > $1.score }

        // Diversity & Deduplication Controls
        var seenCanonicalKeys = Set<String>()
        var seenVideoIds = Set<String>()
        var artistCounts: [String: Int] = [:]
        var diverseList: [ScoredTrack] = []

        for item in sorted {
            let track = item.track
            if track.id.isEmpty || seenVideoIds.contains(track.id) { continue }
            let identity = SongDeduplicationEngine.getCanonicalIdentity(track: track)
            if seenCanonicalKeys.contains(identity.canonicalKey) { continue }

            let art = !identity.normalizedArtist.isEmpty ? identity.normalizedArtist : profile.extractArtist(track).lowercased()
            let count = artistCounts[art, default: 0]
            if count < maxPerArtist {
                diverseList.append(item)
                seenVideoIds.insert(track.id)
                seenCanonicalKeys.insert(identity.canonicalKey)
                artistCounts[art] = count + 1
            }
        }

        return diverseList
    }
}
