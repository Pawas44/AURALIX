import Foundation
import Combine

struct SmartHomeFeed: Equatable {
    var madeForYou: [VideoItem] = []
    var recommendedForYou: [VideoItem] = []
    var discoverWeekly: [VideoItem] = []
    var dailyMixes: [DailyMix] = []
    var becauseYouListenedTo: (artist: String, tracks: [VideoItem])? = nil
    var yourFavouriteGenres: [(genre: String, tracks: [VideoItem])] = []
    var continueListening: [VideoItem] = []
    var yourMostPlayed: [VideoItem] = []
    var recentlyDiscovered: [VideoItem] = []
    var hiddenGems: [VideoItem] = []
    var trendingInFavouriteGenres: [VideoItem] = []
    var similarToPlaylist: [VideoItem] = []
    var moodRecommendations: [VideoItem] = []
    var isLoading: Bool = false
    
    static func == (lhs: SmartHomeFeed, rhs: SmartHomeFeed) -> Bool {
        return lhs.madeForYou == rhs.madeForYou &&
            lhs.recommendedForYou == rhs.recommendedForYou &&
            lhs.discoverWeekly == rhs.discoverWeekly &&
            lhs.dailyMixes == rhs.dailyMixes &&
            lhs.continueListening == rhs.continueListening &&
            lhs.yourMostPlayed == rhs.yourMostPlayed &&
            lhs.recentlyDiscovered == rhs.recentlyDiscovered &&
            lhs.hiddenGems == rhs.hiddenGems &&
            lhs.isLoading == rhs.isLoading
    }
}

@MainActor
final class RecommendationEngine: ObservableObject {
    static let shared = RecommendationEngine()
    
    @Published var feedState = SmartHomeFeed()
    
    private init() {}
    
