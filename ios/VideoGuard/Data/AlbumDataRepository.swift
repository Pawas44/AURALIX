import Foundation

final class AlbumDataRepository {
    static let shared = AlbumDataRepository()

    static let curatedReleases: [AlbumItem] = [
        AlbumItem(
            id: "album_glory",
            title: "GLORY",
            artist: "Yo Yo Honey Singh",
            coverUrl: "https://i.scdn.co/image/ab67616d0000b273760bc21008f1b621e25e4f45",
            year: "2024",
            trackCount: 18,
            releaseType: "Album",
            isCurated: true
        ),
        AlbumItem(
            id: "album_moosetape",
            title: "Moosetape",
            artist: "Sidhu Moose Wala",
            coverUrl: "https://i.scdn.co/image/ab67616d0000b273e34b9d0738e44fa789b7eb50",
            year: "2021",
            trackCount: 32,
            releaseType: "Mixtape",
            isCurated: true
        ),
        AlbumItem(
            id: "album_making_memories",
            title: "Making Memories",
            artist: "Karan Aujla, Ikky",
            coverUrl: "https://i.scdn.co/image/ab67616d0000b273bc3674681329b3f9ff43e2f9",
            year: "2023",
            trackCount: 9,
            releaseType: "Album",
            isCurated: true
        ),
        AlbumItem(
            id: "album_four_you",
            title: "Four You",
            artist: "Karan Aujla",
            coverUrl: "https://i.scdn.co/image/ab67616d0000b273752e5057b2cb7980ffca3260",
            year: "2023",
            trackCount: 4,
            releaseType: "EP",
            isCurated: true
        ),
        AlbumItem(
            id: "album_still_here",
            title: "Still Here",
            artist: "KR$NA",
            coverUrl: "https://i.scdn.co/image/ab67616d0000b27393d6d539828d54157e841261",
            year: "2021",
            trackCount: 9,
            releaseType: "Album",
            isCurated: true
        ),
        AlbumItem(
            id: "album_hard_drive",
            title: "Hard Drive Vol. 1",
            artist: "Raftaar",
            coverUrl: "https://i.scdn.co/image/ab67616d0000b2733cba73be6be9bfec6f642646",
            year: "2022",
            trackCount: 7,
            releaseType: "Mixtape",
            isCurated: true
        ),
        AlbumItem(
            id: "album_rockstar",
            title: "Rockstar",
            artist: "A. R. Rahman, Mohit Chauhan",
            coverUrl: "https://i.scdn.co/image/ab67616d0000b273a7f80db27d5320c0f86eeff0",
            year: "2011",
            trackCount: 14,
            releaseType: "Album",
            isCurated: true
        ),
        AlbumItem(
            id: "album_animal",
            title: "Animal",
            artist: "Pritam, Vishal Mishra, Manan",
            coverUrl: "https://i.scdn.co/image/ab67616d0000b27318ff24f5a34e8f85eeecb510",
            year: "2023",
            trackCount: 14,
            releaseType: "Album",
            isCurated: true
        ),
        AlbumItem(
            id: "album_ghost",
            title: "Ghost",
            artist: "Diljit Dosanjh",
            coverUrl: "https://i.scdn.co/image/ab67616d0000b27301daec6faae61ba7f3295c2f",
            year: "2023",
            trackCount: 21,
            releaseType: "Album",
            isCurated: true
        ),
        AlbumItem(
            id: "album_open_letter",
            title: "Open Letter",
            artist: "Talha Anjum, Umair",
            coverUrl: "https://i.scdn.co/image/ab67616d0000b273618146aaecf3e09886fdf06a",
            year: "2023",
            trackCount: 15,
            releaseType: "Album",
            isCurated: true
        ),
        AlbumItem(
            id: "album_aashiqui2",
            title: "Aashiqui 2",
            artist: "Mithoon, Ankit Tiwari, Arijit Singh",
            coverUrl: "https://i.scdn.co/image/ab67616d0000b273cb181fa39fb39366e6eb293e",
            year: "2013",
            trackCount: 11,
            releaseType: "Album",
            isCurated: true
        ),
        AlbumItem(
            id: "album_karam",
            title: "Karam",
            artist: "KSHMR",
            coverUrl: "https://i.scdn.co/image/ab67616d0000b273860bb4f7960fa0a37e170da5",
            year: "2023",
            trackCount: 14,
            releaseType: "Album",
            isCurated: true
        ),
        AlbumItem(
            id: "album_starboy",
            title: "Starboy",
            artist: "The Weeknd",
            coverUrl: "https://i.scdn.co/image/ab67616d0000b2734718e2b124f79258be7bc452",
            year: "2016",
            trackCount: 18,
            releaseType: "Album",
            isCurated: true
        ),
        AlbumItem(
            id: "album_after_hours",
            title: "After Hours",
            artist: "The Weeknd",
            coverUrl: "https://i.scdn.co/image/ab67616d0000b2738863bc11d2aa12b54f5aeb36",
            year: "2020",
            trackCount: 14,
            releaseType: "Album",
            isCurated: true
        )
    ]

