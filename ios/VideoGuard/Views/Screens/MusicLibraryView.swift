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
                            Text("Move your Spotify library and playlists seamlessly to AURALIX")
                                .font(.system(size: 12))
                                .foregroundColor(Color(hex: 0xAAAAAA))
                        }

                        // Services list
                        VStack(spacing: 0) {
                            // Spotify
                            importServiceRow(
                                title: "Spotify",
                                brandColor: Color(hex: 0x1DB954),
                                iconName: "music.note"
                            ) {
                                openServiceImport(service: "Spotify")
                            }
                        }
                        .background(Color(hex: 0x141622))
                        .cornerRadius(14)
                        .overlay(
                            RoundedRectangle(cornerRadius: 14)
                                .stroke(Color.white.opacity(0.07), lineWidth: 1)
                        )

                        Text("Paste any Spotify playlist link to import all tracks directly into your AURALIX library.")
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
                                Text("Create a new playlist or import from Spotify")
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
        .sheet(isPresented: Binding<Bool>(
            get: { selectedService != nil },
            set: { if !$0 { selectedService = nil } }
        )) {
            if let service = selectedService {
                serviceImportSheetView(service: service)
            }
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
        let brandColor: Color = Color(hex: 0x1DB954)

        NavigationView {
            VStack(alignment: .leading, spacing: 16) {
                Text("Paste any Spotify playlist or album link to import all tracks directly into AURALIX:")
                    .font(.system(size: 13))
                    .foregroundColor(Color(hex: 0xCCCCCC))

                HStack {
                    TextField("https://open.spotify.com/playlist/... or song names", text: $importUrlInput)
                        .font(.system(size: 13))
                        .foregroundColor(.white)
                        .autocapitalization(.none)
                        .disableAutocorrection(true)

                    if !importUrlInput.isEmpty {
                        Button(action: { importUrlInput = "" }) {
                            Image(systemName: "xmark.circle.fill")
                                .foregroundColor(.gray)
                        }
                    }

                    Button(action: {
                        if let clip = UIPasteboard.general.string {
                            importUrlInput = clip
                        }
                    }) {
                        HStack(spacing: 4) {
                            Image(systemName: "doc.on.clipboard")
                            Text("Paste")
                        }
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundColor(brandColor)
                    }
                }
                .padding(12)
                .background(Color(hex: 0x1E202E))
                .cornerRadius(10)
                .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color.white.opacity(0.1), lineWidth: 1))

                if let prog = importProgress {
                    VStack(alignment: .leading, spacing: 6) {
                        if isImporting {
                            ProgressView()
                                .progressViewStyle(LinearProgressViewStyle(tint: brandColor))
                        }
                        Text(prog.isComplete ? "✓ Done! Matched \(prog.total) tracks" : (prog.error != nil ? "Error: \(prog.error!)" : prog.stage))
                            .font(.system(size: 12))
                            .foregroundColor(prog.error != nil ? Color.red : Color(hex: 0xAAAAAA))
                    }
                    .padding(.top, 4)
                }

                if let res = importedPlaylistResult {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("✓ Successfully Imported!")
                            .font(.system(size: 14, weight: .bold))
                            .foregroundColor(brandColor)
                        Text("\(res.title) • \(res.videos.count) tracks")
                            .font(.system(size: 12))
                            .foregroundColor(.white)

                        Button(action: {
                            selectedService = nil
                            onPlaylistClick(res)
                        }) {
                            Text("Open Playlist")
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
                    .overlay(RoundedRectangle(cornerRadius: 10).stroke(brandColor.opacity(0.3), lineWidth: 1))
                }

                Spacer()

                if importedPlaylistResult == nil {
                    Button(action: {
                        guard !importUrlInput.isEmpty && !isImporting else { return }
                        isImporting = true
                        importProgress = nil
                        importedPlaylistResult = nil
                        Task {
                            let res = await SpotifyImportEngine.shared.importPlaylist(from: importUrlInput) { prog in
                                DispatchQueue.main.async {
                                    self.importProgress = prog
                                }
                            }
                            await MainActor.run {
                                self.isImporting = false
                                if case .success(let pl) = res {
                                    self.importedPlaylistResult = pl
                                }
                            }
                        }
                    }) {
                        Text(isImporting ? "Importing..." : "Start Import")
                            .font(.system(size: 14, weight: .bold))
                            .foregroundColor(.black)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 14)
                            .background(importUrlInput.isEmpty || isImporting ? Color.gray : brandColor)
                            .cornerRadius(12)
                    }
                    .disabled(importUrlInput.isEmpty || isImporting)
                }
            }
            .padding(16)
            .background(Color(hex: 0x0E1019).ignoresSafeArea())
            .navigationTitle("Import from \(service)")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Close") { selectedService = nil }
                        .foregroundColor(.white)
                }
            }
        }
    }
}
