import Foundation

final class DiscoverWeeklyGenerator {
    static let shared = DiscoverWeeklyGenerator()
    
    private let keyWeeklyTracks = "auralix_weekly_tracks_json"
    private let keyPreviousTracks = "auralix_prev_weekly_tracks_json"
    private let keyLastRefresh = "auralix_discover_weekly_last_refresh"
    
    private let sevenDaysInterval: TimeInterval = 7 * 24 * 60 * 60
    private let targetDiscoverSize = 30
    
    private var cachedWeekly: [VideoItem] = []
    private var previousWeekly: [VideoItem] = []
    private var lastRefreshTimestamp: TimeInterval = 0
    private let lock = NSLock()
    
    private init() {
        loadFromStorage()
    }
    
    /**
     * Retrieves the current Discover Weekly playlist (~30 tracks).
     * Refreshes automatically if older than 7 days or if forced.
     */
    func getOrGenerateDiscoverWeekly(candidatePool: [VideoItem], forceRefresh: Bool = false) -> [VideoItem] {
        lock.lock()
        defer { lock.unlock() }
        
        let now = Date().timeIntervalSince1970
        if !forceRefresh && cachedWeekly.count >= 15 && (now - lastRefreshTimestamp) < sevenDaysInterval {
            return cachedWeekly
        }
        
        let generated = generateDiscoverWeekly(candidatePool: candidatePool)
        if !generated.isEmpty {
            if !cachedWeekly.isEmpty {
                previousWeekly = cachedWeekly
            }
            cachedWeekly = generated
            lastRefreshTimestamp = now
            saveToStorage()
        }
        
        return !cachedWeekly.isEmpty ? cachedWeekly : Array(candidatePool.prefix(targetDiscoverSize))
    }
    
    private func generateDiscoverWeekly(candidatePool: [VideoItem]) -> [VideoItem] {
        let profile = UserTasteProfile.shared
        let topArtists = profile.getTopArtists(limit: 6).map { $0.artistName }
        
        var familiarTracks: [VideoItem] = []
        var discoveryTracks: [VideoItem] = []
        
        // 1. Separate candidates into familiar and discovery
        for track in candidatePool {
            let artist = profile.extractArtist(track)
            if profile.isTrackHidden(track.id) || profile.isArtistHidden(artist) { continue }
            
            if topArtists.contains(where: { $0.caseInsensitiveCompare(artist) == .orderedSame }) {
                familiarTracks.append(track)
            } else {
                discoveryTracks.append(track)
            }
        }
        
        // 2. Target ratio: ~40% familiar (~12 tracks), ~60% discovery (~18 tracks)
        let targetFamiliarCount = 12
        let targetDiscoveryCount = 18
        
        let selectedFamiliar = Array(familiarTracks.shuffled().prefix(targetFamiliarCount))
        let selectedDiscovery = Array(discoveryTracks.shuffled().prefix(targetDiscoveryCount))
        
        var blended: [VideoItem] = []
        for track in (selectedFamiliar + selectedDiscovery) {
            if !blended.contains(where: { $0.id == track.id }) {
                blended.append(track)
            }
        }
        
        // 3. Fallback: if not enough tracks, pull from candidatePool
        if blended.count < targetDiscoverSize {
            let needed = targetDiscoverSize - blended.count
            let fill = candidatePool.filter { c in
                !blended.contains(where: { $0.id == c.id }) &&
                !profile.isTrackHidden(c.id) &&
                !profile.isArtistHidden(profile.extractArtist(c))
            }.shuffled().prefix(needed)
            blended.append(contentsOf: fill)
        }
        
        return blended.shuffled()
    }
    
    func getPreviousWeek() -> [VideoItem] {
        lock.lock()
        defer { lock.unlock() }
        return previousWeekly
    }
    
    private func saveToStorage() {
        let encoder = JSONEncoder()
        if let currentData = try? encoder.encode(cachedWeekly) {
            UserDefaults.standard.set(currentData, forKey: keyWeeklyTracks)
        }
        if let prevData = try? encoder.encode(previousWeekly) {
            UserDefaults.standard.set(prevData, forKey: keyPreviousTracks)
        }
        UserDefaults.standard.set(lastRefreshTimestamp, forKey: keyLastRefresh)
    }
    
    private func loadFromStorage() {
        lastRefreshTimestamp = UserDefaults.standard.double(forKey: keyLastRefresh)
        let decoder = JSONDecoder()
        if let currentData = UserDefaults.standard.data(forKey: keyWeeklyTracks),
           let list = try? decoder.decode([VideoItem].self, from: currentData) {
            cachedWeekly = list
        }
        if let prevData = UserDefaults.standard.data(forKey: keyPreviousTracks),
           let list = try? decoder.decode([VideoItem].self, from: prevData) {
            previousWeekly = list
        }
    }
}