    func getUserAlbums() -> [AlbumItem] {
        var all: [VideoItem] = []
        for pl in UserDataRepository.shared.playlists {
            all.append(contentsOf: pl.videos)
        }
        all.append(contentsOf: UserDataRepository.shared.history)

        var groups: [String: [VideoItem]] = [:]
        for item in all {
            let alb = item.album.trimmingCharacters(in: .whitespacesAndNewlines)
            if alb.isEmpty || alb.lowercased() == "single" || alb.lowercased() == "unknown" { continue }
            let key = "\(alb.lowercased()):::\(item.artist.lowercased())"
            groups[key, default: []].append(item)
        }

        return groups.compactMap { (_, group) -> AlbumItem? in
            guard let first = group.first else { return nil }
            let albumName = first.album.trimmingCharacters(in: .whitespacesAndNewlines)
            let isMixtape = albumName.lowercased().contains("tape") ||
                            albumName.lowercased().contains("mix") ||
                            albumName.lowercased().contains("vol")
            let albumId = "user_album_\(abs(albumName.hashValue))"
            let artistName = first.artist.isEmpty ? first.channelName : first.artist
            let distinctTracks = SongDeduplicationEngine.deduplicate(group)
            let resolvedCover = AlbumArtworkResolver.shared.resolveArtwork(
                albumId: albumId,
                title: albumName,
                artist: artistName,
                primaryUrl: first.thumbnailUrl,
                tracks: distinctTracks
            )

            return AlbumItem(
                id: albumId,
                title: albumName,
                artist: artistName,
                coverUrl: resolvedCover.isEmpty ? first.thumbnailUrl : resolvedCover,
                year: "2024",
                trackCount: distinctTracks.count,
                releaseType: isMixtape ? "Mixtape" : "Album",
                tracks: distinctTracks,
                isCurated: false
            )
        }
    }

    func getAllAlbums() -> [AlbumItem] {
        let userAlbums = getUserAlbums()
        let userTitles = Set(userAlbums.map { $0.title.lowercased() })
        let remainingCurated = Self.curatedReleases.filter { !userTitles.contains($0.title.lowercased()) }
        return (userAlbums + remainingCurated)
    }

    func getMixtapes() -> [AlbumItem] {
        getAllAlbums().filter {
            $0.releaseType.lowercased() == "mixtape" ||
            $0.title.lowercased().contains("tape") ||
            $0.title.lowercased().contains("mix")
        }
    }

    func getPureAlbums() -> [AlbumItem] {
        getAllAlbums().filter {
            $0.releaseType.lowercased() != "mixtape" &&
            !$0.title.lowercased().contains("tape")
        }
    }

