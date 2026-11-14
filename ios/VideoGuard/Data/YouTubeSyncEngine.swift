import Foundation
import Combine
import WebKit
import CommonCrypto

struct MusicFeedData {
    var quickPicks: [VideoItem] = []
    var listenAgain: [VideoItem] = []
    var recommendedVideos: [VideoItem] = []
    var trendingCharts: [VideoItem] = []
}

class YouTubeSyncEngine: ObservableObject {
    static let shared = YouTubeSyncEngine()

    static let innerTubeApiKey = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8"
    static let clientVersion = "2.20261010.01.00"
    static let desktopUA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

    static let musicMoodParams: [String: String] = [
        "Energize": "ggNCSgQIDBABSgQIBBABSgQIBxABSgQIAxABSgQICRADSgQICBABSgQIDRABSgQIDhABSgQIChABSgQIBhABSgQIBRAB",
        "Workout": "ggNCSgQIDBABSgQIBBADSgQIBxABSgQIAxABSgQICRABSgQICBABSgQIDRABSgQIDhABSgQIChABSgQIBhABSgQIBRAB",
        "Relax": "ggNCSgQIDBABSgQIBBABSgQIBxADSgQIAxABSgQICRABSgQICBABSgQIDRABSgQIDhABSgQIChABSgQIBhABSgQIBRAB",
        "Focus": "ggNCSgQIDBABSgQIBBABSgQIBxABSgQIAxABSgQICRABSgQICBABSgQIDRABSgQIDhABSgQIChABSgQIBhADSgQIBRAB",
        "Commute": "ggNCSgQIDBABSgQIBBABSgQIBxABSgQIAxADSgQICRABSgQICBABSgQIDRABSgQIDhABSgQIChABSgQIBhABSgQIBRAB",
        "Party": "ggNCSgQIDBABSgQIBBABSgQIBxABSgQIAxABSgQICRABSgQICBABSgQIDRABSgQIDhADSgQIChABSgQIBhABSgQIBRAB"
    ]

    @Published var homeFeed: [VideoItem] = []
    @Published var musicFeed = MusicFeedData()

    private var homeFeedContinuationToken: String? = nil
    private var homeFeedCategory: String = "All"
    private var homeFeedPage: Int = 0
    private var channelIdCache: [String: String] = [:]
    private var isSyncRunning = false

    private init() {}

    // MARK: - Cookies & SAPISIDHASH
    func getAuthCookies() -> String {
        let cookies = HTTPCookieStorage.shared.cookies ?? []
        let ytCookies = cookies.filter {
            $0.domain.contains("youtube.com") || $0.domain.contains("google.com")
        }
        return ytCookies.map { "\($0.name)=\($0.value)" }.joined(separator: "; ")
    }

    func syncCookiesFromWebKit() async -> String {
        let wkCookies: [HTTPCookie] = await withCheckedContinuation { continuation in
            DispatchQueue.main.async {
                WKWebsiteDataStore.default().httpCookieStore.getAllCookies { cookies in
                    continuation.resume(returning: cookies)
                }
            }
        }
        for cookie in wkCookies {
            if cookie.domain.contains("youtube.com") || cookie.domain.contains("google.com") {
                HTTPCookieStorage.shared.setCookie(cookie)
            }
        }
        let ytCookies = wkCookies.filter { $0.domain.contains("youtube.com") }
        let source = ytCookies.isEmpty ? wkCookies.filter { $0.domain.contains("google.com") } : ytCookies
        return source.map { "\($0.name)=\($0.value)" }.joined(separator: "; ")
    }

    func hasAuthCookies() -> Bool {
        let cookies = getAuthCookies()
        return cookies.contains("SAPISID") ||
               cookies.contains("__Secure-3PAPISID") ||
               cookies.contains("LOGIN_INFO") ||
               cookies.contains("SID")
    }

    private func getSapisidHash(cookies: String, origin: String = "https://www.youtube.com") -> String? {
        let pattern = #"(?:SAPISID|__Secure-3PAPISID)=([^;\s]+)"#
        guard let regex = try? NSRegularExpression(pattern: pattern),
              let match = regex.firstMatch(in: cookies, range: NSRange(cookies.startIndex..., in: cookies)),
              let range = Range(match.range(at: 1), in: cookies) else {
            return nil
        }
        let sapisid = String(cookies[range]).trimmingCharacters(in: .whitespacesAndNewlines)
        let timestamp = Int(Date().timeIntervalSince1970)
        let input = "\(timestamp) \(sapisid) \(origin)"
        guard let data = input.data(using: .utf8) else { return nil }

        var digest = [UInt8](repeating: 0, count: Int(CC_SHA1_DIGEST_LENGTH))
        data.withUnsafeBytes { ptr in
            _ = CC_SHA1(ptr.baseAddress, CC_LONG(data.count), &digest)
        }
        let hex = digest.map { String(format: "%02x", $0) }.joined()
        return "SAPISIDHASH \(timestamp)_\(hex)"
    }

    func cacheHomeFeed(_ videos: [VideoItem]) {
        guard !videos.isEmpty else { return }
        DispatchQueue.main.async {
            self.homeFeed = videos
        }
    }

    // MARK: - InnerTube Networking
    private func buildContext() -> [String: Any] {
        return [
            "client": [
                "clientName": "WEB",
                "clientVersion": Self.clientVersion,
                "hl": "en",
                "gl": "US"
            ]
        ]
    }

    private func buildMusicContext() -> [String: Any] {
        return [
            "client": [
                "clientName": "WEB_REMIX",
                "clientVersion": "1.20240101.01.00",
                "hl": "en",
                "gl": "US"
            ]
        ]
    }

    private func callInnerTube(endpoint: String, payload: [String: Any]) async -> [String: Any]? {
        let cookies = await syncCookiesFromWebKit()
        guard let url = URL(string: "https://www.youtube.com/youtubei/v1/\(endpoint)?key=\(Self.innerTubeApiKey)&prettyPrint=false") else {
            return nil
        }
        var request = URLRequest(url: url, timeoutInterval: 10.0)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue(Self.desktopUA, forHTTPHeaderField: "User-Agent")
        request.setValue("1", forHTTPHeaderField: "X-YouTube-Client-Name")
        request.setValue(Self.clientVersion, forHTTPHeaderField: "X-YouTube-Client-Version")
        request.setValue("https://www.youtube.com", forHTTPHeaderField: "Origin")
        request.setValue("https://www.youtube.com/", forHTTPHeaderField: "Referer")
        request.setValue("https://www.youtube.com", forHTTPHeaderField: "X-Origin")
        request.setValue("0", forHTTPHeaderField: "X-Goog-AuthUser")

        if !cookies.isEmpty {
            request.setValue(cookies, forHTTPHeaderField: "Cookie")
            if let hash = getSapisidHash(cookies: cookies, origin: "https://www.youtube.com") {
                request.setValue(hash, forHTTPHeaderField: "Authorization")
            }
        }

        request.httpBody = try? JSONSerialization.data(withJSONObject: payload)

        do {
            let (data, response) = try await URLSession.shared.data(for: request)
            if let http = response as? HTTPURLResponse, http.statusCode == 200 {
                return try? JSONSerialization.jsonObject(with: data) as? [String: Any]
            }
        } catch {}
        return nil
    }

    private func callInnerTubeMusic(endpoint: String, payload: [String: Any]) async -> [String: Any]? {
        let cookies = await syncCookiesFromWebKit()
        guard let url = URL(string: "https://music.youtube.com/youtubei/v1/\(endpoint)?key=\(Self.innerTubeApiKey)&prettyPrint=false") else {
            return nil
        }
        var request = URLRequest(url: url, timeoutInterval: 10.0)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue(Self.desktopUA, forHTTPHeaderField: "User-Agent")
        request.setValue("67", forHTTPHeaderField: "X-YouTube-Client-Name")
        request.setValue("1.20240101.01.00", forHTTPHeaderField: "X-YouTube-Client-Version")
        request.setValue("https://music.youtube.com", forHTTPHeaderField: "Origin")
        request.setValue("https://music.youtube.com/", forHTTPHeaderField: "Referer")
        request.setValue("https://music.youtube.com", forHTTPHeaderField: "X-Origin")
        request.setValue("0", forHTTPHeaderField: "X-Goog-AuthUser")

        if !cookies.isEmpty {
            request.setValue(cookies, forHTTPHeaderField: "Cookie")
            if let hash = getSapisidHash(cookies: cookies, origin: "https://music.youtube.com") {
                request.setValue(hash, forHTTPHeaderField: "Authorization")
            }
        }

        request.httpBody = try? JSONSerialization.data(withJSONObject: payload)

        do {
            let (data, response) = try await URLSession.shared.data(for: request)
            if let http = response as? HTTPURLResponse, http.statusCode == 200 {
                return try? JSONSerialization.jsonObject(with: data) as? [String: Any]
            }
        } catch {}
        return nil
    }

    private func fetchHtml(urlStr: String, cookies: String) async -> String? {
        guard let url = URL(string: urlStr) else { return nil }
        var request = URLRequest(url: url, timeoutInterval: 8.0)
        request.setValue(Self.desktopUA, forHTTPHeaderField: "User-Agent")
        request.setValue("en-US,en;q=0.9", forHTTPHeaderField: "Accept-Language")
        if !cookies.isEmpty {
            request.setValue(cookies, forHTTPHeaderField: "Cookie")
        }
        do {
            let (data, response) = try await URLSession.shared.data(for: request)
            if let http = response as? HTTPURLResponse, http.statusCode == 200 {
                return String(data: data, encoding: .utf8)
            }
        } catch {}
        return nil
    }

    // MARK: - Search & Topics
    func searchVideosInnerTube(query: String, filterToVideos: Bool = true) async -> [VideoItem] {
        var outVideos: [VideoItem] = []
        var payload: [String: Any] = [
            "context": buildContext(),
            "query": query
        ]
        if filterToVideos {
            payload["params"] = "EgIQAQ%3D%3D"
        }
        if let resp = await callInnerTube(endpoint: "search", payload: payload) {
            Self.extractVideosRecursively(resp, into: &outVideos)
            if let token = Self.extractContinuationToken(resp), !token.isEmpty {
                homeFeedContinuationToken = token
            }
        }
        return Self.distinctVideos(outVideos)
    }

    func getUserAccountTopics() -> [String] {
        let history = UserDataRepository.shared.watchHistory
        var counts: [String: Int] = [:]
        for item in history {
            let ch = item.channelName.trimmingCharacters(in: .whitespacesAndNewlines)
            if !ch.isEmpty && ch != "YouTube Creator" && ch != "YouTube Video" && !Self.isViewOrDateString(ch) {
                counts[ch, default: 0] += 1
            }
        }
        let topCreators = counts.sorted { $0.value > $1.value }.map { $0.key }
        let searchTopics = UserDataRepository.shared.searchHistory.filter { !$0.isEmpty }
        let subs = Array(UserDataRepository.shared.subscribedChannels).filter { !$0.isEmpty }
        let accountName = AccountRepository.shared.currentAccount?.name

        var combined: [String] = []
        if let acc = accountName, !acc.isEmpty, acc != "YouTube Account", acc != "Google User" {
            combined.append(acc)
        }
        combined.append(contentsOf: topCreators.prefix(8))
        combined.append(contentsOf: searchTopics.prefix(6))
        combined.append(contentsOf: subs.prefix(6))

        var seen = Set<String>()
        let unique = combined.filter { seen.insert($0).inserted }
        return unique.isEmpty ? ["trending", "popular music", "gaming", "entertainment"] : unique
    }

    // MARK: - Home Feed & Infinite Scroll
    func fetchHomeFeed(category: String = "All") async -> [VideoItem] {
        homeFeedCategory = category
        homeFeedPage = 0
        let cookies = await syncCookiesFromWebKit()

        if category != "All" && !category.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            var searchResults = await searchVideosInnerTube(query: category)
            if searchResults.isEmpty {
                searchResults = await YouTubeSearchEngine.searchVideos(query: category)
            }
            if !searchResults.isEmpty {
                cacheHomeFeed(searchResults)
                return searchResults
            }
        }

        var browseVideos: [VideoItem] = []
        var subVideos: [VideoItem] = []
        var htmlVideos: [VideoItem] = []

        if let resp = await callInnerTube(endpoint: "browse", payload: [
            "context": buildContext(),
            "browseId": "FEwhat_to_watch"
        ]) {
            var videos: [VideoItem] = []
            Self.extractVideosRecursively(resp, into: &videos)
            if let token = Self.extractContinuationToken(resp), !token.isEmpty {
                homeFeedContinuationToken = token
            }
            browseVideos = Self.distinctVideos(videos)
        }

