import Foundation

struct YouTubeSearchEngine {
    struct SearchResult {
        let videos: [VideoItem]
        let suggestions: [String]
    }

    static let trendingSearches = [
        "trending music 2024",
        "lofi hip hop",
        "workout motivation",
        "cooking recipes easy",
        "tech review",
        "gaming highlights",
        "travel vlog",
        "meditation music"
    ]

    static func getTrendingSearches() -> [String] {
        return trendingSearches
    }

    static func getSuggestions(query: String) -> [String] {
        if query.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            return trendingSearches
        }
        let q = query.lowercased().trimmingCharacters(in: .whitespacesAndNewlines)
        var suggestions: [String] = []

        for video in VideoRepository.sampleVideos {
            if video.title.lowercased().contains(q) && !suggestions.contains(video.title) {
                suggestions.append(video.title)
            }
            if video.channelName.lowercased().contains(q) && !suggestions.contains(video.channelName) {
                suggestions.append(video.channelName)
            }
            if !video.artist.isEmpty && video.artist.lowercased().contains(q) && !suggestions.contains(video.artist) {
                suggestions.append(video.artist)
            }
        }

        let extraSuggestions = [
            "\(query) official video",
            "\(query) lyrics",
            "\(query) live performance",
            "\(query) remix",
            "\(query) tutorial"
        ]
        for extra in extraSuggestions {
            if extra.lowercased() != q && !suggestions.contains(extra) {
                suggestions.append(extra)
            }
        }

        return Array(suggestions.prefix(8))
    }

    static func search(query: String) -> SearchResult {
        let videos = VideoRepository.searchVideos(query: query)
        let suggestions = getSuggestions(query: query)
        return SearchResult(videos: videos, suggestions: suggestions)
    }

    static func searchMusic(query: String) -> SearchResult {
        let videos = VideoRepository.searchMusic(query: query)
        let suggestions = getSuggestions(query: query)
        return SearchResult(videos: videos, suggestions: suggestions)
    }
}

