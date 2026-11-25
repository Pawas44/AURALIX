import Foundation

struct SongSimilarityEngine {

    private static let genreClusters = [
        "pop", "hip hop", "rock", "desi", "lofi", "edm", "r&b", "punjabi", "classical", "indie", "electronic", "bollywood"
    ]
    private static let vectorDim = 24

    static func extractFeatureVector(_ track: VideoItem) -> [Double] {
        var vec = [Double](repeating: 0.0, count: vectorDim)
        let text = "\(track.title) \(track.artist) \(track.channelName) \(track.category) \(track.mood)".lowercased()

        // 1. Energy
        var energy = 0.50
        if text.contains("remix") || text.contains("bass") || text.contains("party") || text.contains("club") || text.contains("edm") || text.contains("trap") || text.contains("rock") { energy += 0.35 }
        if text.contains("acoustic") || text.contains("unplugged") || text.contains("piano") || text.contains("slowed") || text.contains("lofi") || text.contains("sleep") { energy -= 0.35 }
        vec[0] = min(0.95, max(0.05, energy))

        // 2. Valence
        var valence = 0.50
        if text.contains("happy") || text.contains("love") || text.contains("dance") || text.contains("summer") || text.contains("vibe") || text.contains("celebrate") { valence += 0.30 }
        if text.contains("sad") || text.contains("alone") || text.contains("heartbreak") || text.contains("dark") || text.contains("cry") || text.contains("breakup") { valence -= 0.30 }
        vec[1] = min(0.95, max(0.05, valence))

        // 3. Danceability
        var dance = 0.50
        if text.contains("beat") || text.contains("dance") || text.contains("groove") || text.contains("drop") || text.contains("banger") { dance += 0.35 }
        if text.contains("speech") || text.contains("podcast") || text.contains("meditation") || text.contains("classical") { dance -= 0.35 }
        vec[2] = min(0.95, max(0.05, dance))

        // 4. Acousticness
        var acoustic = 0.30
        if text.contains("acoustic") || text.contains("unplugged") || text.contains("guitar") || text.contains("piano") || text.contains("orchestra") || text.contains("folk") { acoustic += 0.50 }
        if text.contains("synth") || text.contains("electronic") || text.contains("techno") || text.contains("autotune") || text.contains("dubstep") { acoustic -= 0.25 }
        vec[3] = min(0.95, max(0.05, acoustic))

        // 5. Duration Profile
        let parts = track.duration.split(separator: ":").compactMap { Int($0) }
        let durSec: Int
        if parts.count == 2 {
            durSec = parts[0] * 60 + parts[1]
        } else if parts.count == 3 {
            durSec = parts[0] * 3600 + parts[1] * 60 + parts[2]
        } else {
            durSec = 210
        }
        vec[4] = min(1.0, max(0.1, Double(durSec) / 420.0))

        // 6..16: Genre Cluster Projections
        for (i, genre) in genreClusters.enumerated() {
            vec[5 + i] = text.contains(genre) ? 1.0 : 0.0
        }

        // 17..23: Semantic Token Projection via consistent Hash
        let tokens = text.components(separatedBy: CharacterSet.alphanumerics.inverted).filter { $0.count > 2 }
        for token in tokens {
            let h = abs(token.hashValue) % 7
            vec[17 + h] = min(1.0, vec[17 + h] + 0.30)
        }

        return vec
    }

    static func cosineSimilarity(_ v1: [Double], _ v2: [Double]) -> Double {
        guard !v1.isEmpty, !v2.isEmpty else { return 0.0 }
        var dot = 0.0
        var norm1 = 0.0
        var norm2 = 0.0
        let len = min(v1.count, v2.count)
        for i in 0..<len {
            dot += v1[i] * v2[i]
            norm1 += v1[i] * v1[i]
            norm2 += v2[i] * v2[i]
        }
        let denom = sqrt(norm1) * sqrt(norm2)
        return denom > 1e-6 ? min(1.0, max(0.0, dot / denom)) : 0.0
    }

    static func computeProfileCentroid(_ weightedTracks: [(VideoItem, Double)]) -> [Double] {
        var centroid = [Double](repeating: 0.0, count: vectorDim)
        var totalWeight = 0.0
        for (track, weight) in weightedTracks {
            if weight <= 0.0 { continue }
            let vec = extractFeatureVector(track)
            for i in 0..<vectorDim {
                centroid[i] += vec[i] * weight
            }
            totalWeight += weight
        }
        if totalWeight > 0.0 {
            for i in 0..<vectorDim {
                centroid[i] /= totalWeight
            }
        }
        return centroid
    }

    static func computeSimilarity(_ trackA: VideoItem, _ trackB: VideoItem) -> Double {
        if trackA.id == trackB.id { return 1.0 }
        let v1 = extractFeatureVector(trackA)
        let v2 = extractFeatureVector(trackB)
        let vectorSim = cosineSimilarity(v1, v2)

        let artistA = UserTasteProfile.shared.extractArtist(trackA).lowercased()
        let artistB = UserTasteProfile.shared.extractArtist(trackB).lowercased()

        let artistScore: Double
        if !artistA.isEmpty && artistA == artistB {
            artistScore = 1.0
        } else if !artistA.isEmpty && !artistB.isEmpty && (artistA.contains(artistB) || artistB.contains(artistA)) {
            artistScore = 0.70
        } else {
            artistScore = 0.0
        }

        return (vectorSim * 0.65) + (artistScore * 0.35)
    }

    static func maxSimilarityAgainstCollection(_ track: VideoItem, _ collection: [VideoItem]) -> Double {
        var maxSim = 0.0
        for item in collection.prefix(20) {
            let sim = computeSimilarity(track, item)
            if sim > maxSim { maxSim = sim }
        }
        return maxSim
    }
}