    func searchAlbums(query: String) async -> [AlbumItem] {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty { return getAllAlbums() }

        var outList: [AlbumItem] = []
        guard let url = URL(string: "https://music.youtube.com/youtubei/v1/search?key=\(YouTubeSyncEngine.innerTubeApiKey)") else {
            return getAllAlbums().filter { $0.title.localizedCaseInsensitiveContains(trimmed) || $0.artist.localizedCaseInsensitiveContains(trimmed) }
        }

        var req = URLRequest(url: url)
        req.httpMethod = "POST"
        req.setValue("application/json; charset=UTF-8", forHTTPHeaderField: "Content-Type")
        req.setValue(YouTubeSyncEngine.desktopUA, forHTTPHeaderField: "User-Agent")
        req.setValue("https://music.youtube.com", forHTTPHeaderField: "Origin")
        req.setValue("https://music.youtube.com/", forHTTPHeaderField: "Referer")
        req.setValue("67", forHTTPHeaderField: "X-YouTube-Client-Name")
        req.setValue("1.20240101.01.00", forHTTPHeaderField: "X-YouTube-Client-Version")

        let payload: [String: Any] = [
            "context": [
                "client": [
                    "clientName": "WEB_REMIX",
                    "clientVersion": "1.20240101.01.00",
                    "hl": "en",
                    "gl": "IN"
                ]
            ],
            "query": trimmed,
            "params": "EgWKAQIYAWoKEAkQChAFEAMQBA=="
        ]
        req.httpBody = try? JSONSerialization.data(withJSONObject: payload)

        do {
            let (data, resp) = try await URLSession.shared.data(for: req)
            if let http = resp as? HTTPURLResponse, http.statusCode == 200,
               let json = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any] {
                let contents = json["contents"] as? [String: Any]
                let tabbed = contents?["tabbedSearchResultsRenderer"] as? [String: Any]
                let tabs = tabbed?["tabs"] as? [[String: Any]]
                let tab0 = tabs?.first?["tabRenderer"] as? [String: Any]
                let tabContent = tab0?["content"] as? [String: Any]
                let shelves = (tabContent?["sectionListRenderer"] as? [String: Any])?["contents"] as? [[String: Any]]

                if let shelves = shelves {
                    for shelf in shelves {
                        let musicShelf = shelf["musicShelfRenderer"] as? [String: Any]
                        let items = musicShelf?["contents"] as? [[String: Any]] ?? []

                        for it in items {
                            guard let itObj = it["musicResponsiveListItemRenderer"] as? [String: Any],
                                  let cols = itObj["flexColumns"] as? [[String: Any]], !cols.isEmpty else { continue }

                            let runs0 = ((cols[0]["musicResponsiveListItemFlexColumnRenderer"] as? [String: Any])?["text"] as? [String: Any])?["runs"] as? [[String: Any]]
                            let title = (runs0?.first?["text"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
                            if title.isEmpty { continue }

                            let itData = (try? JSONSerialization.data(withJSONObject: itObj)) ?? Data()
                            let itStr = String(data: itData, encoding: .utf8) ?? ""

                            var pid = ""
                            if let regex = try? NSRegularExpression(pattern: #""playlistId":\s*"([^"]+)""#),
                               let m = regex.firstMatch(in: itStr, range: NSRange(itStr.startIndex..., in: itStr)),
                               let r = Range(m.range(at: 1), in: itStr) {
                                pid = String(itStr[r])
                            }

                            var browseId = ""
                            if let regex = try? NSRegularExpression(pattern: #""browseId":\s*"(MPREb_[^"]+)""#),
                               let m = regex.firstMatch(in: itStr, range: NSRange(itStr.startIndex..., in: itStr)),
                               let r = Range(m.range(at: 1), in: itStr) {
                                browseId = String(itStr[r])
                            }

                            let effectiveId = !pid.isEmpty ? pid : (!browseId.isEmpty ? browseId : "album_\(abs(title.hashValue))")

                            let thumbObj = itObj["thumbnail"] as? [String: Any]
                            let thumbs = ((thumbObj?["musicThumbnailRenderer"] as? [String: Any])?["thumbnail"] as? [String: Any])?["thumbnails"] as? [[String: Any]]
                                ?? ((thumbObj?["croppedSquareThumbnailRenderer"] as? [String: Any])?["thumbnail"] as? [String: Any])?["thumbnails"] as? [[String: Any]]
                                ?? (thumbObj?["thumbnails"] as? [[String: Any]])

                            var thumbUrl = thumbs?.last?["url"] as? String ?? ""
                            thumbUrl = AlbumArtworkResolver.cleanUrl(thumbUrl)

                            var relType = "Album"
                            var year = "2024"
                            var artists: [String] = []

                            if cols.count > 1 {
                                let runs1 = ((cols[1]["musicResponsiveListItemFlexColumnRenderer"] as? [String: Any])?["text"] as? [String: Any])?["runs"] as? [[String: Any]] ?? []
                                for r in runs1 {
                                    let t = (r["text"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
                                    if t == "•" || t == "," || t == "&" || t.isEmpty { continue }
                                    let low = t.lowercased()
                                    if low == "album" || low == "ep" || low == "single" || low == "mixtape" {
                                        relType = t
                                    } else if t.range(of: #"^\d{4}$"#, options: .regularExpression) != nil {
                                        year = t
                                    } else if t.count > 1 {
                                        artists.append(t)
                                    }
                                }
                            }

                            let artistName = artists.isEmpty ? trimmed : artists.joined(separator: ", ")
                            let resolvedCover = AlbumArtworkResolver.shared.resolveArtwork(
                                albumId: effectiveId,
                                title: title,
                                artist: artistName,
                                primaryUrl: thumbUrl
                            )

                            outList.append(
                                AlbumItem(
                                    id: effectiveId,
                                    title: title,
                                    artist: artistName,
                                    coverUrl: resolvedCover.isEmpty ? thumbUrl : resolvedCover,
                                    year: year,
                                    releaseType: relType
                                )
                            )
                        }
                    }
                }
            }
        } catch {}

        if !outList.isEmpty {
            var seen = Set<String>()
            return outList.filter { seen.insert($0.id).inserted }
        } else {
            return getAllAlbums().filter {
                $0.title.localizedCaseInsensitiveContains(trimmed) ||
                $0.artist.localizedCaseInsensitiveContains(trimmed)
            }
        }
    }

    func getOrFetchAlbumTracks(album: AlbumItem) async -> [VideoItem] {
        if !album.tracks.isEmpty { return album.tracks }

        if album.id.hasPrefix("OLAK5uy_") || album.id.hasPrefix("MPREb_") || album.id.hasPrefix("PL") || album.id.hasPrefix("VL") {
            let plTracks = await YouTubeSyncEngine.shared.fetchPlaylistVideos(album.id)
            if !plTracks.isEmpty {
                let enriched = plTracks.map { track in
                    var t = track
                    t.album = album.title
                    if t.artist.isEmpty || t.artist == "YouTube Music" {
                        t.artist = album.artist
                    }
                    return t
                }
                let resolvedCover = AlbumArtworkResolver.shared.resolveArtwork(
                    albumId: album.id,
                    title: album.title,
                    artist: album.artist,
                    primaryUrl: album.coverUrl,
                    tracks: enriched
                )
                if !resolvedCover.isEmpty {
                    AlbumArtworkResolver.shared.cacheAlbumArtwork(albumId: album.id, title: album.title, artist: album.artist, artworkUrl: resolvedCover)
                }
                return enriched
            }
        }

        // Secondary fallback search
        let query = "\(album.title) \(album.artist) songs"
        let searchResults = await YouTubeSearchEngine.shared.searchVideos(query, isMusicOnly: true)
        let tracks = searchResults.map { track in
            var t = track
            t.album = album.title
            if t.artist.isEmpty || t.artist == "YouTube Music" {
                t.artist = album.artist
            }
            return t
        }
        return tracks
    }

    func toUserPlaylist(album: AlbumItem) -> UserPlaylist {
        let resolvedCover = AlbumArtworkResolver.shared.resolveArtwork(
            albumId: album.id,
            title: album.title,
            artist: album.artist,
            primaryUrl: album.coverUrl,
            tracks: album.tracks
        )
        return UserPlaylist(
            id: album.id,
            title: album.title,
            description: "\(album.releaseType.uppercased()) • \(album.artist) • \(album.year) • \(album.trackCount > 0 ? "\(album.trackCount) tracks" : "Full Album")",
            videos: album.tracks,
            isSystem: false,
            iconType: "album",
            customCoverUrl: resolvedCover.isEmpty ? album.coverUrl : resolvedCover
        )
    }
}
