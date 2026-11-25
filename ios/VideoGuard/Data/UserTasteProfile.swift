import Foundation

struct ArtistAffinity: Codable {
    let artistName: String
    var score: Double = 0.0
    var playCount: Int = 0
    var completeCount: Int = 0
    var skipCount: Int = 0
    var likeCount: Int = 0
    var dislikeCount: Int = 0
    var lastInteractedMs: Int64 = Int64(Date().timeIntervalSince1970 * 1000)
}

struct TrackAffinity: Codable {
    let trackId: String
    let title: String
    let artist: String
    var playCount: Int = 0
    var completeCount: Int = 0
    var skipCount: Int = 0
    var isLiked: Bool = false
    var isDisliked: Bool = false
    var isHidden: Bool = false
    var lastPlayedMs: Int64 = Int64(Date().timeIntervalSince1970 * 1000)
}

struct GenreAffinity: Codable {
    let genre: String
    var score: Double = 0.0
    var interactionCount: Int = 0
}

final class UserTasteProfile {
    static let shared = UserTasteProfile()

    private let userDefaultsKey = "auralix_user_taste_profile"
    private let lock = NSLock()

    var artistAffinities: [String: ArtistAffinity] = [:]
    var trackAffinities: [String: TrackAffinity] = [:]
    var genreAffinities: [String: GenreAffinity] = [:]
    var moodAffinities: [String: Double] = [:]
    var hiddenTrackIds: Set<String> = []
    var hiddenArtistNames: Set<String> = []
    var discoveredArtists: Set<String> = []

    private init() {
        loadFromPrefs()
    }

    func extractArtist(_ track: VideoItem) -> String {
        if !track.artist.isEmpty && track.artist != "YouTube Music" {
            return track.artist
        }
        let ext = SongMetadataExtractor.extract(title: track.title, rawArtist: track.artist, channelName: track.channelName)
        return ext.primaryArtist.isEmpty ? track.channelName : ext.primaryArtist
    }

    func extractGenre(_ track: VideoItem) -> String {
        let text = "\(track.title) \(track.artist) \(track.category) \(track.mood)".lowercased()
        let genres = ["pop", "hip hop", "rock", "desi", "lofi", "edm", "r&b", "punjabi", "classical", "indie", "electronic", "bollywood"]
        return genres.first { text.contains($0) } ?? (track.category.isEmpty ? "Pop" : track.category)
    }

    func recordTrackStarted(_ track: VideoItem) {
        lock.lock()
        defer { lock.unlock() }

        let artist = extractArtist(track)
        let genre = extractGenre(track)
        let now = Int64(Date().timeIntervalSince1970 * 1000)

        var ta = trackAffinities[track.id] ?? TrackAffinity(trackId: track.id, title: track.title, artist: artist, lastPlayedMs: now)
        ta.playCount += 1
        ta.lastPlayedMs = now
        trackAffinities[track.id] = ta

        if !artist.isEmpty {
            var aa = artistAffinities[artist] ?? ArtistAffinity(artistName: artist, lastInteractedMs: now)
            aa.playCount += 1
            aa.score += 1.0
            aa.lastInteractedMs = now
            artistAffinities[artist] = aa
            discoveredArtists.insert(artist)
        }

        if !genre.isEmpty {
            var ga = genreAffinities[genre] ?? GenreAffinity(genre: genre)
            ga.score += 1.0
            ga.interactionCount += 1
            genreAffinities[genre] = ga
        }
        persist()
    }

    func recordTrackCompleted(_ track: VideoItem, isReplay: Bool = false) {
        lock.lock()
        defer { lock.unlock() }

        let artist = extractArtist(track)
        let now = Int64(Date().timeIntervalSince1970 * 1000)

        var ta = trackAffinities[track.id] ?? TrackAffinity(trackId: track.id, title: track.title, artist: artist, lastPlayedMs: now)
        ta.completeCount += 1
        trackAffinities[track.id] = ta

        if !artist.isEmpty {
            var aa = artistAffinities[artist] ?? ArtistAffinity(artistName: artist, lastInteractedMs: now)
            aa.completeCount += 1
            aa.score += (isReplay ? 4.5 : 3.5)
            aa.lastInteractedMs = now
            artistAffinities[artist] = aa
        }
        persist()
    }

    func recordTrackLiked(_ track: VideoItem, isLiked: Bool) {
        lock.lock()
        defer { lock.unlock() }

        let artist = extractArtist(track)
        var ta = trackAffinities[track.id] ?? TrackAffinity(trackId: track.id, title: track.title, artist: artist)
        ta.isLiked = isLiked
        trackAffinities[track.id] = ta

        if !artist.isEmpty {
            var aa = artistAffinities[artist] ?? ArtistAffinity(artistName: artist)
            if isLiked {
                aa.likeCount += 1
                aa.score += 5.0
            }
            artistAffinities[artist] = aa
        }
        persist()
    }

    func hideTrack(_ trackId: String) {
        lock.lock()
        hiddenTrackIds.insert(trackId)
        lock.unlock()
        persist()
    }

    func isTrackHidden(_ trackId: String) -> Bool {
        lock.lock()
        defer { lock.unlock() }
        return hiddenTrackIds.contains(trackId)
    }

    func isArtistHidden(_ artistName: String) -> Bool {
        lock.lock()
        defer { lock.unlock() }
        return hiddenArtistNames.contains(artistName.lowercased())
    }

    func getTopArtists(limit: Int = 10) -> [ArtistAffinity] {
        lock.lock()
        defer { lock.unlock() }
        return artistAffinities.values.sorted { $0.score > $1.score }.prefix(limit).map { $0 }
    }

    func getTopGenres(limit: Int = 5) -> [GenreAffinity] {
        lock.lock()
        defer { lock.unlock() }
        return genreAffinities.values.sorted { $0.score > $1.score }.prefix(limit).map { $0 }
    }

    func getArtistAffinity(_ artist: String) -> Double {
        lock.lock()
        defer { lock.unlock() }
        return artistAffinities[artist]?.score ?? 0.0
    }

    func getGenreAffinity(_ genre: String) -> Double {
        lock.lock()
        defer { lock.unlock() }
        return genreAffinities[genre]?.score ?? 0.0
    }

    private func persist() {
        // Light JSON / Dictionary persistence
        let dict: [String: Any] = [
            "artists": (try? JSONEncoder().encode(artistAffinities)) ?? Data(),
            "tracks": (try? JSONEncoder().encode(trackAffinities)) ?? Data(),
            "genres": (try? JSONEncoder().encode(genreAffinities)) ?? Data(),
            "hiddenTracks": Array(hiddenTrackIds),
            "hiddenArtists": Array(hiddenArtistNames)
        ]
        UserDefaults.standard.set(dict, forKey: userDefaultsKey)
    }

    private func loadFromPrefs() {
        guard let dict = UserDefaults.standard.dictionary(forKey: userDefaultsKey) else { return }
        if let data = dict["artists"] as? Data, let a = try? JSONDecoder().decode([String: ArtistAffinity].self, from: data) {
            artistAffinities = a
        }
        if let data = dict["tracks"] as? Data, let t = try? JSONDecoder().decode([String: TrackAffinity].self, from: data) {
            trackAffinities = t
        }
        if let data = dict["genres"] as? Data, let g = try? JSONDecoder().decode([String: GenreAffinity].self, from: data) {
            genreAffinities = g
        }
        if let hT = dict["hiddenTracks"] as? [String] {
            hiddenTrackIds = Set(hT)
        }
        if let hA = dict["hiddenArtists"] as? [String] {
            hiddenArtistNames = Set(hA)
        }
    }
}
