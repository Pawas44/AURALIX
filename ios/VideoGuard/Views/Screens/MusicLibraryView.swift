import SwiftUI
import WebKit

struct MusicLibraryView: View {
    let onTrackSelected: (VideoItem, [VideoItem]) -> Void
    let onPlaylistClick: (UserPlaylist) -> Void
    let onOpenSearch: () -> Void
    let onOpenAccountSheet: () -> Void

    @ObservedObject var userData = UserDataRepository.shared
    @State private var selectedFilter: String = "All"
    @State private var showNewPlaylistAlert = false
    @State private var newPlaylistTitle = ""
    @State private var selectedPlaylistForOptions: UserPlaylist? = nil

    @State private var selectedService: String? = nil
    @State private var showInAppBrowser = false
    @State private var inAppBrowserUrl: String = ""
    @State private var importUrlInput = ""
    @State private var importProgress: ImportProgress? = nil
    @State private var isImporting = false
    @State private var importedPlaylistResult: UserPlaylist? = nil
    @State private var isSpotifyConnected: Bool = SpotifyImportEngine.shared.isConnected
    @State private var spotifyUserName: String? = SpotifyImportEngine.shared.userDisplayName
    @State private var spotifyPlaylists: [RemotePlaylistSummary] = SpotifyImportEngine.shared.userPlaylists
    @State private var selectedPlaylistIds: Set<String> = []

    @Environment(\.openURL) var openURL

