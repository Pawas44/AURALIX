import Foundation

struct CachedSection {
    let tracks: [VideoItem]
    let timestampMs: Double
}

final class RecommendationCache {
    static let shared = RecommendationCache()
    
    private let defaultTTL: TimeInterval = 30 * 60 // 30 minutes in seconds
    private var memoryCache: [String: CachedSection] = [:]
    private let lock = NSLock()
    
    private init() {}
    
    func get(sectionKey: String, ttl: TimeInterval? = nil) -> [VideoItem]? {
        lock.lock()
        defer { lock.unlock() }
        
        guard let entry = memoryCache[sectionKey] else { return nil }
        let allowedTTL = ttl ?? defaultTTL
        let now = Date().timeIntervalSince1970
        if (now - (entry.timestampMs / 1000.0)) > allowedTTL {
            memoryCache.removeValue(forKey: sectionKey)
            return nil
        }
        return entry.tracks
    }
    
    func put(sectionKey: String, tracks: [VideoItem]) {
        guard !tracks.isEmpty else { return }
        lock.lock()
        defer { lock.unlock() }
        let nowMs = Date().timeIntervalSince1970 * 1000.0
        memoryCache[sectionKey] = CachedSection(tracks: tracks, timestampMs: nowMs)
    }
    
    func invalidate(sectionKey: String? = nil) {
        lock.lock()
        defer { lock.unlock() }
        if let key = sectionKey {
            memoryCache.removeValue(forKey: key)
        } else {
            memoryCache.removeAll()
        }
    }
}
