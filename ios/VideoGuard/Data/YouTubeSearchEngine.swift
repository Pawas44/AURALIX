import Foundation

struct YouTubeSearchEngine {
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

    static func getSuggestions(query: String) async -> [String] {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return [] }
        guard let encoded = trimmed.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed),
              let url = URL(string: "https://suggestqueries.google.com/complete/search?client=firefox&ds=yt&q=\(encoded)") else {
            return []
        }

        var request = URLRequest(url: url, timeoutInterval: 4.0)
        request.setValue("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36", forHTTPHeaderField: "User-Agent")

        do {
            let (data, response) = try await URLSession.shared.data(for: request)
            if let http = response as? HTTPURLResponse, http.statusCode == 200 {
                if let jsonArray = try JSONSerialization.jsonObject(with: data) as? [Any],
                   jsonArray.count > 1,
                   let suggestions = jsonArray[1] as? [String] {
                    return suggestions
                }
            }
        } catch {
            // Fall back to empty
        }
        return []
    }

    static func searchVideos(query: String, isMusicOnly: Bool = false, page: Int = 0) async -> [VideoItem] {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else {
            return isMusicOnly ? VideoRepository.getMusicTracks() : VideoRepository.sampleVideos
        }

        let searchQuery: String
        if isMusicOnly && !trimmed.lowercased().contains("song") && !trimmed.lowercased().contains("music") {
            searchQuery = "\(trimmed) song"
        } else {
            searchQuery = trimmed
        }

        guard let encoded = searchQuery.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) else {
            return isMusicOnly ? VideoRepository.searchMusic(query: trimmed) : VideoRepository.searchVideos(query: trimmed)
        }

        let pageParam: String
        switch page {
        case 0: pageParam = ""
        case 1: pageParam = "&sp=EgIQAQ%3D%3D"
        case 2: pageParam = "&sp=EgIQAw%3D%3D"
        default: pageParam = "&sp=EgIQAQ%3D%3D&page=\(page)"
        }

        if let url = URL(string: "https://www.youtube.com/results?search_query=\(encoded)\(pageParam)") {
            var request = URLRequest(url: url, timeoutInterval: 7.0)
            request.setValue("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36", forHTTPHeaderField: "User-Agent")
            request.setValue("en-US,en;q=0.9", forHTTPHeaderField: "Accept-Language")

            do {
                let (data, response) = try await URLSession.shared.data(for: request)
                if let http = response as? HTTPURLResponse, http.statusCode == 200,
                   let html = String(data: data, encoding: .utf8) {
                    let parsed = parseSearchResults(html: html, isMusic: isMusicOnly, query: trimmed)
                    if !parsed.isEmpty {
                        return parsed
                    }
                }
            } catch {
                // Fallback below
            }
        }

        return isMusicOnly ? VideoRepository.searchMusic(query: trimmed) : VideoRepository.searchVideos(query: trimmed)
    }

    static func getRelatedVideos(video: VideoItem, isMusicOnly: Bool = false) async -> [VideoItem] {
        // Strip bracketed tags like [Official Video] or (Lyrics)
        let cleanTitle = video.title.replacingOccurrences(of: #"[\[\(].*?[\]\)]"#, with: "", options: .regularExpression)
            .trimmingCharacters(in: .whitespacesAndNewlines)

        let query: String
        if !video.artist.isEmpty && !video.title.localizedCaseInsensitiveContains(video.artist) {
            query = "\(video.artist) \(cleanTitle)"
        } else if !video.channelName.isEmpty && !video.channelName.hasPrefix("@") && !video.title.localizedCaseInsensitiveContains(video.channelName) {
            query = "\(video.channelName) \(cleanTitle)"
        } else {
            query = cleanTitle.isEmpty ? video.title : cleanTitle
        }

        let res = await searchVideos(query: query, isMusicOnly: isMusicOnly, page: 0).filter { $0.id != video.id }
        if !res.isEmpty { return res }

        let secondaryQuery = video.artist.isEmpty ? video.channelName : video.artist
        if !secondaryQuery.isEmpty {
            let sec = await searchVideos(query: secondaryQuery, isMusicOnly: isMusicOnly, page: 0).filter { $0.id != video.id }
            if !sec.isEmpty { return sec }
        }

        if isMusicOnly {
            return VideoRepository.getMusicTracks().filter { $0.id != video.id }
        } else {
            return VideoRepository.sampleVideos.filter { $0.id != video.id }
        }
    }

    // MARK: - Parsing HTML ytInitialData
    private static func parseSearchResults(html: String, isMusic: Bool, query: String = "") -> [VideoItem] {
        var results: [VideoItem] = []

        let markers = ["var ytInitialData = ", "ytInitialData = ", "window[\"ytInitialData\"] = "]
        var jsonStr: String? = nil

        for marker in markers {
            if let startRange = html.range(of: marker) {
                let startPos = startRange.upperBound
                var endPos: String.Index? = nil
                if let endRange = html.range(of: ";</script>", range: startPos..<html.endIndex) {
                    endPos = endRange.lowerBound
                } else if let endRange2 = html.range(of: "</script>", range: startPos..<html.endIndex) {
                    endPos = endRange2.lowerBound
                }
                if let endPos = endPos {
                    jsonStr = String(html[startPos..<endPos]).trimmingCharacters(in: .whitespacesAndNewlines)
                    if jsonStr?.hasSuffix(";") == true {
                        jsonStr?.removeLast()
                    }
                    break
                }
            }
        }

        if let jsonStr = jsonStr,
           let data = jsonStr.data(using: .utf8),
           let root = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
           let contents = (((root["contents"] as? [String: Any])?["twoColumnSearchResultsRenderer"] as? [String: Any])?["primaryContents"] as? [String: Any])?["sectionListRenderer"] as? [String: Any],
           let sections = contents["contents"] as? [[String: Any]] {

            for section in sections {
                if let itemSection = (section["itemSectionRenderer"] as? [String: Any])?["contents"] as? [[String: Any]] {
                    for item in itemSection {
                        if !isMusic, let cr = item["channelRenderer"] as? [String: Any],
                           let parsed = YouTubeSyncEngine.parseChannelRenderer(cr) {
                            results.append(parsed)
                        }
                        if let vr = item["videoRenderer"] as? [String: Any],
                           var parsed = YouTubeSyncEngine.parseVideoRenderer(vr) {
                            if isMusic {
                                parsed.isMusicTrack = true
                                parsed.category = "Music"
                            }
                            results.append(parsed)
                        }
                        if let cvr = item["compactVideoRenderer"] as? [String: Any],
                           var parsed = YouTubeSyncEngine.parseVideoRenderer(cvr) {
                            if isMusic {
                                parsed.isMusicTrack = true
                                parsed.category = "Music"
                            }
                            results.append(parsed)
                        }
                        if let lvm = item["lockupViewModel"] as? [String: Any],
                           var parsed = YouTubeSyncEngine.parseLockupViewModel(lvm) {
                            if isMusic {
                                parsed.isMusicTrack = true
                                parsed.category = "Music"
                            }
                            results.append(parsed)
                        }
                    }
                }
            }
        }

        // Secondary regex fallback if JSON format differed
        if results.isEmpty {
            let pattern = #""videoId":"([a-zA-Z0-9_-]{11})""#
            if let regex = try? NSRegularExpression(pattern: pattern) {
                let nsHtml = html as NSString
                let matches = regex.matches(in: html, range: NSRange(location: 0, length: nsHtml.length))
                var seen = Set<String>()
                var cleanTitle = query.replacingOccurrences(of: " song", with: "", options: .caseInsensitive).trimmingCharacters(in: .whitespacesAndNewlines)
                if cleanTitle.isEmpty { cleanTitle = "Song Track" }
                let cleanArtist = "YouTube Music"

                for match in matches {
                    if match.numberOfRanges > 1 {
                        let vid = nsHtml.substring(with: match.range(at: 1))
                        if !seen.contains(vid) {
                            seen.insert(vid)
                            results.append(VideoItem(
                                id: vid,
                                title: cleanTitle,
                                channelName: cleanArtist,
                                channelAvatarUrl: "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=150",
                                subscriberCount: "1M subscribers",
                                thumbnailUrl: "https://img.youtube.com/vi/\(vid)/hqdefault.jpg",
                                views: "500K views",
                                uploadTime: "Recently",
                                duration: "3:45",
                                description: cleanTitle,
                                likes: "50K",
                                isMusicTrack: isMusic,
                                artist: cleanArtist,
                                album: "Single",
                                lyrics: "",
                                category: isMusic ? "Music" : "YouTube"
                            ))
                            if results.count >= 15 { break }
                        }
                    }
                }
            }
        }

        return results
    }
}