    var likedPlaylist: UserPlaylist? {
        userData.playlists.first(where: { $0.id == "liked_videos" })
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                // Top Bar
                HStack {
                    Text("Library")
                        .font(.system(size: 24, weight: .bold))
                        .foregroundColor(.white)
                    Spacer()
                    Button(action: onOpenSearch) {
                        Image(systemName: "magnifyingglass")
                            .font(.system(size: 19))
                            .foregroundColor(.white)
                    }
                    Button(action: onOpenAccountSheet) {
                        Image(systemName: "person.crop.circle")
                            .font(.system(size: 21))
                            .foregroundColor(.white)
                            .padding(.leading, 12)
                    }
                }
                .padding(.horizontal, 16)
                .padding(.top, 10)

                // Category Chips
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(["All", "Playlists", "Liked Songs", "Recently Played", "Downloads"], id: \.self) { chip in
                            let isSelected = selectedFilter == chip
                            Button(action: { selectedFilter = chip }) {
                                Text(chip)
                                    .font(.system(size: 12, weight: isSelected ? .bold : .medium))
                                    .foregroundColor(isSelected ? .black : .white)
                                    .padding(.horizontal, 14)
                                    .padding(.vertical, 7)
                                    .background(isSelected ? Color.white : Color(hex: 0x1E1E24))
                                    .cornerRadius(16)
                            }
                        }
                    }
                    .padding(.horizontal, 16)
                }

                // 1. "Import your music" Section (matching user uploaded reference image)
                if selectedFilter == "All" || selectedFilter == "Playlists" {
                    VStack(alignment: .leading, spacing: 14) {
                        VStack(alignment: .leading, spacing: 4) {
                            Text("Import your music")
                                .font(.system(size: 18, weight: .bold))
                                .foregroundColor(.white)
                            Text("Select a service below to move your library and playlists to AURALIX")
                                .font(.system(size: 12))
                                .foregroundColor(Color(hex: 0xAAAAAA))
                        }

                        // Services list (matching reference screenshot)
                        VStack(spacing: 0) {
                            // 1. Spotify
                            importServiceRow(
                                title: "Spotify",
                                brandColor: Color(hex: 0x1DB954),
                                iconName: "music.note"
                            ) {
                                openServiceImport(service: "Spotify")
                            }

                            Divider().background(Color.white.opacity(0.08))

                            // 2. Amazon Music
                            importServiceRow(
                                title: "Amazon Music",
                                brandColor: Color(hex: 0x00A8E1),
                                iconName: "music.note"
                            ) {
                                openServiceImport(service: "Amazon Music")
                            }
                        }
                        .background(Color(hex: 0x141622))
                        .cornerRadius(14)
                        .overlay(
                            RoundedRectangle(cornerRadius: 14)
                                .stroke(Color.white.opacity(0.07), lineWidth: 1)
                        )

                        Text("By selecting a service above, you are choosing to transfer your followed artists, liked tracks, albums and playlists to AURALIX via TuneMyMusic.")
                            .font(.system(size: 11))
                            .foregroundColor(Color(hex: 0x888B9E))
                            .lineSpacing(4)
                    }
                    .padding(16)
                    .background(Color(hex: 0x0E1019))
                    .cornerRadius(18)
                    .overlay(
                        RoundedRectangle(cornerRadius: 18)
                            .stroke(Color.white.opacity(0.08), lineWidth: 1)
                    )
                    .padding(.horizontal, 16)
                }

                // 2. Liked Songs Hero Card
                if selectedFilter == "All" || selectedFilter == "Liked Songs" {
                    Button(action: {
                        if let pl = likedPlaylist {
                            onPlaylistClick(pl)
                        }
                    }) {
                        HStack(spacing: 14) {
                            ZStack {
                                RoundedRectangle(cornerRadius: 10)
                                    .fill(
                                        LinearGradient(
                                            colors: [AppTheme.ytMusicRedAccent, Color(hex: 0x990022)],
                                            startPoint: .topLeading,
                                            endPoint: .bottomTrailing
                                        )
                                    )
                                    .frame(width: 54, height: 54)
                                Image(systemName: "heart.fill")
                                    .font(.system(size: 26))
                                    .foregroundColor(.white)
                            }

                            VStack(alignment: .leading, spacing: 3) {
                                Text("Liked Songs")
                                    .font(.system(size: 16, weight: .bold))
                                    .foregroundColor(.white)
                                Text("Auto playlist • \(likedPlaylist?.videoCount ?? 0) tracks")
                                    .font(.system(size: 12))
                                    .foregroundColor(Color(hex: 0xAAAAAA))
                            }

                            Spacer()

                            Image(systemName: "play.circle.fill")
                                .font(.system(size: 34))
                                .foregroundColor(AppTheme.ytMusicRedAccent)
                        }
                        .padding(14)
                        .background(Color(hex: 0x191014))
                        .cornerRadius(14)
                        .overlay(
                            RoundedRectangle(cornerRadius: 14)
                                .stroke(AppTheme.ytMusicRedAccent.opacity(0.35), lineWidth: 1)
                        )
                    }
                    .padding(.horizontal, 16)
                }

                // 3. Recently Played Carousel
                if (selectedFilter == "All" || selectedFilter == "Recently Played") && !userData.watchHistory.isEmpty {
                    VStack(alignment: .leading, spacing: 10) {
                        Text("Recently played")
                            .font(.system(size: 17, weight: .bold))
                            .foregroundColor(.white)
                            .padding(.horizontal, 16)

                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 12) {
                                ForEach(userData.watchHistory.prefix(12)) { track in
                                    Button(action: { onTrackSelected(track, userData.watchHistory) }) {
                                        VStack(alignment: .leading, spacing: 6) {
                                            AsyncImage(url: URL(string: track.thumbnailUrl)) { img in
                                                img.resizable().aspectRatio(contentMode: .fill)
                                            } placeholder: {
                                                Rectangle().fill(AppTheme.ytMusicCardBg)
                                            }
                                            .frame(width: 110, height: 110)
                                            .cornerRadius(8)
                                            .clipped()

                                            Text(track.title)
                                                .font(.system(size: 12, weight: .medium))
                                                .foregroundColor(.white)
                                                .lineLimit(1)
                                                .frame(width: 110, alignment: .leading)

                                            Text(track.channelName)
                                                .font(.system(size: 11))
                                                .foregroundColor(Color(hex: 0xAAAAAA))
                                                .lineLimit(1)
                                                .frame(width: 110, alignment: .leading)
                                        }
                                    }
                                }
                            }
                            .padding(.horizontal, 16)
                        }
                    }
                }

                // 4. Made For You / Smart Recommendations
                if (selectedFilter == "All" || selectedFilter == "Made For You") && !userData.watchHistory.isEmpty {
                    VStack(alignment: .leading, spacing: 10) {
                        Text("Made for you")
                            .font(.system(size: 17, weight: .bold))
                            .foregroundColor(.white)
                            .padding(.horizontal, 16)

                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 12) {
                                ForEach(userData.watchHistory.shuffled().prefix(8)) { track in
                                    Button(action: { onTrackSelected(track, userData.watchHistory) }) {
                                        VStack(alignment: .leading, spacing: 6) {
                                            ZStack(alignment: .bottomTrailing) {
                                                AsyncImage(url: URL(string: track.thumbnailUrl)) { img in
                                                    img.resizable().aspectRatio(contentMode: .fill)
                                                } placeholder: {
                                                    Rectangle().fill(AppTheme.ytMusicCardBg)
                                                }
                                                .frame(width: 120, height: 120)
                                                .cornerRadius(8)
                                                .clipped()

                                                Circle()
                                                    .fill(Color.black.opacity(0.7))
                                                    .frame(width: 28, height: 28)
                                                    .overlay(
                                                        Image(systemName: "play.fill")
                                                            .font(.system(size: 12))
                                                            .foregroundColor(.white)
                                                    )
                                                    .padding(6)
                                            }

                                            Text(track.title)
                                                .font(.system(size: 12, weight: .medium))
                                                .foregroundColor(.white)
                                                .lineLimit(1)
                                                .frame(width: 120, alignment: .leading)

                                            Text("Mix • \(track.channelName)")
                                                .font(.system(size: 11))
                                                .foregroundColor(Color(hex: 0xAAAAAA))
                                                .lineLimit(1)
                                                .frame(width: 120, alignment: .leading)
                                        }
                                    }
                                }
                            }
                            .padding(.horizontal, 16)
                        }
                    }
                }

                // 4. Downloads / Offline Section
                if selectedFilter == "All" || selectedFilter == "Downloads" {
                    if !userData.offlineTracks.isEmpty {
                        VStack(alignment: .leading, spacing: 10) {
                            HStack {
                                Text("Downloads (\(userData.offlineTracks.count))")
                                    .font(.system(size: 18, weight: .bold))
                                    .foregroundColor(.white)
                                Spacer()
                                Text("Offline Available")
                                    .font(.system(size: 11, weight: .semibold))
                                    .foregroundColor(AppTheme.ytMusicRedAccent)
                            }
                            .padding(.horizontal, 16)

                            VStack(spacing: 8) {
                                ForEach(userData.offlineTracks) { track in
                                    Button(action: {
                                        onTrackSelected(track, userData.offlineTracks)
                                    }) {
                                        HStack(spacing: 12) {
                                            AsyncImage(url: URL(string: track.thumbnailUrl)) { img in
                                                img.resizable().aspectRatio(contentMode: .fill)
                                            } placeholder: {
                                                Color(hex: 0x222222)
                                            }
                                            .frame(width: 44, height: 44)
                                            .cornerRadius(6)

                                            VStack(alignment: .leading, spacing: 2) {
                                                Text(track.title)
                                                    .font(.system(size: 13, weight: .semibold))
                                                    .foregroundColor(.white)
                                                    .lineLimit(1)
                                                Text("\(track.artist.isEmpty ? track.channelName : track.artist) • Offline")
                                                    .font(.system(size: 11))
                                                    .foregroundColor(Color.gray)
                                                    .lineLimit(1)
                                            }

                                            Spacer()

                                            Image(systemName: "checkmark.circle.fill")
                                                .font(.system(size: 17))
                                                .foregroundColor(AppTheme.ytMusicRedAccent)
                                        }
                                        .padding(10)
                                        .background(Color(hex: 0x141620))
                                        .cornerRadius(10)
                                    }
                                }
                            }
                            .padding(.horizontal, 16)
                        }
                    } else if selectedFilter == "Downloads" {
                        VStack(spacing: 10) {
                            Image(systemName: "arrow.down.circle")
                                .font(.system(size: 44))
                                .foregroundColor(.gray)
                            Text("No downloaded tracks")
                                .font(.system(size: 16, weight: .bold))
                                .foregroundColor(.white)
                            Text("Tap the download icon on any song to listen offline anytime.")
                                .font(.system(size: 12))
                                .foregroundColor(.gray)
                                .multilineTextAlignment(.center)
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 40)
                        .padding(.horizontal, 24)
                    }
                }

                // 5. Playlists Section with '+' New Playlist Button
                if selectedFilter == "All" || selectedFilter == "Playlists" {
                    VStack(alignment: .leading, spacing: 12) {
                        HStack {
                            Text("Playlists")
                                .font(.system(size: 18, weight: .bold))
                                .foregroundColor(.white)
                            Spacer()
                            Button(action: { showNewPlaylistAlert = true }) {
                                Label("New Playlist", systemImage: "plus")
                                    .font(.system(size: 13, weight: .semibold))
                                    .foregroundColor(.white)
                                    .padding(.horizontal, 12)
                                    .padding(.vertical, 6)
                                    .liquidGlassPill()
                            }
                        }
                        .padding(.horizontal, 16)

                        if userData.playlists.isEmpty {
                            VStack(spacing: 12) {
                                Image(systemName: "music.note.list")
                                    .font(.system(size: 40))
                                    .foregroundColor(.gray)
                                Text("No playlists yet")
                                    .font(.system(size: 15, weight: .semibold))
                                    .foregroundColor(.white)
                                Text("Create a new playlist or import from Spotify / Amazon Music")
                                    .font(.system(size: 12))
                                    .foregroundColor(.gray)
                                    .multilineTextAlignment(.center)
                                Button(action: { showNewPlaylistAlert = true }) {
                                    HStack(spacing: 6) {
                                        Image(systemName: "plus")
                                        Text("Create Playlist")
                                    }
                                    .font(.system(size: 13, weight: .bold))
                                    .foregroundColor(.white)
                                    .padding(.horizontal, 16)
                                    .padding(.vertical, 8)
                                    .background(AppTheme.ytMusicRedAccent)
                                    .cornerRadius(20)
                                }
                            }
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 24)
                        } else {
                            LazyVStack(spacing: 10) {
                                ForEach(userData.playlists) { playlist in
                                    Button(action: { onPlaylistClick(playlist) }) {
                                        HStack(spacing: 14) {
                                            if !playlist.thumbnailUrl.isEmpty {
                                                AsyncImage(url: URL(string: playlist.thumbnailUrl)) { img in
                                                    img.resizable().aspectRatio(contentMode: .fill)
                                                } placeholder: {
                                                    Rectangle().fill(AppTheme.ytMusicCardBg)
                                                }
                                                .frame(width: 56, height: 56)
                                                .cornerRadius(8)
                                                .clipped()
                                            } else {
                                                ZStack {
                                                    RoundedRectangle(cornerRadius: 8)
                                                        .fill(Color.white.opacity(0.08))
                                                    Image(systemName: playlist.iconType == "like" ? "heart.fill" : "music.note.list")
                                                        .font(.system(size: 22))
                                                        .foregroundColor(.white)
                                                }
                                                .frame(width: 56, height: 56)
                                            }

                                            VStack(alignment: .leading, spacing: 3) {
                                                Text(playlist.title)
                                                    .font(.system(size: 14, weight: .semibold))
                                                    .foregroundColor(.white)
                                                    .lineLimit(1)
                                                Text("Playlist • \(playlist.videoCount) tracks")
                                                    .font(.system(size: 12))
                                                    .foregroundColor(AppTheme.ytMusicTextSecondary)
                                            }
                                            Spacer()

                                            Button(action: { selectedPlaylistForOptions = playlist }) {
                                                Image(systemName: "ellipsis")
                                                    .rotationEffect(.degrees(90))
                                                    .font(.system(size: 16))
                                                    .foregroundColor(Color(hex: 0xAAAAAA))
                                                    .frame(width: 34, height: 34)
                                            }
                                        }
                                        .padding(.horizontal, 16)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            .padding(.bottom, 90)
        }
        .confirmationDialog(
            selectedPlaylistForOptions?.title ?? "Playlist Options",
            isPresented: Binding(
                get: { selectedPlaylistForOptions != nil },
                set: { if !$0 { selectedPlaylistForOptions = nil } }
            ),
            titleVisibility: .visible
        ) {
            if let pl = selectedPlaylistForOptions {
                Button("Clear All Songs", role: .destructive) {
                    userData.clearPlaylist(playlistId: pl.id)
                    selectedPlaylistForOptions = nil
                }
                Button("Delete Playlist", role: .destructive) {
                    userData.deletePlaylist(playlistId: pl.id)
                    selectedPlaylistForOptions = nil
                }
                Button("Cancel", role: .cancel) {
                    selectedPlaylistForOptions = nil
                }
            }
        }
        .alert("New Playlist", isPresented: $showNewPlaylistAlert) {
            TextField("Playlist Title", text: $newPlaylistTitle)
            Button("Create") {
                let trimmed = newPlaylistTitle.trimmingCharacters(in: .whitespacesAndNewlines)
                if !trimmed.isEmpty {
                    let created = userData.createPlaylist(title: trimmed)
                    newPlaylistTitle = ""
                    onPlaylistClick(created)
                }
            }
            Button("Cancel", role: .cancel) { newPlaylistTitle = "" }
        }
        .sheet(item: Binding<ServiceItem?>(
            get: { selectedService != nil ? ServiceItem(name: selectedService!) : nil },
            set: { selectedService = $0?.name }
        )) { item in
            serviceImportSheetView(service: item.name)
        }
    }

    private func openServiceImport(service: String) {
        selectedService = service
        importUrlInput = ""
        importProgress = nil
        importedPlaylistResult = nil
    }

    @ViewBuilder
    private func importServiceRow(
        title: String,
        brandColor: Color,
        iconName: String,
        onClick: @escaping () -> Void
    ) -> some View {
        Button(action: onClick) {
            HStack(spacing: 14) {
                ZStack {
                    RoundedRectangle(cornerRadius: 8)
                        .fill(brandColor)
                        .frame(width: 36, height: 36)
                    Image(systemName: iconName)
                        .font(.system(size: 17, weight: .semibold))
                        .foregroundColor(.white)
                }

                Text(title)
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundColor(.white)

                Spacer()

                Image(systemName: "chevron.right")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundColor(Color(hex: 0x888888))
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 12)
        }
    }

    @ViewBuilder
    private func serviceImportSheetView(service: String) -> some View {
        let brandColor: Color = {
            switch service {
            case "Spotify": return Color(hex: 0x1DB954)
            case "Apple Music": return Color(hex: 0xFA243C)
            case "YouTube": return Color(hex: 0xFF0000)
            case "Tidal": return Color(hex: 0x00E5FF)
            case "SoundCloud": return Color(hex: 0xFF5500)
            case "Amazon Music": return Color(hex: 0x00A8E1)
            default: return AppTheme.ytMusicRedAccent
            }
        }()

        let authUrlStr: String = {
            switch service {
            case "Spotify": return SpotifyImportEngine.shared.createAuthUrl()
            case "Apple Music": return "https://music.apple.com"
            case "YouTube": return "https://music.youtube.com"
            case "Tidal": return "https://login.tidal.com"
            case "SoundCloud": return "https://soundcloud.com/signin"
            case "Amazon Music": return "https://music.amazon.com"
            default: return "https://www.tunemymusic.com"
            }
        }()

        let tuneMyMusicUrlStr: String = {
            switch service {
            case "Spotify": return "https://www.tunemymusic.com/spotify"
            case "Apple Music": return "https://www.tunemymusic.com/apple-music"
            case "YouTube": return "https://www.tunemymusic.com/youtube"
            case "Tidal": return "https://www.tunemymusic.com/tidal"
            case "SoundCloud": return "https://www.tunemymusic.com/soundcloud"
            case "Amazon Music": return "https://www.tunemymusic.com/amazon-music"
            default: return "https://www.tunemymusic.com"
            }
        }()

        NavigationView {
            VStack(alignment: .leading, spacing: 16) {
                if service == "Spotify" && isSpotifyConnected {
                    HStack {
                        Image(systemName: "checkmark.circle.fill")
                            .foregroundColor(brandColor)
                            .font(.system(size: 18))
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Connected to Spotify")
                                .font(.system(size: 13, weight: .bold))
                                .foregroundColor(.white)
                            Text(spotifyUserName ?? "Authorized Account")
                                .font(.system(size: 11))
                                .foregroundColor(Color.gray)
                        }
                        Spacer()
                        Button("Disconnect") {
                            SpotifyImportEngine.shared.disconnect()
                            isSpotifyConnected = false
                            spotifyUserName = nil
                            spotifyPlaylists = []
                        }
                        .font(.system(size: 11, weight: .semibold))
                        .foregroundColor(Color.red.opacity(0.8))
                    }
                    .padding(12)
                    .background(brandColor.opacity(0.15))
                    .cornerRadius(12)
                    .overlay(RoundedRectangle(cornerRadius: 12).stroke(brandColor.opacity(0.4), lineWidth: 1))

                    if spotifyPlaylists.isEmpty {
                        Button(action: {
                            Task {
                                let pls = await SpotifyImportEngine.shared.fetchUserPlaylists()
                                await MainActor.run { self.spotifyPlaylists = pls }
                            }
                        }) {
                            HStack {
                                Image(systemName: "arrow.clockwise")
                                Text("Fetch My Playlists")
                                    .fontWeight(.bold)
                            }
                            .font(.system(size: 13))
                            .foregroundColor(.black)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 12)
                            .background(brandColor)
                            .cornerRadius(12)
                        }
                    } else {
                        HStack {
                            Text("\(spotifyPlaylists.count) Playlists Found")
                                .font(.system(size: 13, weight: .semibold))
                                .foregroundColor(.white)
                            Spacer()
                            Button(selectedPlaylistIds.count == spotifyPlaylists.count ? "Deselect All" : "Select All") {
                                if selectedPlaylistIds.count == spotifyPlaylists.count {
                                    selectedPlaylistIds = []
                                } else {
                                    selectedPlaylistIds = Set(spotifyPlaylists.map { $0.id })
                                }
                            }
                            .font(.system(size: 12, weight: .semibold))
                            .foregroundColor(brandColor)
                        }

                        ScrollView {
                            VStack(spacing: 8) {
                                ForEach(spotifyPlaylists) { pl in
                                    let isSelected = selectedPlaylistIds.contains(pl.id)
                                    Button(action: {
                                        if isSelected {
                                            selectedPlaylistIds.remove(pl.id)
                                        } else {
                                            selectedPlaylistIds.insert(pl.id)
                                        }
                                    }) {
                                        HStack(spacing: 10) {
                                            Image(systemName: isSelected ? "checkmark.square.fill" : "square")
                                                .foregroundColor(isSelected ? brandColor : Color.gray)
                                                .font(.system(size: 18))
                                            if let urlStr = pl.coverUrl, let url = URL(string: urlStr) {
                                                AsyncImage(url: url) { phase in
                                                    if let img = phase.image {
                                                        img.resizable().scaledToFill()
                                                    } else {
                                                        Color.gray.opacity(0.3)
                                                    }
                                                }
                                                .frame(width: 36, height: 36)
                                                .cornerRadius(6)
                                            }
                                            VStack(alignment: .leading, spacing: 2) {
                                                Text(pl.title)
                                                    .font(.system(size: 13, weight: .semibold))
                                                    .foregroundColor(.white)
                                                    .lineLimit(1)
                                                Text("\(pl.trackCount) tracks")
                                                    .font(.system(size: 11))
                                                    .foregroundColor(Color.gray)
                                            }
                                            Spacer()
                                        }
                                        .padding(8)
                                        .background(isSelected ? brandColor.opacity(0.15) : Color(hex: 0x1E202E))
                                        .cornerRadius(8)
                                    }
                                }
                            }
                        }
                        .frame(maxHeight: 200)

                        Button(action: {
                            let targets = spotifyPlaylists.filter { selectedPlaylistIds.contains($0.id) }
                            guard !targets.isEmpty && !isImporting else { return }
                            isImporting = true
                            importProgress = nil
                            Task {
                                for target in targets {
                                    _ = await SpotifyImportEngine.shared.importRemotePlaylist(summary: target) { prog in
                                        DispatchQueue.main.async {
                                            self.importProgress = prog
                                        }
                                    }
                                }
                                await MainActor.run {
                                    self.isImporting = false
                                }
                            }
                        }) {
                            Text(isImporting ? "Importing Playlists..." : "Import Selected (\(selectedPlaylistIds.count))")
                                .font(.system(size: 14, weight: .bold))
                                .foregroundColor(.black)
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 12)
                                .background(selectedPlaylistIds.isEmpty ? Color.gray : brandColor)
                                .cornerRadius(12)
                        }
                        .disabled(selectedPlaylistIds.isEmpty || isImporting)
                    }
                } else {
                    if service == "Amazon Music" {
                        VStack(alignment: .leading, spacing: 4) {
                            Text("Official Amazon Music Partner Notice")
                                .font(.system(size: 11, weight: .bold))
                                .foregroundColor(.white)
                            Text("Amazon restricts playlist export APIs to enterprise whitelisted partners. Use TuneMyMusic (Official Partner) or Amazon Music Web below to transfer seamlessly.")
                                .font(.system(size: 10))
                                .foregroundColor(Color(hex: 0xAAAAAA))
                                .lineSpacing(3)
                        }
                        .padding(10)
                        .background(Color(hex: 0x161A26))
                        .cornerRadius(10)
                        .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color.white.opacity(0.08), lineWidth: 1))
                    }

                    Text("Sign in to transfer your playlists and liked library automatically, or paste a direct playlist link below:")
                        .font(.system(size: 13))
                        .foregroundColor(Color(hex: 0xCCCCCC))

                    // In-App Sign In Button
                    Button(action: {
                        inAppBrowserUrl = authUrlStr
                        showInAppBrowser = true
                    }) {
                        HStack(spacing: 8) {
                            Image(systemName: "globe")
                                .font(.system(size: 16, weight: .bold))
                            Text("Connect & Sign In (In-App)")
                                .font(.system(size: 14, weight: .bold))
                        }
                        .foregroundColor(.black)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                        .background(brandColor)
                        .cornerRadius(12)
                    }

                    // TuneMyMusic Official Cloud Transfer
                    Button(action: {
                        inAppBrowserUrl = tuneMyMusicUrlStr
                        showInAppBrowser = true
                    }) {
                        HStack(spacing: 8) {
                            Image(systemName: "bolt.fill")
                                .foregroundColor(brandColor)
                            Text("⚡ Sync via TuneMyMusic (Official)")
                                .font(.system(size: 13, weight: .semibold))
                                .foregroundColor(.white)
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 11)
                        .background(Color.white.opacity(0.08))
                        .cornerRadius(12)
                        .overlay(
                            RoundedRectangle(cornerRadius: 12)
                                .stroke(brandColor.opacity(0.6), lineWidth: 1)
                        )
                    }

                    // Safari / External Browser fallback
                    Button(action: {
                        if let url = URL(string: authUrlStr) {
                            UIApplication.shared.open(url)
                        }
                    }) {
                        HStack(spacing: 8) {
                            Image(systemName: "safari")
                                .foregroundColor(.white)
                            Text("↗ Open in External Safari")
                                .font(.system(size: 12, weight: .medium))
                                .foregroundColor(Color(hex: 0xDDDDDD))
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 10)
                        .background(Color.white.opacity(0.04))
                        .cornerRadius(12)
                        .overlay(
                            RoundedRectangle(cornerRadius: 12)
                                .stroke(Color.white.opacity(0.15), lineWidth: 1)
                        )
                    }
                }

                HStack {
                    Rectangle().fill(Color.white.opacity(0.12)).frame(height: 1)
                    Text("OR PASTE LINK")
                        .font(.system(size: 10, weight: .semibold))
                        .foregroundColor(.gray)
                    Rectangle().fill(Color.white.opacity(0.12)).frame(height: 1)
                }

                VStack(alignment: .trailing, spacing: 8) {
                    TextField(
                        service == "Spotify" ? "https://open.spotify.com/playlist/..." :
                        "https://music.amazon.com/playlists/...",
                        text: $importUrlInput
                    )
                    .padding(12)
                    .background(Color.white.opacity(0.08))
                    .cornerRadius(10)
                    .foregroundColor(.white)

                    Button(action: {
                        if let clip = UIPasteboard.general.string {
                            importUrlInput = clip
                        }
                    }) {
                        HStack(spacing: 4) {
                            Image(systemName: "doc.on.clipboard")
                            Text("Paste from Clipboard")
                        }
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundColor(brandColor)
                    }
                }

                if let prog = importProgress {
                    VStack(alignment: .leading, spacing: 6) {
                        if isImporting {
                            ProgressView(value: prog.total > 0 ? Double(prog.current) / Double(prog.total) : 0)
                                .tint(brandColor)
                        }

                        Text(prog.total > 0 ? "\(prog.stage) (\(prog.current)/\(prog.total))" : prog.stage)
                            .font(.system(size: 13, weight: .medium))
                            .foregroundColor(.white)

                        if !prog.currentTrackName.isEmpty {
                            Text(prog.currentTrackName)
                                .font(.system(size: 11))
                                .foregroundColor(Color(hex: 0xAAAAAA))
                                .lineLimit(1)
                        }
                    }
                    .padding(12)
                    .background(Color.white.opacity(0.05))
                    .cornerRadius(10)
                }

                if let res = importedPlaylistResult {
                    VStack(alignment: .leading, spacing: 6) {
                        Text("✓ Successfully Imported!")
                            .font(.system(size: 14, weight: .bold))
                            .foregroundColor(brandColor)
                        Text("\(res.title) • \(res.videoCount) tracks")
                            .font(.system(size: 13))
                            .foregroundColor(.white)

                        Button(action: {
                            selectedService = nil
                            onPlaylistClick(res)
                        }) {
                            Text("Play Playlist Now")
                                .font(.system(size: 14, weight: .bold))
                                .foregroundColor(.black)
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 10)
                                .background(brandColor)
                                .cornerRadius(8)
                        }
                        .padding(.top, 6)
                    }
                    .padding(12)
                    .background(brandColor.opacity(0.15))
                    .cornerRadius(10)
                }

                Spacer()

                if importedPlaylistResult == nil {
                    Button(action: {
                        guard !importUrlInput.isEmpty && !isImporting else { return }
                        isImporting = true
                        importProgress = nil
                        Task {
                            let result = await SpotifyImportEngine.shared.importPlaylist(from: importUrlInput) { prog in
                                DispatchQueue.main.async {
                                    self.importProgress = prog
                                }
                            }
                            await MainActor.run {
                                self.isImporting = false
                                if case .success(let pl) = result {
                                    self.importedPlaylistResult = pl
                                }
                            }
                        }
                    }) {
                        Text(isImporting ? "Syncing..." : "Start Sync")
                            .font(.system(size: 16, weight: .bold))
                            .foregroundColor(.black)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 14)
                            .background(importUrlInput.isEmpty || isImporting ? Color.gray : brandColor)
                            .cornerRadius(12)
                    }
                    .disabled(importUrlInput.isEmpty || isImporting)
                }
            }
            .padding(20)
            .background(Color(hex: 0x10121C).ignoresSafeArea())
            .navigationTitle("Import from \(service)")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Close") {
                        selectedService = nil
                        importProgress = nil
                        importedPlaylistResult = nil
                    }
                    .foregroundColor(.white)
                }
            }
            .sheet(isPresented: $showInAppBrowser) {
                InAppWebTransferSheet(
                    initialUrl: inAppBrowserUrl,
                    serviceName: service,
                    brandColor: brandColor,
                    onSyncUrl: { detectedUrl in
                        importUrlInput = detectedUrl
                        showInAppBrowser = false
                        isImporting = true
                        importProgress = nil
                        importedPlaylistResult = nil
                        Task {
                            let result = await SpotifyImportEngine.shared.importPlaylist(from: detectedUrl) { prog in
                                DispatchQueue.main.async {
                                    self.importProgress = prog
                                }
                            }
                            await MainActor.run {
                                self.isImporting = false
                                if case .success(let pl) = result {
                                    self.importedPlaylistResult = pl
                                }
                            }
                        }
                    }
                )
            }
            .onChange(of: showInAppBrowser) { isOpen in
                if !isOpen && service == "Spotify" && SpotifyImportEngine.shared.isConnected {
                    self.isSpotifyConnected = true
                    Task {
                        _ = await SpotifyImportEngine.shared.fetchUserProfile()
                        let pls = await SpotifyImportEngine.shared.fetchUserPlaylists()
                        await MainActor.run {
                            self.spotifyUserName = SpotifyImportEngine.shared.userDisplayName
                            self.spotifyPlaylists = pls
                        }
                    }
                }
            }
        }
    }
}