        if !cookies.isEmpty {
            if let subResp = await callInnerTube(endpoint: "browse", payload: [
                "context": buildContext(),
                "browseId": "FEsubscriptions"
            ]) {
                var rawSubs: [VideoItem] = []
                Self.extractVideosRecursively(subResp, into: &rawSubs)
                subVideos = Self.distinctVideos(rawSubs)
                let channels = Set(rawSubs.map { $0.channelName }.filter { !$0.isEmpty && $0 != "YouTube Creator" })
                if !channels.isEmpty {
                    UserDataRepository.shared.setSubscribedChannels(channels)
                }
            }
        }

        if browseVideos.isEmpty && !cookies.isEmpty {
            if let html = await fetchHtml(urlStr: "https://www.youtube.com/", cookies: cookies) {
                htmlVideos = Self.parseVideosFromHtml(html)
            }
        }

        var topicBatches: [[VideoItem]] = []
        let topics = getUserAccountTopics()
        for topic in topics.prefix(6) {
            var vids = await searchVideosInnerTube(query: topic)
            if vids.isEmpty {
                vids = await YouTubeSearchEngine.searchVideos(query: topic)
            }
            if !vids.isEmpty {
                topicBatches.append(Array(vids.prefix(5)))
            }
        }

        let userLiked = UserDataRepository.shared.playlists.first(where: { $0.id == "liked_videos" })?.videos ?? []
        let userHistory = UserDataRepository.shared.watchHistory

        var feedGroups: [[VideoItem]] = []
        if !browseVideos.isEmpty { feedGroups.append(browseVideos) }
        if !htmlVideos.isEmpty { feedGroups.append(htmlVideos) }
        if !subVideos.isEmpty { feedGroups.append(subVideos) }
        feedGroups.append(contentsOf: topicBatches)
        if !userLiked.isEmpty { feedGroups.append(Array(userLiked.prefix(5))) }
        if !userHistory.isEmpty { feedGroups.append(Array(userHistory.shuffled().prefix(6))) }

        var interleaved: [VideoItem] = []
        var seenIds = Set<String>()
        var channelCounts: [String: Int] = [:]
        let maxBatchSize = feedGroups.map { $0.count }.max() ?? 0

        for idx in 0..<maxBatchSize {
            for group in feedGroups {
                if idx < group.count {
                    let item = group[idx]
                    if !seenIds.contains(item.id) {
                        let count = channelCounts[item.channelName, default: 0]
                        if count < 2 {
                            interleaved.append(item)
                            seenIds.insert(item.id)
                            channelCounts[item.channelName] = count + 1
                        }
                    }
                }
            }
        }

        if interleaved.count < 20 {
            for idx in 0..<maxBatchSize {
                for group in feedGroups {
                    if idx < group.count {
                        let item = group[idx]
                        if !seenIds.contains(item.id) {
                            let count = channelCounts[item.channelName, default: 0]
                            if count < 5 {
                                interleaved.append(item)
                                seenIds.insert(item.id)
                                channelCounts[item.channelName] = count + 1
                            }
                        }
                    }
                }
            }
        }

        if interleaved.isEmpty {
            var popular = await searchVideosInnerTube(query: "trending")
            if popular.isEmpty {
                popular = await YouTubeSearchEngine.searchVideos(query: "trending")
            }
            interleaved.append(contentsOf: popular)
        }