    /**
     * Asynchronously re-generates all personalized smart home sections.
     */
    @discardableResult
    func refreshHomeFeed(selectedMood: String? = nil, forceRefresh: Bool = false) async -> SmartHomeFeed {
        feedState.isLoading = true
        
        if forceRefresh {
            RecommendationCache.shared.invalidate()
        }
        
        let profile = UserTasteProfile.shared
        let userHistory = UserDataRepository.shared.history
        let userPlaylists = UserDataRepository.shared.playlists
        let likedPlaylist = userPlaylists.first(where: { $0.id == "liked_videos" })?.videos ?? []
        let importedPlaylistTracks = userPlaylists.flatMap { $0.videos }
        
        // 1. Dynamic taste discovery from YouTube Music
        var dynamicDiscoveryTracks: [VideoItem] = []
        let topArtists = profile.getTopArtists(limit: 4).map { $0.artistName }.filter { !$0.trimmingCharacters(in: .whitespaces).isEmpty }
        let seedTracks = (likedPlaylist + userHistory).filter { $0.id.count == 11 }.prefix(3)
        
        // A. Algorithmic Song Radio for top tracks
        for st in seedTracks {
            if let radio = try? await YouTubeSearchEngine.shared.getSongRadio(videoId: st.id), !radio.isEmpty {
                dynamicDiscoveryTracks.append(contentsOf: radio)
            }
        }
        
        // B. Fetch fresh songs for user's favorite artists
        for artist in topArtists {
            let fresh = await YouTubeSyncEngine.shared.searchMusicVideosInnerTube(query: "\(artist) songs")
            if !fresh.isEmpty {
                dynamicDiscoveryTracks.append(contentsOf: fresh.prefix(8))
            }
        }
        
        // C. Fetch mood songs if selected
        if let mood = selectedMood, !mood.trimmingCharacters(in: .whitespaces).isEmpty {
            let moodTracks = await YouTubeSyncEngine.shared.searchMusicVideosInnerTube(query: "\(mood) music")
            if !moodTracks.isEmpty {
                dynamicDiscoveryTracks.append(contentsOf: moodTracks.prefix(12))
            }
        }
        
        // Candidate pool collection
        let syncFeed = YouTubeSyncEngine.shared.musicFeed
        var rawCandidatePool: [VideoItem] = []
        rawCandidatePool.append(contentsOf: dynamicDiscoveryTracks)
        rawCandidatePool.append(contentsOf: syncFeed.quickPicks)
        rawCandidatePool.append(contentsOf: syncFeed.listenAgain)
        rawCandidatePool.append(contentsOf: syncFeed.recommendedVideos)
        rawCandidatePool.append(contentsOf: syncFeed.trendingCharts)
        rawCandidatePool.append(contentsOf: importedPlaylistTracks)
        rawCandidatePool.append(contentsOf: userHistory)
        rawCandidatePool.append(contentsOf: likedPlaylist)
        rawCandidatePool.append(contentsOf: VideoRepository.sampleVideos)
        
        let candidatePool = SongDeduplicationEngine.shared.deduplicate(rawCandidatePool)
        
        var placedTrackIds = Set<String>()
        var placedCanonicalKeys = Set<String>()
        
        // 1. Made For You / Selected Mood Mix
        let scoredMadeForYou: [VideoItem]
        if let mood = selectedMood, !mood.trimmingCharacters(in: .whitespaces).isEmpty {
            let moodCandidates = candidatePool.filter {
                $0.mood.caseInsensitiveCompare(mood) == .orderedSame ||
                $0.category.caseInsensitiveCompare(mood) == .orderedSame
            }
            let pool = !moodCandidates.isEmpty ? moodCandidates : candidatePool
            let candidatesToUse = forceRefresh ? pool.shuffled() : pool
            scoredMadeForYou = SongDeduplicationEngine.shared.deduplicateAndFill(
                primaryTracks: candidatesToUse.filter { !placedTrackIds.contains($0.id) },
                candidatePool: candidatePool,
                targetCount: 16,
                excludedKeys: placedCanonicalKeys
            )
        } else {
            let pool = forceRefresh ? candidatePool.shuffled() : candidatePool
            let ranked = RecommendationRanker.shared.rank(
                candidates: pool,
                recentHistory: Array(userHistory.prefix(15)),
                likedTracks: likedPlaylist,
                discoveryFactor: forceRefresh ? 0.55 : 0.40,
                maxPerArtist: 2
            ).map { $0.track }.filter { !placedTrackIds.contains($0.id) }
            
            scoredMadeForYou = SongDeduplicationEngine.shared.deduplicateAndFill(
                primaryTracks: ranked,
                candidatePool: candidatePool,
                targetCount: 16,
                excludedKeys: placedCanonicalKeys
            )
        }
        
        for t in scoredMadeForYou {
            placedTrackIds.insert(t.id)
            placedCanonicalKeys.insert(SongDeduplicationEngine.shared.getCanonicalIdentity(t).canonicalKey)
        }
        
        // 2. Discover Weekly (~30 tracks, ~40% familiar / ~60% discovery, 7-day cache)
        let discoverWeeklyTracks = DiscoverWeeklyGenerator.shared.getOrGenerateDiscoverWeekly(
            candidatePool: candidatePool,
            forceRefresh: forceRefresh
        )
        
        // 3. Daily Mixes (Mix 1 to Mix 5)
        let dailyMixes = DailyMixGenerator.shared.generateDailyMixes(candidatePool: candidatePool)
        
        // 4. Because You Listened To...
        let seedTrack = userHistory.first(where: { $0.isMusicTrack }) ?? userHistory.first
        var becauseYouListenedPair: (artist: String, tracks: [VideoItem])? = nil
        if let st = seedTrack {
            let seedArtist = profile.extractArtist(st).isEmpty ? st.title : profile.extractArtist(st)
            let similar = candidatePool.filter { track in
                track.id != st.id &&
                !placedTrackIds.contains(track.id) &&
                SongSimilarityEngine.shared.computeSimilarity(st, track) > 0.40
            }.prefix(10)
            if !similar.isEmpty {
                let list = Array(similar)
                becauseYouListenedPair = (artist: seedArtist, tracks: list)
                for t in list { placedTrackIds.insert(t.id) }
            }
        }
        
        // 5. Your Favourite Genres
        let topGenres = profile.getTopGenres(limit: 3)
        var genreSections: [(genre: String, tracks: [VideoItem])] = []
        for ga in topGenres {
            let genreTracks = Array(candidatePool.filter { track in
                !placedTrackIds.contains(track.id) &&
                profile.extractGenre(track).localizedCaseInsensitiveContains(ga.genre)
            }.prefix(8))
            if !genreTracks.isEmpty {
                genreSections.append((genre: ga.genre, tracks: genreTracks))
                for t in genreTracks { placedTrackIds.insert(t.id) }
            }
        }
        
        // 6. Continue Listening (Recently played tracks)
        let continueListening = Array(userHistory.filter { !placedTrackIds.contains($0.id) }.prefix(10))
        for t in continueListening { placedTrackIds.insert(t.id) }
        
        // 7. Your Most Played
        var counts: [String: Int] = [:]
        for h in userHistory { counts[h.id, default: 0] += 1 }
        let sortedIds = counts.sorted { $0.value > $1.value }.map { $0.key }
        var mostPlayed: [VideoItem] = []
        for sid in sortedIds {
            if let track = userHistory.first(where: { $0.id == sid }), !placedTrackIds.contains(track.id) {
                mostPlayed.append(track)
                if mostPlayed.count >= 10 { break }
            }
        }
        for t in mostPlayed { placedTrackIds.insert(t.id) }
        
        // 8. Recently Discovered (New artists)
        let familiarArtists = Set(profile.getTopArtists(limit: 5).map { $0.artistName.lowercased() })
        let recentlyDiscovered = Array(candidatePool.filter { track in
            let art = profile.extractArtist(track).lowercased()
            return !art.isEmpty && !familiarArtists.contains(art) && !placedTrackIds.contains(track.id)
        }.prefix(10))
        for t in recentlyDiscovered { placedTrackIds.insert(t.id) }
        
        // 9. Hidden Gems
        let hiddenGems = Array(candidatePool.filter { track in
            !placedTrackIds.contains(track.id) &&
            !profile.isTrackHidden(track.id) &&
            (track.views.localizedCaseInsensitiveContains("K") || !track.views.localizedCaseInsensitiveContains("M"))
        }.shuffled().prefix(10))
        for t in hiddenGems { placedTrackIds.insert(t.id) }
        
        // 10. Trending In Your Favourite Genres
        let primaryGenre = topGenres.first?.genre ?? "Pop"
        let trendingInGenre = Array(candidatePool.filter { track in
            !placedTrackIds.contains(track.id) &&
            profile.extractGenre(track).localizedCaseInsensitiveContains(primaryGenre)
        }.prefix(10))
        for t in trendingInGenre { placedTrackIds.insert(t.id) }
        
        // 11. Similar To Your Playlist
        let playlistSeed = likedPlaylist.first
        var similarToPlaylist: [VideoItem] = []
        if let plSeed = playlistSeed {
            similarToPlaylist = Array(candidatePool.filter { track in
                !placedTrackIds.contains(track.id) &&
                track.id != plSeed.id &&
                SongSimilarityEngine.shared.computeSimilarity(plSeed, track) > 0.35
            }.prefix(10))
            for t in similarToPlaylist { placedTrackIds.insert(t.id) }
        }
        
        // 12. Mood-based recommendations
        let targetMood = (selectedMood?.isEmpty == false ? selectedMood : nil) ?? "Energize"
        let rawMoodTracks = candidatePool.filter { track in
            !placedTrackIds.contains(track.id) &&
            (track.mood.caseInsensitiveCompare(targetMood) == .orderedSame ||
             track.category.caseInsensitiveCompare(targetMood) == .orderedSame)
        }
        let moodTracks = Array((forceRefresh ? rawMoodTracks.shuffled() : rawMoodTracks).prefix(10))
        
        // Recommended for you (Fall-through general ranking)
        let remaining = candidatePool.filter { !placedTrackIds.contains($0.id) }
        let poolToRank = forceRefresh ? remaining.shuffled() : remaining
        let recommendedForYou = RecommendationRanker.shared.rank(
            candidates: poolToRank,
            recentHistory: userHistory,
            likedTracks: likedPlaylist,
            discoveryFactor: forceRefresh ? 0.45 : 0.30,
            maxPerArtist: 2
        ).map { $0.track }.prefix(12)
        
        let cleanMadeForYou = SongDeduplicationEngine.shared.ensureNoConsecutiveDuplicates(
            !scoredMadeForYou.isEmpty ? scoredMadeForYou :
            SongDeduplicationEngine.shared.deduplicateAndFill(
                primaryTracks: candidatePool,
                candidatePool: candidatePool,
                targetCount: 10,
                excludedKeys: placedCanonicalKeys
            )
        )
        
        let cleanRecommended = SongDeduplicationEngine.shared.ensureNoConsecutiveDuplicates(
            !recommendedForYou.isEmpty ? Array(recommendedForYou) :
            SongDeduplicationEngine.shared.deduplicateAndFill(
                primaryTracks: candidatePool.shuffled(),
                candidatePool: candidatePool,
                targetCount: 10,
                excludedKeys: placedCanonicalKeys
            )
        )
        
        let finalFeed = SmartHomeFeed(
            madeForYou: cleanMadeForYou,
            recommendedForYou: cleanRecommended,
            discoverWeekly: discoverWeeklyTracks,
            dailyMixes: dailyMixes,
            becauseYouListenedTo: becauseYouListenedPair,
            yourFavouriteGenres: genreSections,
            continueListening: continueListening,
            yourMostPlayed: mostPlayed,
            recentlyDiscovered: recentlyDiscovered,
            hiddenGems: hiddenGems,
            trendingInFavouriteGenres: trendingInGenre,
            similarToPlaylist: similarToPlaylist,
            moodRecommendations: moodTracks,
            isLoading: false
        )
        
        self.feedState = finalFeed
        return finalFeed
    }
}