struct InAppWebTransferSheet: View {
    let initialUrl: String
    let serviceName: String
    let brandColor: Color
    let onSyncUrl: (String) -> Void

    @Environment(\.dismiss) var dismiss
    @State private var currentUrl: String = ""
    @State private var webViewInstance: WKWebView? = nil

    var body: some View {
        NavigationView {
            VStack(spacing: 0) {
                InAppWKWebView(
                    urlString: initialUrl,
                    onUrlChange: { url in
                        currentUrl = url
                        if serviceName == "Spotify" && SpotifyImportEngine.shared.handleAuthCallback(url: url) {
                            dismiss()
                        }
                    },
                    webViewRef: $webViewInstance
                )

                VStack(spacing: 8) {
                    HStack {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Active Page:")
                                .font(.system(size: 11, weight: .semibold))
                                .foregroundColor(Color.gray)
                            Text(currentUrl.isEmpty ? initialUrl : currentUrl)
                                .font(.system(size: 11))
                                .foregroundColor(.white)
                                .lineLimit(1)
                        }
                        Spacer()
                        Button(action: {
                            let jsCode = """
                            (function() {
                                var title = document.querySelector('h1')?.innerText || document.title || '';
                                title = title.replace(/\\s*\\|\\s*Spotify.*$/i, '').replace(/on Amazon Music.*$/i, '').trim();
                                var tracks = [];
                                var spItems = document.querySelectorAll('[data-testid="tracklist-row"]');
                                spItems.forEach(function(row) {
                                    var t = row.querySelector('[data-testid="internal-track-link"]')?.innerText || '';
                                    var a = row.querySelector('a[href*="/artist/"]')?.innerText || '';
                                    if (t) tracks.push(t + ' - ' + a);
                                });
                                if (tracks.length === 0) {
                                    var amItems = document.querySelectorAll('music-vertical-item, music-horizontal-item, [role="row"]');
                                    amItems.forEach(function(row) {
                                        var primary = row.querySelector('.primary-text, [primary-text], [slot="primaryText"]')?.innerText || '';
                                        var secondary = row.querySelector('.secondary-text, [secondary-text], [slot="secondaryText"]')?.innerText || '';
                                        if (primary && primary !== 'Library') tracks.push(primary + ' - ' + secondary);
                                    });
                                }
                                if (tracks.length === 0) {
                                    var appleItems = document.querySelectorAll('.songs-list-row, [data-testid="track-cell"], .tracklist-item');
                                    appleItems.forEach(function(row) {
                                        var t = row.querySelector('.songs-list-row__song-name, [data-testid="track-title"], .track-lockup__title')?.innerText || '';
                                        var a = row.querySelector('.songs-list-row__by-line, [data-testid="track-artist"], .track-lockup__sub-title, a[href*="/artist/"]')?.innerText || '';
                                        if (t) tracks.push(t + ' - ' + a);
                                    });
                                }
                                return JSON.stringify({ title: title, tracks: tracks });
                            })();
                            """
                            webViewInstance?.evaluateJavaScript(jsCode) { result, _ in
                                if let jsonStr = result as? String,
                                   jsonStr.contains("\"tracks\":[") && !jsonStr.contains("\"tracks\":[]") {
                                    onSyncUrl(jsonStr)
                                } else {
                                    let urlToSync = currentUrl.isEmpty ? initialUrl : currentUrl
                                    onSyncUrl(urlToSync)
                                }
                            }
                        }) {
                            HStack(spacing: 6) {
                                Image(systemName: "bolt.fill")
                                Text("Sync This")
                                    .fontWeight(.bold)
                            }
                            .font(.system(size: 13))
                            .foregroundColor(.black)
                            .padding(.horizontal, 16)
                            .padding(.vertical, 9)
                            .background(brandColor)
                            .cornerRadius(18)
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 10)
                }
                .background(Color(hex: 0x161824))
            }
            .background(Color.black.ignoresSafeArea())
            .navigationTitle("\(serviceName) Connect")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Close") { dismiss() }
                        .foregroundColor(.white)
                }
                ToolbarItem(placement: .primaryAction) {
                    Button(action: {
                        let target = currentUrl.isEmpty ? initialUrl : currentUrl
                        if let u = URL(string: target) {
                            UIApplication.shared.open(u)
                        }
                    }) {
                        Image(systemName: "safari")
                            .foregroundColor(.white)
                    }
                }
            }
        }
    }
}

