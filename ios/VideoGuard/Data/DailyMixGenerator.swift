import Foundation

struct DailyMix: Identifiable, Equatable {
    let id: String
    let title: String
    let subtitle: String
    let description: String
    let tracks: [VideoItem]
    let coverUrl: String?
    
    init(
        id: String,
        title: String,
        subtitle: String,
        description: String,
        tracks: [VideoItem],
        coverUrl: String? = nil
    ) {
        self.id = id
        self.title = title
        self.subtitle = subtitle
        self.description = description
        self.tracks = tracks
        self.coverUrl = coverUrl
    }
}

final class DailyMixGenerator {
    static let shared = DailyMixGenerator()
    
    private init() {}
    
    /**
     * Generates up to 5 personalized Daily Mixes based on the user's actual listening history.
     */
    func generateDailyMixes(candidatePool: [VideoItem]) -> [DailyMix] {
        let validPool = candidatePool.filter {
            ($0.id.count == 11 || $0.id.hasPrefix("spotify_")) && !$0.id.hasPrefix("search_")
        }
        let profile = UserTasteProfile.shared
        let topArtists = profile.getTopArtists(limit: 8).map { $0.artistName }
        let topGenres = profile.getTopGenres(limit: 4).map { $0.genre }
        
        var mixes: [DailyMix] = []
        
        // MIX 1: Heavy Rotation / Favorite Artists
        let mix1Artists = Array(topArtists.prefix(4))
        let mix1Tracks = validPool.filter { track in
            let art = profile.extractArtist(track)
            return mix1Artists.contains { $0.caseInsensitiveCompare(art) == .orderedSame }
        }.reduce(into: [VideoItem]()) { res, item in
            if !res.contains(where: { $0.id == item.id }) { res.append(item) }
        }
        let finalMix1Tracks = Array(mix1Tracks.prefix(20))
        
        if !finalMix1Tracks.isEmpty || !topArtists.isEmpty {
            let artistSummary = !mix1Artists.isEmpty ? mix1Artists.joined(separator: ", ") : "Your favorites"
            let trackList: [VideoItem]
            if finalMix1Tracks.count >= 8 {
                trackList = finalMix1Tracks
            } else {
                var combined = finalMix1Tracks
                for item in validPool.prefix(15) {
                    if !combined.contains(where: { $0.id == item.id }) {
                        combined.append(item)
                    }
                }
                trackList = Array(combined.prefix(20))
            }
            mixes.append(
                DailyMix(
                    id: "daily_mix_1",
                    title: "Daily Mix 1",
                    subtitle: artistSummary,
                    description: "Songs from \(artistSummary) and similar artists you love.",
                    tracks: trackList,
                    coverUrl: trackList.first?.thumbnailUrl
                )
            )
        }
        
        // MIX 2: Top Genre #1
        let genre1 = topGenres.first ?? "Pop"
        let mix2Filtered = validPool.filter {
            profile.extractGenre($0).localizedCaseInsensitiveContains(genre1)
        }.reduce(into: [VideoItem]()) { res, item in
            if !res.contains(where: { $0.id == item.id }) { res.append(item) }
        }
        let mix2Tracks = Array(mix2Filtered.prefix(20))
        
        if !mix2Tracks.isEmpty {
            let keyArtists = Array(mix2Tracks.map { profile.extractArtist($0) }
                .filter { !$0.trimmingCharacters(in: .whitespaces).isEmpty }
                .reduce(into: [String]()) { res, a in if !res.contains(a) { res.append(a) } }
                .prefix(3)).joined(separator: ", ")
            
            mixes.append(
                DailyMix(
                    id: "daily_mix_2",
                    title: "Daily Mix 2",
                    subtitle: "\(genre1) Mix",
                    description: "The best of \(genre1) featuring \(keyArtists).",
                    tracks: mix2Tracks,
                    coverUrl: mix2Tracks.first?.thumbnailUrl
                )
            )
        }
        
        // MIX 3: Top Genre #2
        let genre2 = topGenres.count > 1 ? topGenres[1] : "Electronic / Dance"
        let mix3Filtered = validPool.filter {
            profile.extractGenre($0).localizedCaseInsensitiveContains(genre2)
        }.reduce(into: [VideoItem]()) { res, item in
            if !res.contains(where: { $0.id == item.id }) { res.append(item) }
        }
        let mix3Tracks = Array(mix3Filtered.prefix(20))
        
        if !mix3Tracks.isEmpty {
            let keyArtists = Array(mix3Tracks.map { profile.extractArtist($0) }
                .filter { !$0.trimmingCharacters(in: .whitespaces).isEmpty }
                .reduce(into: [String]()) { res, a in if !res.contains(a) { res.append(a) } }
                .prefix(3)).joined(separator: ", ")
            
            mixes.append(
                DailyMix(
                    id: "daily_mix_3",
                    title: "Daily Mix 3",
                    subtitle: "\(genre2) Mix",
                    description: "Energizing \(genre2) tracks with \(keyArtists).",
                    tracks: mix3Tracks,
                    coverUrl: mix3Tracks.first?.thumbnailUrl
                )
            )
        }
        
        // MIX 4: Chill / Relax / Focus (Mood-based)
        let mix4Filtered = validPool.filter {
            $0.mood?.caseInsensitiveCompare("Relax") == .orderedSame ||
            $0.mood?.caseInsensitiveCompare("Focus") == .orderedSame ||
            profile.extractGenre($0).localizedCaseInsensitiveContains("Lo-Fi") ||
            profile.extractGenre($0).localizedCaseInsensitiveContains("Acoustic")
        }.reduce(into: [VideoItem]()) { res, item in
            if !res.contains(where: { $0.id == item.id }) { res.append(item) }
        }
        let mix4Tracks = Array(mix4Filtered.prefix(20))
        
        if !mix4Tracks.isEmpty {
            mixes.append(
                DailyMix(
                    id: "daily_mix_4",
                    title: "Daily Mix 4",
                    subtitle: "Chill & Relax",
                    description: "Mellow beats, acoustics, and relaxing sounds to unwind.",
                    tracks: mix4Tracks,
                    coverUrl: mix4Tracks.first?.thumbnailUrl
                )
            )
        }
        
        // MIX 5: Fresh Finds / Discoveries
        let familiarArtistSet = Set(topArtists.map { $0.lowercased() })
        let mix5Filtered = validPool.filter {
            let art = profile.extractArtist($0).lowercased()
            return !familiarArtistSet.contains(art) && !profile.isTrackHidden($0.id) && !profile.isArtistHidden(art)
        }.reduce(into: [VideoItem]()) { res, item in
            if !res.contains(where: { $0.id == item.id }) { res.append(item) }
        }
        let mix5Tracks = Array(mix5Filtered.prefix(20))
        
        if !mix5Tracks.isEmpty {
            mixes.append(
                DailyMix(
                    id: "daily_mix_5",
                    title: "Daily Mix 5",
                    subtitle: "New Discoveries",
                    description: "Fresh sounds and new artists matched to your music taste.",
                    tracks: mix5Tracks,
                    coverUrl: mix5Tracks.first?.thumbnailUrl
                )
            )
        }
        
        return mixes
    }
}