        let finalResult = Self.distinctVideos(interleaved)
        let resolved = finalResult.isEmpty ? VideoRepository.getVideosByCategory(category) : finalResult
        cacheHomeFeed(resolved)
        return resolved
    }

    func fetchMoreHomeFeed() async -> Bool {
        if let token = homeFeedContinuationToken, !token.isEmpty {
            let contPayload: [String: Any] = [
                "context": buildContext(),
                "continuation": token
            ]
            var resp = await callInnerTube(endpoint: "browse", payload: contPayload)
            if resp == nil {
                resp = await callInnerTube(endpoint: "search", payload: contPayload)
            }
            if let resp = resp {
                var batch: [VideoItem] = []
                Self.extractVideosRecursively(resp, into: &batch)
                homeFeedContinuationToken = Self.extractContinuationToken(resp)
                let existingIds = Set(homeFeed.map { $0.id })
                let fresh = Self.distinctVideos(batch).filter { !existingIds.contains($0.id) }
                if !fresh.isEmpty {
                    await MainActor.run {
                        self.homeFeed.append(contentsOf: fresh)
                    }
                    return true
                }
            }
        }

        homeFeedPage += 1
        let userTopics = getUserAccountTopics()
        let queryTopic = (homeFeedCategory != "All" && !homeFeedCategory.isEmpty)
            ? homeFeedCategory
            : userTopics[homeFeedPage % userTopics.count]

        var extra = await searchVideosInnerTube(query: queryTopic)
        if extra.isEmpty {
            extra = await YouTubeSearchEngine.searchVideos(query: queryTopic, page: homeFeedPage)
        }
        let existingIds = Set(homeFeed.map { $0.id })
        let fresh = Self.distinctVideos(extra).filter { !existingIds.contains($0.id) }
        if !fresh.isEmpty {
            await MainActor.run {
                self.homeFeed.append(contentsOf: fresh)
            }
            return true
        }
        return false
    }

    // MARK: - Music Home Feed
    func fetchMusicHomeFeed(mood: String? = nil) async -> MusicFeedData {
        var quickPicks: [VideoItem] = []
        var listenAgain: [VideoItem] = []
        var recommendedVideos: [VideoItem] = []
        var trendingCharts: [VideoItem] = []
        var allParsedTracks: [VideoItem] = []
        var playlistIdsToUnpack: [String] = []

        var payload: [String: Any] = [
            "context": buildMusicContext(),
            "browseId": "FEmusic_home"
        ]
        if let m = mood, let p = Self.musicMoodParams[m], !p.isEmpty {
            payload["params"] = p
        }

        if let resp = await callInnerTubeMusic(endpoint: "browse", payload: payload),
           let contents = resp["contents"] as? [String: Any],
           let singleCol = contents["singleColumnBrowseResultsRenderer"] as? [String: Any],
           let tabs = singleCol["tabs"] as? [[String: Any]],
           let firstTab = tabs.first?["tabRenderer"] as? [String: Any],
           let tabContent = firstTab["content"] as? [String: Any],
           let sectionList = tabContent["sectionListRenderer"] as? [String: Any],
           let sections = sectionList["contents"] as? [[String: Any]] {

            for sec in sections {
                let shelf = (sec["musicCarouselShelfRenderer"] as? [String: Any])
                    ?? (sec["musicShelfRenderer"] as? [String: Any])
                guard let shelf = shelf else { continue }

                let header = shelf["header"] as? [String: Any]
                let headerRenderer = (header?["musicCarouselShelfBasicHeaderRenderer"] as? [String: Any])
                    ?? (header?["musicHeaderRenderer"] as? [String: Any])
                let shelfTitle = (((headerRenderer?["title"] as? [String: Any])?["runs"] as? [[String: Any]])?.first?["text"] as? String ?? "").trimmingCharacters(in: .whitespacesAndNewlines)

                guard let shelfItems = shelf["contents"] as? [[String: Any]] else { continue }
                var currentShelfTracks: [VideoItem] = []

                for itemObj in shelfItems {
                    if let respItem = itemObj["musicResponsiveListItemRenderer"] as? [String: Any],
                       let parsed = Self.parseMusicResponsiveListItemRenderer(respItem) {
                        currentShelfTracks.append(parsed)
                    } else if let twoRow = itemObj["musicTwoRowItemRenderer"] as? [String: Any] {
                        if let parsed = Self.parseMusicTwoRowItemRenderer(twoRow) {
                            currentShelfTracks.append(parsed)
                        } else {
                            let navEp = twoRow["navigationEndpoint"] as? [String: Any]
                            let browseId = (navEp?["browseEndpoint"] as? [String: Any])?["browseId"] as? String
                            let overlay = twoRow["thumbnailOverlay"] as? [String: Any]
                            let playBtn = ((overlay?["musicItemThumbnailOverlayRenderer"] as? [String: Any])?["content"] as? [String: Any])?["musicPlayButtonRenderer"] as? [String: Any]
                            let playNav = playBtn?["playNavigationEndpoint"] as? [String: Any]
                            let wpId = (playNav?["watchPlaylistEndpoint"] as? [String: Any])?["playlistId"] as? String
                            let plId = browseId ?? wpId
                            if let plId = plId, (plId.hasPrefix("VL") || plId.hasPrefix("RDCL")) {
                                playlistIdsToUnpack.append(plId)
                            }
                        }
                    }
                }

                if !currentShelfTracks.isEmpty {
                    allParsedTracks.append(contentsOf: currentShelfTracks)
                    let lower = shelfTitle.lowercased()
                    if lower.contains("quick pick") || lower.contains("start radio") || lower.contains("picks") {
                        quickPicks.append(contentsOf: currentShelfTracks)
                    } else if lower.contains("listen again") || lower.contains("forgotten") || lower.contains("history") || lower.contains("recently") {
                        listenAgain.append(contentsOf: currentShelfTracks)
                    } else if lower.contains("recommended") || lower.contains("video") || lower.contains("mixed") {
                        recommendedVideos.append(contentsOf: currentShelfTracks)
                    } else if lower.contains("chart") || lower.contains("trending") || lower.contains("hit") || lower.contains("top") {
                        trendingCharts.append(contentsOf: currentShelfTracks)
                    } else {
                        if quickPicks.isEmpty { quickPicks.append(contentsOf: currentShelfTracks) }
                        else if trendingCharts.isEmpty { trendingCharts.append(contentsOf: currentShelfTracks) }
                        else { recommendedVideos.append(contentsOf: currentShelfTracks) }
                    }
                }
            }
        }

        if allParsedTracks.count < 10 && !playlistIdsToUnpack.isEmpty {
            for plId in playlistIdsToUnpack.prefix(2) {
                let unpacked = await fetchPlaylistVideos(playlistId: plId, maxVideos: 20)
                if !unpacked.isEmpty {
                    allParsedTracks.append(contentsOf: unpacked)
                    if quickPicks.isEmpty { quickPicks.append(contentsOf: unpacked) }
                    else { trendingCharts.append(contentsOf: unpacked) }
                }
            }
        }

        let userHistory = UserDataRepository.shared.watchHistory
        let accountMusicTracks = Self.distinctVideos(userHistory.filter {
            $0.isMusicTrack ||
            $0.title.localizedCaseInsensitiveContains("song") ||
            $0.title.localizedCaseInsensitiveContains("music") ||
            $0.title.localizedCaseInsensitiveContains("audio") ||
            $0.title.localizedCaseInsensitiveContains("official video") ||
            $0.category.caseInsensitiveCompare("Music") == .orderedSame
        })
        let userLiked = UserDataRepository.shared.playlists.first(where: { $0.id == "liked_videos" })?.videos ?? []
        let likedMusic = Self.distinctVideos(userLiked.filter {
            $0.isMusicTrack || $0.category.caseInsensitiveCompare("Music") == .orderedSame ||
            $0.title.localizedCaseInsensitiveContains("song") || $0.title.localizedCaseInsensitiveContains("music")
        })

        let finalListenAgain = !accountMusicTracks.isEmpty
            ? Self.distinctVideos(Array(accountMusicTracks.prefix(15)) + listenAgain)
            : (!listenAgain.isEmpty ? Self.distinctVideos(listenAgain) : (!userHistory.isEmpty ? Array(userHistory.prefix(12)) : Array(allParsedTracks.prefix(10))))

        let finalQuickPicks = !quickPicks.isEmpty
            ? Self.distinctVideos(quickPicks)
            : (!likedMusic.isEmpty ? Array(likedMusic.prefix(12)) : (!accountMusicTracks.isEmpty ? Array(accountMusicTracks.shuffled().prefix(12)) : Array(allParsedTracks.prefix(12))))

        let finalRecommended = !recommendedVideos.isEmpty
            ? Self.distinctVideos(recommendedVideos)
            : (!userLiked.isEmpty ? Array(userLiked.prefix(12)) : Self.distinctVideos(allParsedTracks.reversed()))

        let finalTrending = !trendingCharts.isEmpty
            ? Self.distinctVideos(trendingCharts)
            : Self.distinctVideos(Array(allParsedTracks.dropFirst(4)))

        let resolvedQuick = finalQuickPicks.isEmpty ? (userHistory.isEmpty ? VideoRepository.sampleVideos : Array(userHistory.prefix(10))) : finalQuickPicks
        let resolvedListen = finalListenAgain.isEmpty ? (userHistory.isEmpty ? VideoRepository.sampleVideos : Array(userHistory.prefix(10))) : finalListenAgain
        let resolvedRec = finalRecommended.isEmpty ? resolvedQuick : finalRecommended
        let resolvedTrend = finalTrending.isEmpty ? resolvedQuick : finalTrending

        let result = MusicFeedData(
            quickPicks: resolvedQuick,
            listenAgain: resolvedListen,
            recommendedVideos: resolvedRec,
            trendingCharts: resolvedTrend
        )
        await MainActor.run {
            self.musicFeed = result
        }
        return result
    }

    // MARK: - User History, Playlists & Profile Sync
    func fetchHistory() async -> [VideoItem] {
        let cookies = await syncCookiesFromWebKit()
        guard !cookies.isEmpty else { return [] }

        if let resp = await callInnerTube(endpoint: "browse", payload: [
            "context": buildContext(),
            "browseId": "FEhistory"
        ]) {
            var videos: [VideoItem] = []
            Self.extractVideosRecursively(resp, into: &videos)
            if !videos.isEmpty {
                return Self.distinctVideos(videos)
            }
        }

        if let html = await fetchHtml(urlStr: "https://www.youtube.com/feed/history", cookies: cookies) {
            let videos = Self.parseVideosFromHtml(html)
            if !videos.isEmpty { return videos }
        }
        return []
    }

    func fetchPlaylists() async -> [UserPlaylist] {
        let cookies = await syncCookiesFromWebKit()
        var playlists: [UserPlaylist] = []

        if !cookies.isEmpty {
            var resp = await callInnerTube(endpoint: "browse", payload: [
                "context": buildContext(),
                "browseId": "FEplaylist_aggregation"
            ])
            var iterations = 0
            while let r = resp, iterations < 25 {
                Self.extractPlaylistsRecursively(r, into: &playlists)
                guard let token = Self.extractContinuationToken(r), !token.isEmpty else { break }
                iterations += 1
                resp = await callInnerTube(endpoint: "browse", payload: [
                    "context": buildContext(),
                    "continuation": token
                ])
            }

            if let libResp = await callInnerTube(endpoint: "browse", payload: [
                "context": buildContext(),
                "browseId": "FElibrary"
            ]) {
                Self.extractPlaylistsRecursively(libResp, into: &playlists)
            }

            var subs = Set<String>()
            if let chResp = await callInnerTube(endpoint: "browse", payload: [
                "context": buildContext(),
                "browseId": "FEchannels"
            ]) {
                Self.extractPlaylistsRecursively(chResp, into: &playlists)
                Self.extractSubscribedChannelsRecursively(chResp, into: &subs)
            }

            if let guideResp = await callInnerTube(endpoint: "guide", payload: [
                "context": buildContext()
            ]) {
                Self.extractPlaylistsRecursively(guideResp, into: &playlists)
                Self.extractSubscribedChannelsRecursively(guideResp, into: &subs)
            }

            if !subs.isEmpty {
                UserDataRepository.shared.setSubscribedChannels(subs)
            }
        }

        var channelUrls: [String] = []
        if let handle = AccountRepository.shared.currentAccount?.handle.trimmingCharacters(in: .whitespacesAndNewlines).replacingOccurrences(of: "@", with: ""),
           !handle.isEmpty && handle != "youtube_user" {
            channelUrls.append("https://www.youtube.com/@\(handle)/playlists")
        }
        channelUrls.append("https://www.youtube.com/feed/playlists")
        channelUrls.append("https://www.youtube.com/channel/mine/playlists")

        for u in channelUrls {
            if let html = await fetchHtml(urlStr: u, cookies: cookies) {
                playlists.append(contentsOf: Self.parsePlaylistsFromHtml(html))
            }
        }

        var seen = Set<String>()
        return playlists.filter {
            !$0.id.isEmpty && !$0.title.isEmpty &&
            $0.id != "WL" && $0.id != "LL" && $0.id != "history" &&
            !$0.id.hasPrefix("UC") && !$0.id.hasPrefix("FE") &&
            seen.insert($0.id).inserted
        }
    }

    func fetchPlaylistVideos(playlistId: String, maxVideos: Int = 2000) async -> [VideoItem] {
        if playlistId.hasPrefix("playlist_") || playlistId == "favorites" {
            return UserDataRepository.shared.playlists.first(where: { $0.id == playlistId })?.videos ?? []
        }

        let cookies = await syncCookiesFromWebKit()
        let rawId: String
        switch playlistId {
        case "liked_videos", "LL", "VLLL": rawId = "LL"
        case "watch_later", "WL", "VLWL": rawId = "WL"
        default: rawId = playlistId.hasPrefix("VL") ? String(playlistId.dropFirst(2)) : playlistId
        }
        let cleanId = (rawId == "LL" || rawId == "WL") ? "VL\(rawId)" : "VL\(rawId)"

        func queryBrowse(_ browseId: String) async -> [VideoItem] {
            var collected: [VideoItem] = []
            var resp = await callInnerTube(endpoint: "browse", payload: [
                "context": buildContext(),
                "browseId": browseId
            ])
            var iterations = 0
            while let r = resp, iterations < 50 && collected.count < maxVideos {
                var batch: [VideoItem] = []
                Self.extractVideosRecursively(r, into: &batch)
                if !batch.isEmpty { collected.append(contentsOf: batch) }
                guard let token = Self.extractContinuationToken(r), !token.isEmpty else { break }
                iterations += 1
                resp = await callInnerTube(endpoint: "browse", payload: [
                    "context": buildContext(),
                    "continuation": token
                ])
            }
            return Self.distinctVideos(collected)
        }

        var results = await queryBrowse(cleanId)
        if !results.isEmpty { return results }

        if rawId != cleanId && rawId != "WL" && rawId != "LL" {
            results = await queryBrowse(rawId)
            if !results.isEmpty { return results }
        }

        if let html = await fetchHtml(urlStr: "https://www.youtube.com/playlist?list=\(rawId)", cookies: cookies) {
            let videos = Self.parseVideosFromHtml(html)
            if !videos.isEmpty { return videos }
        }
        return []
    }

    func syncProfileDetails() async {
        let cookies = await syncCookiesFromWebKit()
        guard !cookies.isEmpty else { return }

        var extractedName = ""
        var extractedEmail = ""
        var extractedHandle = ""
        var extractedAvatar: String? = nil

        if let resp = await callInnerTube(endpoint: "account/account_menu", payload: ["context": buildContext()]),
           let actions = resp["actions"] as? [[String: Any]],
           let header = ((((actions.first?["openPopupAction"] as? [String: Any])?["popup"] as? [String: Any])?["multiPageMenuRenderer"] as? [String: Any])?["header"] as? [String: Any])?["activeAccountHeaderRenderer"] as? [String: Any] {

            if let nameObj = header["accountName"] as? [String: Any] {
                extractedName = (nameObj["simpleText"] as? String)
                    ?? ((nameObj["runs"] as? [[String: Any]])?.first?["text"] as? String)
                    ?? ""
            }
            if let emailObj = header["email"] as? [String: Any] {
                extractedEmail = (emailObj["simpleText"] as? String)
                    ?? ((emailObj["runs"] as? [[String: Any]])?.first?["text"] as? String)
                    ?? ""
            }
            if let handleObj = header["channelHandle"] as? [String: Any] {
                extractedHandle = (handleObj["simpleText"] as? String)
                    ?? ((handleObj["runs"] as? [[String: Any]])?.first?["text"] as? String)
                    ?? ""
            }
            if let thumbs = (header["accountPhoto"] as? [String: Any])?["thumbnails"] as? [[String: Any]],
               let url = thumbs.last?["url"] as? String, !url.isEmpty {
                extractedAvatar = url
            }
        }

        if !extractedName.isEmpty || !extractedEmail.isEmpty {
            let current = AccountRepository.shared.currentAccount
            let finalName = extractedName.isEmpty ? (current?.name ?? "YouTube User") : extractedName
            let finalEmail = extractedEmail.isEmpty ? (current?.email ?? "") : extractedEmail
            let finalHandle = !extractedHandle.isEmpty
                ? (extractedHandle.hasPrefix("@") ? extractedHandle : "@\(extractedHandle)")
                : (current?.handle ?? "@\(finalName.lowercased().replacingOccurrences(of: "[^a-z0-9_]", with: "_", options: .regularExpression))")
            let finalAvatar = extractedAvatar ?? current?.avatarUrl

            AccountRepository.shared.setGoogleAccount(
                name: finalName,
                email: finalEmail,
                avatarUrl: finalAvatar,
                handle: finalHandle
            )
        }
    }

    func syncAllUserData() async {
        guard !isSyncRunning else { return }
        isSyncRunning = true
        defer { isSyncRunning = false }

        await syncProfileDetails()

        let onlineHistory = await fetchHistory()
        if !onlineHistory.isEmpty {
            UserDataRepository.shared.setOnlineHistory(onlineHistory)
        }

        let onlinePlaylists = await fetchPlaylists()
        if !onlinePlaylists.isEmpty {
            UserDataRepository.shared.setOnlinePlaylists(onlinePlaylists)
        }

        let liked = await fetchPlaylistVideos(playlistId: "VLLL", maxVideos: 2000)
        if !liked.isEmpty {
            UserDataRepository.shared.updateLikedVideos(liked)
        }

        let watchLater = await fetchPlaylistVideos(playlistId: "VLWL", maxVideos: 2000)
        if !watchLater.isEmpty {
            UserDataRepository.shared.updateWatchLaterVideos(watchLater)
        }

        for pl in onlinePlaylists {
            if pl.id != "liked_videos" && pl.id != "watch_later" && !pl.id.hasPrefix("playlist_") && pl.id != "favorites" {
                let vids = await fetchPlaylistVideos(playlistId: pl.id, maxVideos: 2000)
                if !vids.isEmpty {
                    UserDataRepository.shared.updatePlaylistVideos(playlistId: pl.id, videos: vids)
                }
            }
        }

        _ = await fetchHomeFeed(category: "All")
    }

    // MARK: - Channel Fetching
    func resolveChannelId(_ channelNameOrId: String) async -> String? {
        let trimmed = channelNameOrId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return nil }
        if trimmed.hasPrefix("UC") && trimmed.count >= 20 { return trimmed }
        if let cached = channelIdCache[trimmed] { return cached }

        if let resp = await callInnerTube(endpoint: "search", payload: [
            "context": buildContext(),
            "query": trimmed,
            "params": "EgIQAg%3D%3D"
        ]), let cid = Self.findChannelIdRecursively(resp) {
            channelIdCache[trimmed] = cid
            return cid
        }
        return nil
    }

    func fetchChannelVideos(channelNameOrId: String, continuationToken: String? = nil, fallbackAvatar: String? = nil) async -> ([VideoItem], String?) {
        var outVideos: [VideoItem] = []
        var nextToken: String? = nil
        var headerChannelName: String? = nil
        var headerAvatarUrl: String? = nil

        if let token = continuationToken, !token.isEmpty {
            if let resp = await callInnerTube(endpoint: "browse", payload: [
                "context": buildContext(),
                "continuation": token
            ]) {
                Self.extractVideosRecursively(resp, into: &outVideos)
                nextToken = Self.extractContinuationToken(resp)
            }
        } else {
            if let cid = await resolveChannelId(channelNameOrId),
               let resp = await callInnerTube(endpoint: "browse", payload: [
                   "context": buildContext(),
                   "browseId": cid,
                   "params": "EgZ2aWRlb3PyBgQKAjoA"
               ]) {
                Self.extractVideosRecursively(resp, into: &outVideos)
                nextToken = Self.extractContinuationToken(resp)
                if let header = resp["header"] as? [String: Any] {
                    if let pageHeader = header["pageHeaderRenderer"] as? [String: Any] {
                        headerChannelName = (pageHeader["pageTitle"] as? String).flatMap { $0.isEmpty ? nil : $0 }
                        headerAvatarUrl = Self.extractFirstImageUrl(pageHeader)
                    }
                    if let c4 = header["c4TabbedHeaderRenderer"] as? [String: Any] {
                        if headerChannelName == nil {
                            headerChannelName = (c4["title"] as? String).flatMap { $0.isEmpty ? nil : $0 }
                        }
                        if headerAvatarUrl == nil {
                            headerAvatarUrl = Self.extractFirstImageUrl(c4["avatar"])
                        }
                    }
                }
            }
            if outVideos.isEmpty {
                let fallback = await YouTubeSearchEngine.searchVideos(query: "\(channelNameOrId) official")
                if !fallback.isEmpty { return (fallback, nil) }
            }
        }

        let effectiveChannel = headerChannelName ?? (!channelNameOrId.hasPrefix("UC") ? channelNameOrId : "YouTube Creator")
        let effectiveAvatar = headerAvatarUrl ?? fallbackAvatar

        let sanitized: [VideoItem] = outVideos.map { vid in
            var copy = vid
            if copy.channelName == "YouTube Creator" || Self.isViewOrDateString(copy.channelName) || copy.channelName.isEmpty {
                copy = VideoItem(
                    id: copy.id, title: copy.title, channelName: effectiveChannel,
                    channelAvatarUrl: (copy.channelAvatarUrl.isEmpty || copy.channelAvatarUrl.contains("unsplash")) ? (effectiveAvatar ?? copy.channelAvatarUrl) : copy.channelAvatarUrl,
                    subscriberCount: copy.subscriberCount, thumbnailUrl: copy.thumbnailUrl,
                    views: copy.views, uploadTime: copy.uploadTime, duration: copy.duration,
                    description: copy.description, likes: copy.likes, isMusicTrack: copy.isMusicTrack,
                    artist: effectiveChannel, album: copy.album, lyrics: copy.lyrics,
                    category: copy.category, mood: copy.mood, channelId: copy.channelId
                )
            }
            return copy
        }
        return (Self.distinctVideos(sanitized), nextToken)
    }

    func fetchChannelPlaylists(channelNameOrId: String, continuationToken: String? = nil) async -> ([UserPlaylist], String?) {
        var outPlaylists: [UserPlaylist] = []
        var nextToken: String? = nil

        if let token = continuationToken, !token.isEmpty {
            if let resp = await callInnerTube(endpoint: "browse", payload: [
                "context": buildContext(),
                "continuation": token
            ]) {
                Self.extractPlaylistsRecursively(resp, into: &outPlaylists)
                nextToken = Self.extractContinuationToken(resp)
            }
            return (Self.distinctPlaylists(outPlaylists), nextToken)
        }

        if let cid = await resolveChannelId(channelNameOrId) {
            if let resp1 = await callInnerTube(endpoint: "browse", payload: [
                "context": buildContext(),
                "browseId": cid,
                "params": "EglwbGF5bGlzdHPyBgoKCEIGCgIQaCIA"
            ]) {
                Self.extractPlaylistsRecursively(resp1, into: &outPlaylists)
                nextToken = Self.extractContinuationToken(resp1)
            }
            if outPlaylists.isEmpty,
               let resp2 = await callInnerTube(endpoint: "browse", payload: [
                   "context": buildContext(),
                   "browseId": cid,
                   "params": "EghwbGF5bGlzdHPyBgQKAjoA"
               ]) {
                Self.extractPlaylistsRecursively(resp2, into: &outPlaylists)
                nextToken = Self.extractContinuationToken(resp2)
            }
        }
        return (Self.distinctPlaylists(outPlaylists), nextToken)
    }

    func fetchChannelShorts(channelNameOrId: String, continuationToken: String? = nil, fallbackAvatar: String? = nil) async -> ([VideoItem], String?) {
        var outShorts: [VideoItem] = []
        var nextToken: String? = nil

        if let token = continuationToken, !token.isEmpty {
            if let resp = await callInnerTube(endpoint: "browse", payload: [
                "context": buildContext(),
                "continuation": token
            ]) {
                Self.extractVideosRecursively(resp, into: &outShorts)
                nextToken = Self.extractContinuationToken(resp)
            }
        } else if let cid = await resolveChannelId(channelNameOrId),
                  let resp = await callInnerTube(endpoint: "browse", payload: [
                      "context": buildContext(),
                      "browseId": cid,
                      "params": "EgZzaG9ydHPyBgUKA5oBAA%3D%3D"
                  ]) {
            Self.extractVideosRecursively(resp, into: &outShorts)
            nextToken = Self.extractContinuationToken(resp)
        }
        return (Self.distinctVideos(outShorts), nextToken)
    }

    // MARK: - JSON & HTML Parsers
    static func parseVideosFromJsonString(_ jsonStr: String) -> [VideoItem] {
        guard let data = jsonStr.data(using: .utf8),
              let root = try? JSONSerialization.jsonObject(with: data) else {
            return []
        }
        var results: [VideoItem] = []
        extractVideosRecursively(root, into: &results)
        return distinctVideos(results)
    }

    static func parseVideosFromHtml(_ html: String) -> [VideoItem] {
        guard let markerRange = html.range(of: "ytInitialData"),
              let eqRange = html.range(of: "=", range: markerRange.upperBound..<html.endIndex),
              let braceRange = html.range(of: "{", range: eqRange.upperBound..<html.endIndex),
              html.distance(from: eqRange.upperBound, to: braceRange.lowerBound) < 15,
              let endRange = html.range(of: "</script>", range: braceRange.lowerBound..<html.endIndex) else {
            return []
        }
        var jsonStr = String(html[braceRange.lowerBound..<endRange.lowerBound]).trimmingCharacters(in: .whitespacesAndNewlines)
        if jsonStr.hasSuffix(";") { jsonStr.removeLast() }
        return parseVideosFromJsonString(jsonStr)
    }

    static func parsePlaylistsFromHtml(_ html: String) -> [UserPlaylist] {
        guard let markerRange = html.range(of: "ytInitialData"),
              let eqRange = html.range(of: "=", range: markerRange.upperBound..<html.endIndex),
              let braceRange = html.range(of: "{", range: eqRange.upperBound..<html.endIndex),
              html.distance(from: eqRange.upperBound, to: braceRange.lowerBound) < 15,
              let endRange = html.range(of: "</script>", range: braceRange.lowerBound..<html.endIndex) else {
            return []
        }
        var jsonStr = String(html[braceRange.lowerBound..<endRange.lowerBound]).trimmingCharacters(in: .whitespacesAndNewlines)
        if jsonStr.hasSuffix(";") { jsonStr.removeLast() }
        guard let data = jsonStr.data(using: .utf8),
              let root = try? JSONSerialization.jsonObject(with: data) else {
            return []
        }
        var playlists: [UserPlaylist] = []
        extractPlaylistsRecursively(root, into: &playlists)
        return distinctPlaylists(playlists)
    }

    static func isViewOrDateString(_ text: String) -> Bool {
        let t = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !t.isEmpty else { return false }
        let lower = t.lowercased()
        if lower.hasSuffix("views") || lower.hasSuffix("view") || lower.contains(" views") || lower.contains(" view") { return true }
        if lower.hasSuffix("ago") || lower.hasPrefix("streamed") || lower.contains("streamed") || lower.hasSuffix("watching") || lower.contains("watching") { return true }
        if lower.hasPrefix("premier") || lower.contains("scheduled") { return true }
        return false
    }

    static func extractVideosRecursively(_ obj: Any, into outList: inout [VideoItem]) {
        if let dict = obj as? [String: Any] {
            if let cr = dict["channelRenderer"] as? [String: Any], let parsed = parseChannelRenderer(cr) { outList.append(parsed) }
            if let lvm = dict["lockupViewModel"] as? [String: Any], let parsed = parseLockupViewModel(lvm) { outList.append(parsed) }
            if let vr = dict["videoRenderer"] as? [String: Any], let parsed = parseVideoRenderer(vr) { outList.append(parsed) }
            if let pvr = dict["playlistVideoRenderer"] as? [String: Any], let parsed = parseVideoRenderer(pvr) { outList.append(parsed) }
            if let ppvr = dict["playlistPanelVideoRenderer"] as? [String: Any], let parsed = parseVideoRenderer(ppvr) { outList.append(parsed) }
            if let cvr = dict["compactVideoRenderer"] as? [String: Any], let parsed = parseVideoRenderer(cvr) { outList.append(parsed) }
            if let gvr = dict["gridVideoRenderer"] as? [String: Any], let parsed = parseVideoRenderer(gvr) { outList.append(parsed) }
            if let rir = dict["reelItemRenderer"] as? [String: Any], let parsed = parseVideoRenderer(rir) { outList.append(parsed) }

            for (_, val) in dict {
                extractVideosRecursively(val, into: &outList)
            }
        } else if let arr = obj as? [Any] {
            for elem in arr {
                extractVideosRecursively(elem, into: &outList)
            }
        }
    }

    static func parseLockupViewModel(_ lvm: [String: Any]) -> VideoItem? {
        let contentType = lvm["contentType"] as? String ?? ""
        if contentType == "LOCKUP_CONTENT_TYPE_PLAYLIST" || contentType == "LOCKUP_CONTENT_TYPE_PODCAST" { return nil }
        let vid = lvm["contentId"] as? String ?? ""
        if vid.isEmpty || vid.hasPrefix("PL") || vid.hasPrefix("VL") { return nil }

        let meta = (lvm["metadata"] as? [String: Any])?["lockupMetadataViewModel"] as? [String: Any]
        let title = (meta?["title"] as? [String: Any])?["content"] as? String ?? ""
        if title.isEmpty { return nil }

        var channel = "YouTube Creator"
        var views = "1M views"
        var uploadTime = "Recently"

        if let rows = ((meta?["metadata"] as? [String: Any])?["contentMetadataViewModel"] as? [String: Any])?["metadataRows"] as? [[String: Any]] {
            var allParts: [String] = []
            for row in rows {
                if let rowParts = row["metadataParts"] as? [[String: Any]] {
                    for part in rowParts {
                        if let txt = (part["text"] as? [String: Any])?["content"] as? String,
                           !txt.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                            allParts.append(txt.trimmingCharacters(in: .whitespacesAndNewlines))
                        }
                    }
                }
            }
            for part in allParts {
                if isViewOrDateString(part) {
                    let lower = part.lowercased()
                    if lower.contains("view") || lower.contains("watching") { views = part }
                    else if lower.contains("ago") || lower.contains("streamed") { uploadTime = part }
                } else if channel == "YouTube Creator" {
                    channel = part
                }
            }
        }

        var avatar = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=150"
        if let sources = ((((meta?["image"] as? [String: Any])?["decoratedAvatarViewModel"] as? [String: Any])?["avatar"] as? [String: Any])?["avatarViewModel"] as? [String: Any])?["image"] as? [String: Any],
           let list = sources["sources"] as? [[String: Any]],
           let u = list.last?["url"] as? String, !u.isEmpty {
            avatar = u.hasPrefix("//") ? "https:\(u)" : u
        }

        var thumb = "https://img.youtube.com/vi/\(vid)/hqdefault.jpg"
        if let thumbSources = (((lvm["contentImage"] as? [String: Any])?["thumbnailViewModel"] as? [String: Any])?["image"] as? [String: Any])?["sources"] as? [[String: Any]],
           let t = thumbSources.last?["url"] as? String, !t.isEmpty {
            thumb = t.hasPrefix("//") ? "https:\(t)" : t
        }

        var duration = "3:45"
        if let overlays = ((lvm["contentImage"] as? [String: Any])?["thumbnailViewModel"] as? [String: Any])?["overlays"] as? [[String: Any]] {
            for ov in overlays {
                if let badges = (ov["thumbnailBottomOverlayViewModel"] as? [String: Any])?["badges"] as? [[String: Any]],
                   let badgeText = (badges.first?["thumbnailBadgeViewModel"] as? [String: Any])?["text"] as? String,
                   !badgeText.isEmpty {
                    duration = badgeText
                    break
                }
            }
        }

        return VideoItem(
            id: vid, title: title, channelName: channel, channelAvatarUrl: avatar,
            subscriberCount: "Subscribers", thumbnailUrl: thumb, views: views,
            uploadTime: uploadTime, duration: duration, description: title, likes: "100K",
            isMusicTrack: title.localizedCaseInsensitiveContains("Music") || title.localizedCaseInsensitiveContains("Song") || title.localizedCaseInsensitiveContains("ft."),
            artist: channel, album: "Single", lyrics: "", category: "YouTube"
        )
    }

    static func parseChannelRenderer(_ cr: [String: Any]) -> VideoItem? {
        let cid = cr["channelId"] as? String ?? ""
        let titleObj = cr["title"] as? [String: Any]
        let title = (titleObj?["simpleText"] as? String)
            ?? ((titleObj?["runs"] as? [[String: Any]])?.first?["text"] as? String)
            ?? ""
        if cid.isEmpty || title.isEmpty { return nil }

        var avatar = ((cr["thumbnail"] as? [String: Any])?["thumbnails"] as? [[String: Any]])?.last?["url"] as? String ?? ""
        if avatar.hasPrefix("//") { avatar = "https:\(avatar)" }
        let finalAvatar = avatar.isEmpty ? "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=150" : avatar

        let subText = ((cr["videoCountText"] as? [String: Any])?["simpleText"] as? String)
            ?? ((cr["subscriberCountText"] as? [String: Any])?["simpleText"] as? String)
            ?? "Channel"

        return VideoItem(
            id: "channel_\(cid)", title: title, channelName: title, channelAvatarUrl: finalAvatar,
            subscriberCount: subText, thumbnailUrl: finalAvatar, views: "", uploadTime: "",
            duration: "", description: "", likes: "", isMusicTrack: false,
            artist: title, album: "", lyrics: "", category: "Channel", channelId: cid
        )
    }

    static func parseVideoRenderer(_ vr: [String: Any]) -> VideoItem? {
        var vid = vr["videoId"] as? String ?? ""
        if vid.isEmpty { vid = (vr["watchEndpoint"] as? [String: Any])?["videoId"] as? String ?? "" }
        if vid.isEmpty { vid = ((vr["navigationEndpoint"] as? [String: Any])?["watchEndpoint"] as? [String: Any])?["videoId"] as? String ?? "" }
        if vid.isEmpty { vid = ((vr["navigationEndpoint"] as? [String: Any])?["reelWatchEndpoint"] as? [String: Any])?["videoId"] as? String ?? "" }
        if vid.isEmpty { vid = (((vr["onTap"] as? [String: Any])?["innertubeCommand"] as? [String: Any])?["reelWatchEndpoint"] as? [String: Any])?["videoId"] as? String ?? "" }
        if vid.isEmpty { return nil }

        let titleObj = vr["title"] as? [String: Any]
        let headlineObj = vr["headline"] as? [String: Any]
        let title = ((titleObj?["runs"] as? [[String: Any]])?.first?["text"] as? String)
            ?? (titleObj?["simpleText"] as? String)
            ?? ((headlineObj?["runs"] as? [[String: Any]])?.first?["text"] as? String)
            ?? (headlineObj?["simpleText"] as? String)
            ?? (((vr["overlayMetadata"] as? [String: Any])?["primaryText"] as? [String: Any])?["content"] as? String)
            ?? (vr["title"] as? String)
            ?? ""
        if title.isEmpty { return nil }

        let channel = (((vr["ownerText"] as? [String: Any])?["runs"] as? [[String: Any]])?.first?["text"] as? String)
            ?? (((vr["shortBylineText"] as? [String: Any])?["runs"] as? [[String: Any]])?.first?["text"] as? String)
            ?? (((vr["longBylineText"] as? [String: Any])?["runs"] as? [[String: Any]])?.first?["text"] as? String)
            ?? (((vr["bylineText"] as? [String: Any])?["runs"] as? [[String: Any]])?.first?["text"] as? String)
            ?? "YouTube Creator"

        let vc = vr["viewCountText"] as? [String: Any]
        let svc = vr["shortViewCountText"] as? [String: Any]
        let views = (vc?["simpleText"] as? String)
            ?? ((vc?["runs"] as? [[String: Any]])?.first?["text"] as? String)
            ?? (svc?["simpleText"] as? String)
            ?? ((svc?["runs"] as? [[String: Any]])?.first?["text"] as? String)
            ?? "1M views"

        let lt = vr["lengthText"] as? [String: Any]
        let duration = (lt?["simpleText"] as? String)
            ?? ((lt?["runs"] as? [[String: Any]])?.first?["text"] as? String)
            ?? "3:45"

        let uploadTime = (vr["publishedTimeText"] as? [String: Any])?["simpleText"] as? String ?? "Recently"

        let ctRenderer = (vr["channelThumbnailSupportedRenderers"] as? [String: Any])?["channelThumbnailWithLinkRenderer"] as? [String: Any]
        let ctThumbs = (ctRenderer?["thumbnail"] as? [String: Any])?["thumbnails"] as? [[String: Any]]
        let directThumbs = (vr["channelThumbnail"] as? [String: Any])?["thumbnails"] as? [[String: Any]]
        var avatar = (ctThumbs?.last?["url"] as? String) ?? (directThumbs?.last?["url"] as? String) ?? ""
        if avatar.hasPrefix("//") { avatar = "https:\(avatar)" }
        if avatar.isEmpty { avatar = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=150" }

        let ctBrowse = ((ctRenderer?["navigationEndpoint"] as? [String: Any])?["browseEndpoint"] as? [String: Any])?["browseId"] as? String
        let ownerRuns = (vr["ownerText"] as? [String: Any])?["runs"] as? [[String: Any]]
        let ownerBrowse = ((ownerRuns?.first?["navigationEndpoint"] as? [String: Any])?["browseEndpoint"] as? [String: Any])?["browseId"] as? String
        let cid = ctBrowse ?? ownerBrowse ?? ""

        var thumb = "https://img.youtube.com/vi/\(vid)/hqdefault.jpg"
        if let thumbs = (vr["thumbnail"] as? [String: Any])?["thumbnails"] as? [[String: Any]],
           let t = thumbs.last?["url"] as? String, !t.isEmpty {
            thumb = t.hasPrefix("//") ? "https:\(t)" : t
        }

        return VideoItem(
            id: vid, title: title, channelName: channel, channelAvatarUrl: avatar,
            subscriberCount: "Subscribers", thumbnailUrl: thumb, views: views,
            uploadTime: uploadTime, duration: duration, description: title, likes: "100K",
            isMusicTrack: title.localizedCaseInsensitiveContains("Music") || title.localizedCaseInsensitiveContains("Song") || title.localizedCaseInsensitiveContains("ft."),
            artist: channel, album: "Single", lyrics: "", category: "YouTube", channelId: cid
        )
    }

    static func parseMusicResponsiveListItemRenderer(_ item: [String: Any]) -> VideoItem? {
        var vid = (item["playlistItemData"] as? [String: Any])?["videoId"] as? String ?? ""
        let flexCols = item["flexColumns"] as? [[String: Any]] ?? []
        if vid.isEmpty, let firstCol = flexCols.first,
           let runs = ((firstCol["musicResponsiveListItemFlexColumnRenderer"] as? [String: Any])?["text"] as? [String: Any])?["runs"] as? [[String: Any]] {
            vid = ((runs.first?["navigationEndpoint"] as? [String: Any])?["watchEndpoint"] as? [String: Any])?["videoId"] as? String ?? ""
        }
        if vid.isEmpty {
            vid = ((((((item["overlay"] as? [String: Any])?["musicItemThumbnailOverlayRenderer"] as? [String: Any])?["content"] as? [String: Any])?["musicPlayButtonRenderer"] as? [String: Any])?["playNavigationEndpoint"] as? [String: Any])?["watchEndpoint"] as? [String: Any])?["videoId"] as? String ?? ""
        }
        if vid.isEmpty { return nil }

        var title = ""
        if let firstCol = flexCols.first,
           let runs = ((firstCol["musicResponsiveListItemFlexColumnRenderer"] as? [String: Any])?["text"] as? [String: Any])?["runs"] as? [[String: Any]] {
            title = (runs.first?["text"] as? String ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        }
        if title.isEmpty { return nil }

        var artist = "YouTube Music"
        var album = "Single"
        var views = "Music Track"
        if flexCols.count > 1,
           let runs = ((flexCols[1]["musicResponsiveListItemFlexColumnRenderer"] as? [String: Any])?["text"] as? [String: Any])?["runs"] as? [[String: Any]] {
            if let a = runs.first?["text"] as? String, !a.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                artist = a.trimmingCharacters(in: .whitespacesAndNewlines)
            }
            for r in runs.dropFirst() {
                if let t = r["text"] as? String, (t.localizedCaseInsensitiveContains("plays") || t.localizedCaseInsensitiveContains("views")) {
                    views = t.trimmingCharacters(in: .whitespacesAndNewlines)
                }
            }
        }
        if flexCols.count > 2,
           let runs = ((flexCols[2]["musicResponsiveListItemFlexColumnRenderer"] as? [String: Any])?["text"] as? [String: Any])?["runs"] as? [[String: Any]],
           let alb = runs.first?["text"] as? String, !alb.isEmpty {
            album = alb.trimmingCharacters(in: .whitespacesAndNewlines)
        }

        var thumb = "https://img.youtube.com/vi/\(vid)/hqdefault.jpg"
        if let thumbs = (((item["thumbnail"] as? [String: Any])?["musicThumbnailRenderer"] as? [String: Any])?["thumbnail"] as? [String: Any])?["thumbnails"] as? [[String: Any]],
           let u = thumbs.last?["url"] as? String, !u.isEmpty {
            thumb = u.hasPrefix("//") ? "https:\(u)" : u
        }

        return VideoItem(
            id: vid, title: title, channelName: artist, channelAvatarUrl: thumb,
            subscriberCount: "Music Track", thumbnailUrl: thumb, views: views,
            uploadTime: "YouTube Music", duration: "3:30", description: "\(title) - \(artist)",
            likes: "100K", isMusicTrack: true, artist: artist, album: album, lyrics: "", category: "Music"
        )
    }

    static func parseMusicTwoRowItemRenderer(_ item: [String: Any]) -> VideoItem? {
        var vid = ((item["navigationEndpoint"] as? [String: Any])?["watchEndpoint"] as? [String: Any])?["videoId"] as? String ?? ""
        if vid.isEmpty {
            vid = ((((((item["thumbnailOverlay"] as? [String: Any])?["musicItemThumbnailOverlayRenderer"] as? [String: Any])?["content"] as? [String: Any])?["musicPlayButtonRenderer"] as? [String: Any])?["playNavigationEndpoint"] as? [String: Any])?["watchEndpoint"] as? [String: Any])?["videoId"] as? String ?? ""
        }
        if vid.isEmpty { return nil }

        let title = (((item["title"] as? [String: Any])?["runs"] as? [[String: Any]])?.first?["text"] as? String ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        if title.isEmpty { return nil }

        var artist = "YouTube Music"
        if let subRuns = (item["subtitle"] as? [String: Any])?["runs"] as? [[String: Any]] {
            let parts = subRuns.compactMap { $0["text"] as? String }
                .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
                .filter { !$0.isEmpty && $0 != "•" && $0 != "," }
            if !parts.isEmpty { artist = parts.joined(separator: ", ") }
        }

        var thumb = "https://img.youtube.com/vi/\(vid)/hqdefault.jpg"
        if let thumbs = (((item["thumbnailRenderer"] as? [String: Any])?["musicThumbnailRenderer"] as? [String: Any])?["thumbnail"] as? [String: Any])?["thumbnails"] as? [[String: Any]],
           let u = thumbs.last?["url"] as? String, !u.isEmpty {
            thumb = u.hasPrefix("//") ? "https:\(u)" : u
        }

        return VideoItem(
            id: vid, title: title, channelName: artist, channelAvatarUrl: thumb,
            subscriberCount: "Music Track", thumbnailUrl: thumb, views: "Popular",
            uploadTime: "YouTube Music", duration: "3:45", description: "\(title) - \(artist)",
            likes: "50K", isMusicTrack: true, artist: artist, album: "YouTube Music", lyrics: "", category: "Music"
        )
    }

    static func extractFirstImageUrl(_ obj: Any?) -> String? {
        guard let obj = obj else { return nil }
        if let dict = obj as? [String: Any] {
            let sources = (dict["sources"] as? [[String: Any]]) ?? (dict["thumbnails"] as? [[String: Any]])
            if let last = sources?.last?["url"] as? String, !last.isEmpty {
                return last.hasPrefix("//") ? "https:\(last)" : last
            }
            for (_, v) in dict {
                if let res = extractFirstImageUrl(v) { return res }
            }
        } else if let arr = obj as? [Any] {
            for elem in arr {
                if let res = extractFirstImageUrl(elem) { return res }
            }
        }
        return nil
    }

    static func extractContinuationToken(_ obj: Any?) -> String? {
        guard let obj = obj else { return nil }
        if let dict = obj as? [String: Any] {
            if let ncd = dict["nextContinuationData"] as? [String: Any], let t = ncd["continuation"] as? String, !t.isEmpty { return t }
            if let rcd = dict["reloadContinuationData"] as? [String: Any], let t = rcd["continuation"] as? String, !t.isEmpty { return t }
            if let cc = dict["continuationCommand"] as? [String: Any], let t = cc["token"] as? String, !t.isEmpty { return t }
            if let cir = dict["continuationItemRenderer"] as? [String: Any], let ep = cir["continuationEndpoint"] as? [String: Any] {
                if let t = ((ep["continuationCommand"] as? [String: Any])?["token"] as? String)
                    ?? (ep["continuation"] as? String)
                    ?? ((ep["continuationCommand"] as? [String: Any])?["continuation"] as? String),
                   !t.isEmpty {
                    return t
                }
            }
            if let direct = dict["continuation"] as? String, direct.count > 20 { return direct }
            for (_, v) in dict {
                if let res = extractContinuationToken(v) { return res }
            }
        } else if let arr = obj as? [Any] {
            for elem in arr {
                if let res = extractContinuationToken(elem) { return res }
            }
        }
        return nil
    }

    static func extractPlaylistsRecursively(_ obj: Any, into outList: inout [UserPlaylist]) {
        if let dict = obj as? [String: Any] {
            let wrappers = ["lockupViewModel", "playlistRenderer", "gridPlaylistRenderer", "compactPlaylistRenderer", "radioRenderer", "guideEntryRenderer", "guideCollapsibleSectionEntryRenderer", "guideItemRenderer", "playlistCardViewModel", "musicTwoRowItemRenderer", "musicResponsiveListItemRenderer"]
            for w in wrappers {
                if let pObj = dict[w] as? [String: Any], let parsed = parseAnyPlaylistRenderer(pObj) {
                    outList.append(parsed)
                }
            }
            if let parsed = parseAnyPlaylistRenderer(dict) { outList.append(parsed) }
            for (_, v) in dict { extractPlaylistsRecursively(v, into: &outList) }
        } else if let arr = obj as? [Any] {
            for elem in arr { extractPlaylistsRecursively(elem, into: &outList) }
        }
    }

    private static func parseAnyPlaylistRenderer(_ obj: [String: Any]) -> UserPlaylist? {
        let contentType = obj["contentType"] as? String ?? ""
        if contentType == "LOCKUP_CONTENT_TYPE_VIDEO" || contentType == "LOCKUP_CONTENT_TYPE_SHORTS" { return nil }
        guard let plId = extractPlaylistIdFromObject(obj),
              plId != "WL" && plId != "LL" && plId != "history" && !plId.hasPrefix("UC") && !plId.hasPrefix("FE"),
              let title = extractTitleFromObject(obj) else { return nil }

        let cover = extractFirstImageUrl(obj["contentImage"]) ?? extractFirstImageUrl(obj)
        return UserPlaylist(
            id: plId,
            title: title,
            description: "Synced from YouTube",
            videos: [],
            isSystem: false,
            iconType: "playlist",
            customCoverUrl: cover,
            totalVideosCount: nil
        )
    }

    private static func extractPlaylistIdFromObject(_ obj: [String: Any]) -> String? {
        if let plId = obj["playlistId"] as? String, !plId.isEmpty, plId != "WL", plId != "LL", plId != "history" {
            return plId.hasPrefix("VL") ? String(plId.dropFirst(2)) : plId
        }
        if let cid = obj["contentId"] as? String {
            if cid.hasPrefix("PL") || cid.hasPrefix("RD") || cid.hasPrefix("FL") || cid.hasPrefix("UU") || cid.hasPrefix("OLAK") || cid.hasPrefix("TLGG") || cid.hasPrefix("CL") { return cid }
            if cid.hasPrefix("VL") { return String(cid.dropFirst(2)) }
        }

        func findInEndpoint(_ ep: [String: Any]?) -> String? {
            guard let ep = ep else { return nil }
            if let pid = (ep["watchEndpoint"] as? [String: Any])?["playlistId"] as? String, !pid.isEmpty, pid != "WL", pid != "LL" {
                return pid.hasPrefix("VL") ? String(pid.dropFirst(2)) : pid
            }
            if let bid = (ep["browseEndpoint"] as? [String: Any])?["browseId"] as? String, !bid.isEmpty {
                if bid.hasPrefix("VL") { return String(bid.dropFirst(2)) }
                if bid.hasPrefix("PL") || bid.hasPrefix("RD") || bid.hasPrefix("FL") || bid.hasPrefix("UU") || bid.hasPrefix("OLAK") { return bid }
            }
            return nil
        }

        if let r = findInEndpoint(obj["navigationEndpoint"] as? [String: Any]) { return r }
        if let r = findInEndpoint((obj["onTap"] as? [String: Any])?["innertubeCommand"] as? [String: Any]) { return r }
        if let r = findInEndpoint(obj["serviceEndpoint"] as? [String: Any]) { return r }
        return nil
    }

    private static func extractTitleFromObject(_ obj: [String: Any]) -> String? {
        if let t = obj["title"] as? String, !t.isEmpty, !t.hasPrefix("{") { return t }
        if let t = obj["title"] as? [String: Any] {
            if let r = (t["runs"] as? [[String: Any]])?.first?["text"] as? String, !r.isEmpty { return r }
            if let s = t["simpleText"] as? String, !s.isEmpty { return s }
            if let c = t["content"] as? String, !c.isEmpty { return c }
        }
        if let ft = obj["formattedTitle"] as? [String: Any] {
            if let r = (ft["runs"] as? [[String: Any]])?.first?["text"] as? String, !r.isEmpty { return r }
            if let s = ft["simpleText"] as? String, !s.isEmpty { return s }
        }
        if let lmm = (obj["metadata"] as? [String: Any])?["lockupMetadataViewModel"] as? [String: Any],
           let t = lmm["title"] as? [String: Any] {
            if let c = t["content"] as? String, !c.isEmpty { return c }
            if let r = (t["runs"] as? [[String: Any]])?.first?["text"] as? String, !r.isEmpty { return r }
        }
        return nil
    }

    static func extractSubscribedChannelsRecursively(_ obj: Any?, into outSet: inout Set<String>) {
        guard let obj = obj else { return }
        if let dict = obj as? [String: Any] {
            if let cr = dict["channelRenderer"] as? [String: Any],
               let t = ((cr["title"] as? [String: Any])?["simpleText"] as? String) ?? (((cr["title"] as? [String: Any])?["runs"] as? [[String: Any]])?.first?["text"] as? String),
               !t.isEmpty {
                outSet.insert(t)
            }
            for key in ["guideEntryRenderer", "guideItemRenderer"] {
                if let g = dict[key] as? [String: Any],
                   let bid = ((g["navigationEndpoint"] as? [String: Any])?["browseEndpoint"] as? [String: Any])?["browseId"] as? String,
                   bid.hasPrefix("UC") {
                    let t = (((g["formattedTitle"] as? [String: Any])?["runs"] as? [[String: Any]])?.first?["text"] as? String) ?? (g["title"] as? String)
                    if let t = t, !t.isEmpty { outSet.insert(t) }
                }
            }
            for (_, v) in dict { extractSubscribedChannelsRecursively(v, into: &outSet) }
        } else if let arr = obj as? [Any] {
            for elem in arr { extractSubscribedChannelsRecursively(elem, into: &outSet) }
        }
    }

    static func findChannelIdRecursively(_ obj: Any?) -> String? {
        guard let obj = obj else { return nil }
        if let dict = obj as? [String: Any] {
            if let cr = dict["channelRenderer"] as? [String: Any], let id = cr["channelId"] as? String, id.hasPrefix("UC") { return id }
            if let cid = dict["channelId"] as? String, cid.hasPrefix("UC") { return cid }
            if let bid = (dict["browseEndpoint"] as? [String: Any])?["browseId"] as? String, bid.hasPrefix("UC") { return bid }
            for (_, v) in dict {
                if let r = findChannelIdRecursively(v) { return r }
            }
        } else if let arr = obj as? [Any] {
            for elem in arr {
                if let r = findChannelIdRecursively(elem) { return r }
            }
        }
        return nil
    }

    static func distinctVideos(_ list: [VideoItem]) -> [VideoItem] {
        var seen = Set<String>()
        return list.filter { seen.insert($0.id).inserted }
    }

    static func distinctPlaylists(_ list: [UserPlaylist]) -> [UserPlaylist] {
        var seen = Set<String>()
        return list.filter { seen.insert($0.id).inserted }
    }
}

// MARK: - Spotify & Amazon Music Import Engine

struct ImportTrackMeta: Sendable {
    let title: String
    let artist: String
    var album: String = ""
}

struct ImportProgress: Sendable {
    let stage: String
    let current: Int
    let total: Int
    var currentTrackName: String = ""
    var isComplete: Bool = false
    var error: String? = nil
}

struct RemotePlaylistSummary: Identifiable, Codable {
    let id: String
    let title: String
    let description: String
    let trackCount: Int
    let coverUrl: String?
    var sourceProvider: String = "Spotify"
}

final class SpotifyImportEngine {
    static let shared = SpotifyImportEngine()
    private init() {
        let token = UserDefaults.standard.string(forKey: "spotify_oauth_token")
        self.isConnected = token != nil && !token!.isEmpty
        self.userDisplayName = UserDefaults.standard.string(forKey: "spotify_oauth_name")
    }

    var isConnected: Bool = false
    var userDisplayName: String? = nil
    var userPlaylists: [RemotePlaylistSummary] = []

    func getAccessToken() -> String? {
        UserDefaults.standard.string(forKey: "spotify_oauth_token")
    }

    func createAuthUrl() -> String {
        let clientId = "271077752670460c918ef93949f506e7"
        let redirectUri = "https://open.spotify.com/"
        let scopes = "playlist-read-private%20playlist-read-collaborative%20user-library-read"
        return "https://accounts.spotify.com/authorize?client_id=\(clientId)&response_type=token&redirect_uri=\(redirectUri)&scope=\(scopes)&show_dialog=true"
    }

    func handleAuthCallback(url: String) -> Bool {
        if url.contains("#access_token=") || url.contains("&access_token=") {
            let marker = url.contains("#access_token=") ? "#access_token=" : "&access_token="
            let after = url.components(separatedBy: marker).last ?? ""
            let token = after.components(separatedBy: "&").first ?? ""
            if !token.isEmpty {
                UserDefaults.standard.set(token, forKey: "spotify_oauth_token")
                self.isConnected = true
                return true
            }
        }
        return false
    }

    func disconnect() {
        UserDefaults.standard.removeObject(forKey: "spotify_oauth_token")
        UserDefaults.standard.removeObject(forKey: "spotify_oauth_name")
        self.isConnected = false
        self.userDisplayName = nil
        self.userPlaylists = []
    }

    func fetchUserProfile() async -> String? {
        guard let token = getAccessToken(), let url = URL(string: "https://api.spotify.com/v1/me") else { return nil }
        var req = URLRequest(url: url)
        req.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        if let (data, _) = try? await URLSession.shared.data(for: req),
           let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
           let name = json["display_name"] as? String {
            self.userDisplayName = name
            UserDefaults.standard.set(name, forKey: "spotify_oauth_name")
            return name
        }
        return nil
    }

    func fetchUserPlaylists() async -> [RemotePlaylistSummary] {
        guard let token = getAccessToken() else { return [] }
        var list: [RemotePlaylistSummary] = []
        var nextUrlStr: String? = "https://api.spotify.com/v1/me/playlists?limit=50"

        while let urlStr = nextUrlStr, let url = URL(string: urlStr) {
            var req = URLRequest(url: url)
            req.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
            guard let (data, _) = try? await URLSession.shared.data(for: req),
                  let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                  let items = json["items"] as? [[String: Any]] else { break }

            for item in items {
                guard let id = item["id"] as? String,
                      let title = item["name"] as? String else { continue }
                let desc = (item["description"] as? String) ?? ""
                let tracksObj = item["tracks"] as? [String: Any]
                let count = (tracksObj?["total"] as? Int) ?? 0
                var coverUrl: String? = nil
                if let images = item["images"] as? [[String: Any]], let first = images.first {
                    coverUrl = first["url"] as? String
                }
                list.append(RemotePlaylistSummary(id: id, title: title, description: desc, trackCount: count, coverUrl: coverUrl, sourceProvider: "Spotify"))
            }

            nextUrlStr = json["next"] as? String
        }

        self.userPlaylists = list
        return list
    }

    func fetchPlaylistTracks(playlistId: String) async -> [ImportTrackMeta] {
        guard let token = getAccessToken() else { return [] }
        var tracks: [ImportTrackMeta] = []
        var nextUrlStr: String? = "https://api.spotify.com/v1/playlists/\(playlistId)/tracks?limit=50"

        while let urlStr = nextUrlStr, let url = URL(string: urlStr) {
            var req = URLRequest(url: url)
            req.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
            guard let (data, _) = try? await URLSession.shared.data(for: req),
                  let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                  let items = json["items"] as? [[String: Any]] else { break }

            for item in items {
                guard let track = item["track"] as? [String: Any],
                      let name = track["name"] as? String else { continue }
                var artists: [String] = []
                if let artistsArr = track["artists"] as? [[String: Any]] {
                    for a in artistsArr {
                        if let n = a["name"] as? String { artists.append(n) }
                    }
                }
                let artistStr = artists.joined(separator: ", ")
                let albumObj = track["album"] as? [String: Any]
                let albumName = (albumObj?["name"] as? String) ?? ""
                tracks.append(ImportTrackMeta(title: name, artist: artistStr, album: albumName))
            }

            nextUrlStr = json["next"] as? String
        }

        return tracks
    }

    func importRemotePlaylist(
        summary: RemotePlaylistSummary,
        onProgress: @escaping (ImportProgress) -> Void
    ) async -> Result<UserPlaylist, Error> {
        onProgress(ImportProgress(stage: "Fetching tracks from \(summary.sourceProvider)...", current: 0, total: summary.trackCount))
        let tracks = await fetchPlaylistTracks(playlistId: summary.id)
        guard !tracks.isEmpty else {
            return .failure(NSError(domain: "SpotifyImport", code: 404, userInfo: [NSLocalizedDescriptionKey: "No tracks found in playlist"]))
        }

        let total = tracks.count
        onProgress(ImportProgress(stage: "Matching tracks with YouTube Music...", current: 0, total: total))

        var matchedVideos: [VideoItem] = []
        for (idx, meta) in tracks.enumerated() {
            let query = meta.artist.isEmpty ? meta.title : "\(meta.title) \(meta.artist)"
            var results = await YouTubeSearchEngine.searchVideos(query: query, isMusicOnly: true)
            if results.isEmpty && !meta.artist.isEmpty {
                results = await YouTubeSearchEngine.searchVideos(query: meta.title, isMusicOnly: true)
            }
            if let first = results.first {
                matchedVideos.append(first)
            }
            onProgress(
                ImportProgress(
                    stage: "Matching tracks...",
                    current: idx + 1,
                    total: total,
                    currentTrackName: "\(meta.title) • \(meta.artist)"
                )
            )
        }

        guard !matchedVideos.isEmpty else {
            return .failure(NSError(domain: "SpotifyImport", code: 404, userInfo: [NSLocalizedDescriptionKey: "Could not find matching audio on YouTube Music"]))
        }

        let finalPlaylist = UserPlaylist(
            id: UUID().uuidString,
            title: summary.title.isEmpty ? "Imported Playlist" : summary.title,
            description: "Imported from \(summary.sourceProvider) (\(matchedVideos.count) tracks)",
            videos: matchedVideos,
            isSystem: false,
            iconType: "playlist",
            customCoverUrl: summary.coverUrl ?? matchedVideos.first?.thumbnailUrl
        )

        await MainActor.run {
            UserDataRepository.shared.playlists.append(finalPlaylist)
            UserDataRepository.shared.savePlaylists()
        }

        onProgress(ImportProgress(stage: "Complete!", current: total, total: total, isComplete: true))
        return .success(finalPlaylist)
    }

    func importPlaylist(
        from urlOrText: String,
        onProgress: @escaping (ImportProgress) -> Void
    ) async -> Result<UserPlaylist, Error> {
        let trimmed = urlOrText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else {
            return .failure(NSError(domain: "SpotifyImport", code: 400, userInfo: [NSLocalizedDescriptionKey: "Input link or text cannot be empty"]))
        }

        onProgress(ImportProgress(stage: "Analyzing link...", current: 0, total: 0))

        let parsed: (title: String, tracks: [ImportTrackMeta])
        if trimmed.hasPrefix("{") && trimmed.contains("\"tracks\"") {
            parsed = parseDomTracksJson(jsonStr: trimmed)
        } else if trimmed.contains("spotify.com") {
            parsed = await parseSpotify(url: trimmed)
        } else if trimmed.contains("music.amazon.") || (trimmed.contains("amazon.") && trimmed.contains("music")) || trimmed.contains("amzn.to") {
            parsed = await parseAmazonMusic(url: trimmed)
        } else if trimmed.contains("music.apple.com") || trimmed.contains("apple.com") {
            parsed = await parseAppleMusic(url: trimmed)
        } else {
            parsed = parsePlainText(trimmed)
        }

        guard !parsed.tracks.isEmpty else {
            return .failure(NSError(domain: "SpotifyImport", code: 404, userInfo: [NSLocalizedDescriptionKey: "No tracks found from link"]))
        }

        let total = parsed.tracks.count
        onProgress(ImportProgress(stage: "Matching tracks with YouTube Music...", current: 0, total: total))

        var matchedVideos: [VideoItem] = []

        for (idx, meta) in parsed.tracks.enumerated() {
            let query = meta.artist.isEmpty ? meta.title : "\(meta.title) \(meta.artist)"
            var results = await YouTubeSearchEngine.searchVideos(query: query, isMusicOnly: true)
            if results.isEmpty && !meta.artist.isEmpty {
                results = await YouTubeSearchEngine.searchVideos(query: meta.title, isMusicOnly: true)
            }
            if let first = results.first {
                matchedVideos.append(first)
            }
            onProgress(
                ImportProgress(
                    stage: "Matching tracks...",
                    current: idx + 1,
                    total: total,
                    currentTrackName: "\(meta.title) • \(meta.artist)"
                )
            )
        }

        guard !matchedVideos.isEmpty else {
            return .failure(NSError(domain: "SpotifyImport", code: 404, userInfo: [NSLocalizedDescriptionKey: "Could not find matching audio on YouTube Music"]))
        }

        let finalPlaylist = UserPlaylist(
            id: UUID().uuidString,
            title: parsed.title.isEmpty ? "Imported Playlist" : parsed.title,
            description: "Imported into AURALIX (\(matchedVideos.count) tracks)",
            videos: matchedVideos,
            isSystem: false,
            iconType: "playlist",
            customCoverUrl: matchedVideos.first?.thumbnailUrl
        )

        // Save into UserDataRepository
        await MainActor.run {
            UserDataRepository.shared.playlists.append(finalPlaylist)
            UserDataRepository.shared.savePlaylists()
        }

        onProgress(ImportProgress(stage: "Complete!", current: total, total: total, isComplete: true))
        return .success(finalPlaylist)
    }

    private func parseSpotify(url: String) async -> (title: String, tracks: [ImportTrackMeta]) {
        let cleanUrl = url.components(separatedBy: "?").first ?? url
        let isTrack = cleanUrl.contains("/track/")
        let isAlbum = cleanUrl.contains("/album/")
        let isPlaylist = cleanUrl.contains("/playlist/") || (!isTrack && !isAlbum)

        var spotifyId = ""
        if let regex = try? NSRegularExpression(pattern: "(?:playlist|album|track)/([a-zA-Z0-9]+)"),
           let match = regex.firstMatch(in: url, range: NSRange(location: 0, length: (url as NSString).length)),
           match.numberOfRanges > 1 {
            spotifyId = (url as NSString).substring(with: match.range(at: 1))
        } else {
            spotifyId = cleanUrl.trimmingCharacters(in: CharacterSet(charactersIn: "/")).components(separatedBy: "/").last ?? ""
        }

        if isTrack {
            if let enc = "https://open.spotify.com/track/\(spotifyId)".addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed),
               let oembedUrl = URL(string: "https://open.spotify.com/oembed?url=\(enc)"),
               let (data, _) = try? await URLSession.shared.data(from: oembedUrl),
               let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] {
                let title = (json["title"] as? String) ?? "Spotify Track"
                let author = (json["author_name"] as? String) ?? ""
                return (title, [ImportTrackMeta(title: title, artist: author)])
            }
        }

        let embedUrlString = isAlbum ? "https://open.spotify.com/embed/album/\(spotifyId)" : "https://open.spotify.com/embed/playlist/\(spotifyId)"
        guard let embedUrl = URL(string: embedUrlString) else { return ("Spotify Playlist", []) }

        var req = URLRequest(url: embedUrl)
        req.setValue("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36", forHTTPHeaderField: "User-Agent")
        req.setValue("text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8", forHTTPHeaderField: "Accept")
        req.setValue("en-US,en;q=0.9", forHTTPHeaderField: "Accept-Language")

        guard let (data, _) = try? await URLSession.shared.data(for: req),
              let html = String(data: data, encoding: .utf8) else {
            return ("Spotify Playlist", [])
        }

        var playlistTitle = "Spotify Playlist"
        var tracks: [ImportTrackMeta] = []

        // Extract __NEXT_DATA__
        if let startRange = html.range(of: "<script id=\"__NEXT_DATA__\""),
           let tagClose = html[startRange.lowerBound...].range(of: ">"),
           let endRange = html[tagClose.upperBound...].range(of: "</script>") {
            let jsonString = String(html[tagClose.upperBound..<endRange.lowerBound])
            if let jsonData = jsonString.data(using: .utf8),
               let root = try? JSONSerialization.jsonObject(with: jsonData) as? [String: Any],
               let props = root["props"] as? [String: Any],
               let pageProps = props["pageProps"] as? [String: Any],
               let state = pageProps["state"] as? [String: Any],
               let dataObj = state["data"] as? [String: Any],
               let entity = dataObj["entity"] as? [String: Any] {
                if let name = entity["name"] as? String, !name.isEmpty {
                    playlistTitle = name
                }
                if let trackList = entity["trackList"] as? [[String: Any]] {
                    for item in trackList {
                        let t = (item["title"] as? String) ?? ""
                        let s = (item["subtitle"] as? String) ?? ""
                        if !t.isEmpty {
                            tracks.append(ImportTrackMeta(title: t, artist: s))
                        }
                    }
                }
            }
        }

        // Fallback: oEmbed for playlist title
        if tracks.isEmpty {
            if let enc = "https://open.spotify.com/playlist/\(spotifyId)".addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed),
               let oembedUrl = URL(string: "https://open.spotify.com/oembed?url=\(enc)"),
               let (data, _) = try? await URLSession.shared.data(from: oembedUrl),
               let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
               let t = json["title"] as? String, !t.isEmpty {
                playlistTitle = t
            }
        }

        // Fallback: regex for title / subtitle
        if tracks.isEmpty {
            let pattern = "\"title\":\"([^\"]+)\"[^}]*?\"subtitle\":\"([^\"]+)\""
            if let regex = try? NSRegularExpression(pattern: pattern) {
                let nsHtml = html as NSString
                let matches = regex.matches(in: html, range: NSRange(location: 0, length: nsHtml.length))
                for m in matches {
                    if m.numberOfRanges > 2 {
                        let t = nsHtml.substring(with: m.range(at: 1))
                        let s = nsHtml.substring(with: m.range(at: 2))
                        if !t.isEmpty && !t.contains("Spotify") {
                            tracks.append(ImportTrackMeta(title: t, artist: s))
                        }
                    }
                }
            }
        }

        return (playlistTitle, tracks)
    }

    private func parseAmazonMusic(url: String) async -> (title: String, tracks: [ImportTrackMeta]) {
        guard let u = URL(string: url) else { return ("Amazon Music Playlist", []) }
        var req = URLRequest(url: u)
        req.setValue("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36", forHTTPHeaderField: "User-Agent")
        req.setValue("text/html,application/json,*/*", forHTTPHeaderField: "Accept")
        req.setValue("en-US,en;q=0.9", forHTTPHeaderField: "Accept-Language")

        guard let (data, _) = try? await URLSession.shared.data(for: req),
              let html = String(data: data, encoding: .utf8) else {
            return ("Amazon Music Playlist", [])
        }

        var title = "Amazon Music Playlist"
        if let tStart = html.range(of: "<title>", options: .caseInsensitive),
           let tEnd = html[tStart.upperBound...].range(of: "</title>", options: .caseInsensitive) {
            let rawTitle = String(html[tStart.upperBound..<tEnd.lowerBound])
            title = rawTitle
                .replacingOccurrences(of: "on Amazon Music", with: "", options: .caseInsensitive)
                .replacingOccurrences(of: "Amazon.com: ", with: "", options: .caseInsensitive)
                .replacingOccurrences(of: "Amazon Music: ", with: "", options: .caseInsensitive)
                .replacingOccurrences(of: "Amazon Music", with: "", options: .caseInsensitive)
                .trimmingCharacters(in: .whitespacesAndNewlines)
        }

        var tracks: [ImportTrackMeta] = []

        // 1. Check for JSON-LD schema scripts
        let jsonLdPattern = "<script[^>]*type=[\"']application/ld\\+json[\"'][^>]*>(.*?)</script>"
        if let jsonLdRegex = try? NSRegularExpression(pattern: jsonLdPattern, options: [.dotMatchesLineSeparators, .caseInsensitive]) {
            let nsHtml = html as NSString
            let matches = jsonLdRegex.matches(in: html, range: NSRange(location: 0, length: nsHtml.length))
            for m in matches {
                guard m.numberOfRanges > 1 else { continue }
                let jsonStr = nsHtml.substring(with: m.range(at: 1)).trimmingCharacters(in: .whitespacesAndNewlines)
                if let jData = jsonStr.data(using: .utf8),
                   let root = try? JSONSerialization.jsonObject(with: jData) as? [String: Any] {
                    if let pName = root["name"] as? String, !pName.isEmpty, (title == "Amazon Music Playlist" || title.isEmpty) {
                        title = pName
                    }
                    if let trackArr = root["track"] as? [[String: Any]] {
                        for item in trackArr {
                            let tName = (item["name"] as? String) ?? ""
                            var aName = ""
                            if let byArtist = item["byArtist"] as? [String: Any] {
                                aName = (byArtist["name"] as? String) ?? ""
                            } else if let aStr = item["artist"] as? String {
                                aName = aStr
                            }
                            if !tName.isEmpty {
                                tracks.append(ImportTrackMeta(title: tName, artist: aName))
                            }
                        }
                    }
                    if let itemArr = root["itemListElement"] as? [[String: Any]] {
                        for elem in itemArr {
                            let item = (elem["item"] as? [String: Any]) ?? elem
                            let tName = (item["name"] as? String) ?? ""
                            var aName = ""
                            if let byArtist = item["byArtist"] as? [String: Any] {
                                aName = (byArtist["name"] as? String) ?? ""
                            }
                            if !tName.isEmpty {
                                tracks.append(ImportTrackMeta(title: tName, artist: aName))
                            }
                        }
                    }
                }
            }
        }

        // 2. Parse Amazon Web Client embedded track JSON blobs (primaryText & secondaryText)
        if tracks.isEmpty {
            let ptPattern = "\"primaryText\"\\s*:\\s*\"([^\"]+)\"[^}]*?\"secondaryText(?:1)?\"\\s*:\\s*\"([^\"]+)\""
            if let ptRegex = try? NSRegularExpression(pattern: ptPattern, options: []) {
                let nsHtml = html as NSString
                let matches = ptRegex.matches(in: html, range: NSRange(location: 0, length: nsHtml.length))
                for m in matches {
                    if m.numberOfRanges > 2 {
                        let t = nsHtml.substring(with: m.range(at: 1)).trimmingCharacters(in: .whitespacesAndNewlines)
                        let a = nsHtml.substring(with: m.range(at: 2)).trimmingCharacters(in: .whitespacesAndNewlines)
                        if !t.isEmpty && !t.lowercased().contains("amazon music") && !t.lowercased().contains("library") {
                            tracks.append(ImportTrackMeta(title: t, artist: a))
                        }
                    }
                }
            }
        }

        // 3. Alternative Amazon Music track/artist JSON fields ("title" & "artistName")
        if tracks.isEmpty {
            let taPattern = "\"title\"\\s*:\\s*\"([^\"]+)\"[^}]*?\"artistName\"\\s*:\\s*\"([^\"]+)\""
            if let taRegex = try? NSRegularExpression(pattern: taPattern, options: []) {
                let nsHtml = html as NSString
                let matches = taRegex.matches(in: html, range: NSRange(location: 0, length: nsHtml.length))
                for m in matches {
                    if m.numberOfRanges > 2 {
                        let t = nsHtml.substring(with: m.range(at: 1)).trimmingCharacters(in: .whitespacesAndNewlines)
                        let a = nsHtml.substring(with: m.range(at: 2)).trimmingCharacters(in: .whitespacesAndNewlines)
                        if !t.isEmpty && !t.contains("Amazon") {
                            tracks.append(ImportTrackMeta(title: t, artist: a))
                        }
                    }
                }
            }
        }

        // 4. OpenGraph meta property="music:song" fallback
        if tracks.isEmpty {
            let pattern = "meta property=\"music:song\" content=\"([^\"]+)\""
            if let regex = try? NSRegularExpression(pattern: pattern) {
                let nsString = html as NSString
                let results = regex.matches(in: html, range: NSRange(location: 0, length: nsString.length))
                for match in results {
                    if match.numberOfRanges > 1 {
                        let songUrl = nsString.substring(with: match.range(at: 1))
                        let songName = (songUrl.components(separatedBy: "/").last ?? "").replacingOccurrences(of: "-", with: " ")
                        if !songName.isEmpty {
                            tracks.append(ImportTrackMeta(title: songName, artist: ""))
                        }
                    }
                }
            }
        }

        // 5. Intelligent URL slug & title fallback
        if tracks.isEmpty {
            let pathPart = url.components(separatedBy: "?").first ?? url
            let lastSegment = pathPart.components(separatedBy: "/").last ?? ""
            let cleanSlug = lastSegment
                .replacingOccurrences(of: "^B0[A-Z0-9]+-?", with: "", options: .regularExpression)
                .replacingOccurrences(of: "-", with: " ")
                .replacingOccurrences(of: "_", with: " ")
                .trimmingCharacters(in: .whitespacesAndNewlines)

            let candidateTitle: String
            if title != "Amazon Music Playlist" && !title.isEmpty && !title.lowercased().contains("amazon music") {
                candidateTitle = title
            } else if !cleanSlug.isEmpty && cleanSlug.count > 2 && !cleanSlug.hasPrefix("B0") {
                candidateTitle = cleanSlug
            } else {
                candidateTitle = ""
            }

            if !candidateTitle.isEmpty {
                title = candidateTitle
                let searchItems = await YouTubeSearchEngine.searchVideos(query: "\(candidateTitle) songs", isMusicOnly: true)
                for item in searchItems.prefix(25) {
                    tracks.append(ImportTrackMeta(title: item.title, artist: item.artist.isEmpty ? item.channelName : item.artist))
                }
            }
        }

        // Deduplicate tracks while preserving order
        var uniqueTracks: [ImportTrackMeta] = []
        var seen = Set<String>()
        for tr in tracks {
            let key = "\(tr.title.lowercased().trimmingCharacters(in: .whitespaces))_\(tr.artist.lowercased().trimmingCharacters(in: .whitespaces))"
            if !seen.contains(key) {
                seen.insert(key)
                uniqueTracks.append(tr)
            }
        }

        let filteredTracks = uniqueTracks.filter { tr in
            let lower = tr.title.lowercased()
            return !lower.contains("how to make") &&
                   !lower.contains("amazon music help") &&
                   !lower.contains("customer service") &&
                   !lower.contains("sign in") &&
                   !lower.contains("log in") &&
                   lower != "library" &&
                   lower != "browse" &&
                   lower != "home"
        }

        return (title, filteredTracks)
    }

    private func parseDomTracksJson(jsonStr: String) -> (title: String, tracks: [ImportTrackMeta]) {
        guard let data = jsonStr.data(using: .utf8),
              let dict = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any] else {
            return ("Imported Playlist", [])
        }
        let title = (dict["title"] as? String) ?? "Imported Playlist"
        let tracksArr = (dict["tracks"] as? [String]) ?? []
        var list: [ImportTrackMeta] = []
        for line in tracksArr {
            let trimmed = line.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !trimmed.isEmpty else { continue }
            let parts = trimmed.components(separatedBy: " - ")
            let t = parts[0].trimmingCharacters(in: .whitespacesAndNewlines)
            let a = parts.count > 1 ? parts[1].trimmingCharacters(in: .whitespacesAndNewlines) : ""
            let lower = t.lowercased()
            if !lower.contains("how to make") && !lower.contains("amazon music help") && lower != "library" && lower != "browse" {
                list.append(ImportTrackMeta(title: t, artist: a))
            }
        }
        return (title, list)
    }

    private func parseAppleMusic(url: String) async -> (title: String, tracks: [ImportTrackMeta]) {
        guard let requestUrl = URL(string: url) else { return ("Apple Music Playlist", []) }
        var req = URLRequest(url: requestUrl)
        req.setValue("Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1", forHTTPHeaderField: "User-Agent")
        req.setValue("text/html,application/xhtml+xml,application/json", forHTTPHeaderField: "Accept")

        guard let (data, _) = try? await URLSession.shared.data(for: req),
              let html = String(data: data, encoding: .utf8) else {
            return ("Apple Music Playlist", [])
        }

        var title = "Apple Music Playlist"
        if let titleRegex = try? NSRegularExpression(pattern: "<title>([^<]+)</title>", options: .caseInsensitive),
           let match = titleRegex.firstMatch(in: html, range: NSRange(location: 0, length: (html as NSString).length)),
           match.numberOfRanges > 1 {
            let fullTitle = (html as NSString).substring(with: match.range(at: 1))
            title = fullTitle
                .replacingOccurrences(of: "on Apple Music", with: "")
                .replacingOccurrences(of: "Apple Music", with: "")
                .replacingOccurrences(of: " - Playlist - ", with: " - ")
                .trimmingCharacters(in: .whitespacesAndNewlines)
        }

        var tracks: [ImportTrackMeta] = []

        // 1. JSON-LD scripts
        let jsonLdPattern = "<script[^>]*type=[\"']application/ld\\+json[\"'][^>]*>(.*?)</script>"
        if let jsonLdRegex = try? NSRegularExpression(pattern: jsonLdPattern, options: [.dotMatchesLineSeparators, .caseInsensitive]) {
            let nsHtml = html as NSString
            let matches = jsonLdRegex.matches(in: html, range: NSRange(location: 0, length: nsHtml.length))
            for m in matches {
                guard m.numberOfRanges > 1 else { continue }
                let jsonStr = nsHtml.substring(with: m.range(at: 1)).trimmingCharacters(in: .whitespacesAndNewlines)
                guard let jsonData = jsonStr.data(using: .utf8),
                      let jsonObj = (try? JSONSerialization.jsonObject(with: jsonData)) as? [String: Any] else {
                    continue
                }

                if let pName = jsonObj["name"] as? String, !pName.isEmpty, (title == "Apple Music Playlist" || title.isEmpty) {
                    title = pName
                }

                if let trackArr = (jsonObj["track"] as? [[String: Any]]) ?? (jsonObj["tracks"] as? [[String: Any]]) {
                    for item in trackArr {
                        let tName = (item["name"] as? String) ?? ""
                        let artistObj = item["byArtist"] as? [String: Any]
                        let aName = (artistObj?["name"] as? String) ?? (item["artist"] as? String) ?? ""
                        if !tName.isEmpty {
                            tracks.append(ImportTrackMeta(title: tName, artist: aName))
                        }
                    }
                }

                if let itemArr = jsonObj["itemListElement"] as? [[String: Any]] {
                    for item in itemArr {
                        let inner = (item["item"] as? [String: Any]) ?? item
                        let tName = (inner["name"] as? String) ?? ""
                        let artistObj = inner["byArtist"] as? [String: Any]
                        let aName = (artistObj?["name"] as? String) ?? (inner["artist"] as? String) ?? ""
                        if !tName.isEmpty {
                            tracks.append(ImportTrackMeta(title: tName, artist: aName))
                        }
                    }
                }
            }
        }

        // 2. Fallback regex
        if tracks.isEmpty {
            let pattern = "\"name\":\"([^\"]+)\"[^}]*?\"byArtist\":\\{\"@type\":\"MusicGroup\",\"name\":\"([^\"]+)\"\\}"
            if let regex = try? NSRegularExpression(pattern: pattern, options: []) {
                let nsHtml = html as NSString
                let matches = regex.matches(in: html, range: NSRange(location: 0, length: nsHtml.length))
                for m in matches {
                    if m.numberOfRanges > 2 {
                        let t = nsHtml.substring(with: m.range(at: 1)).trimmingCharacters(in: .whitespacesAndNewlines)
                        let a = nsHtml.substring(with: m.range(at: 2)).trimmingCharacters(in: .whitespacesAndNewlines)
                        if !t.isEmpty && !t.contains("Apple Music") {
                            tracks.append(ImportTrackMeta(title: t, artist: a))
                        }
                    }
                }
            }
        }

        // Deduplicate tracks
        var uniqueTracks: [ImportTrackMeta] = []
        var seen = Set<String>()
        for tr in tracks {
            let key = "\(tr.title.lowercased().trimmingCharacters(in: .whitespaces))_\(tr.artist.lowercased().trimmingCharacters(in: .whitespaces))"
            if !seen.contains(key) {
                seen.insert(key)
                uniqueTracks.append(tr)
            }
        }

        return (title, uniqueTracks)
    }

    private func parsePlainText(_ text: String) -> (title: String, tracks: [ImportTrackMeta]) {
        let lines = text.components(separatedBy: .newlines).map { $0.trimmingCharacters(in: .whitespaces) }.filter { !$0.isEmpty }
        var tracks: [ImportTrackMeta] = []

        for line in lines {
            // Remove leading track numbers e.g. "1.", "01 -", "(1)"
            var cleaned = line.replacingOccurrences(of: "^\\s*\\(?\\d+[\\.\\)\\-]\\s*", with: "", options: .regularExpression).trimmingCharacters(in: .whitespaces)
            // Remove trailing timestamp/duration like "(03:45)" or "3:45"
            cleaned = cleaned.replacingOccurrences(of: "\\s*\\(?\\d{1,2}:\\d{2}\\)?$", with: "", options: .regularExpression).trimmingCharacters(in: .whitespaces)

            // Skip table header lines
            let lower = cleaned.lowercased()
            if lower == "title" || lower == "track" || lower == "song" || lower.hasPrefix("track title") {
                continue
            }

            let parts: [String]
            if cleaned.contains("\t") {
                parts = cleaned.components(separatedBy: "\t")
            } else if cleaned.contains(" - ") {
                parts = cleaned.components(separatedBy: " - ")
            } else if cleaned.contains(" – ") {
                parts = cleaned.components(separatedBy: " – ")
            } else if cleaned.contains(" — ") {
                parts = cleaned.components(separatedBy: " — ")
            } else if cleaned.contains(" by ") {
                parts = cleaned.components(separatedBy: " by ")
            } else if cleaned.contains(" | ") {
                parts = cleaned.components(separatedBy: " | ")
            } else {
                parts = [cleaned]
            }

            if parts.count >= 2 {
                let t = parts[0].trimmingCharacters(in: .whitespaces).trimmingCharacters(in: CharacterSet(charactersIn: "\""))
                let a = parts[1].trimmingCharacters(in: .whitespaces).trimmingCharacters(in: CharacterSet(charactersIn: "\""))
                if !t.isEmpty {
                    tracks.append(ImportTrackMeta(title: t, artist: a))
                }
            } else if !cleaned.isEmpty {
                tracks.append(ImportTrackMeta(title: cleaned.trimmingCharacters(in: CharacterSet(charactersIn: "\"")), artist: ""))
            }
        }

        return ("Imported Custom Playlist", tracks)
    }
}