struct InAppWKWebView: UIViewRepresentable {
    let urlString: String
    let onUrlChange: (String) -> Void
    @Binding var webViewRef: WKWebView?

    func makeCoordinator() -> Coordinator {
        Coordinator(onUrlChange: onUrlChange)
    }

    func makeUIView(context: Context) -> WKWebView {
        let config = WKWebViewConfiguration()
        config.defaultWebpagePreferences.allowsContentJavaScript = true
        config.websiteDataStore = WKWebsiteDataStore.default()
        let webView = WKWebView(frame: .zero, configuration: config)
        webView.customUserAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Safari/605.1.15"
        webView.navigationDelegate = context.coordinator
        webView.allowsBackForwardNavigationGestures = true
        DispatchQueue.main.async {
            self.webViewRef = webView
        }
        if let url = URL(string: urlString) {
            webView.load(URLRequest(url: url))
        }
        return webView
    }

    func updateUIView(_ uiView: WKWebView, context: Context) {}

    class Coordinator: NSObject, WKNavigationDelegate {
        let onUrlChange: (String) -> Void

        init(onUrlChange: @escaping (String) -> Void) {
            self.onUrlChange = onUrlChange
        }

        func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
            if let urlStr = webView.url?.absoluteString {
                onUrlChange(urlStr)
            }
        }
    }
}

private struct ServiceItem: Identifiable {
    var id: String { name }
    let name: String
}
