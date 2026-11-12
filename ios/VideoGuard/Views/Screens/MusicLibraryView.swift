import SwiftUI

struct MusicLibraryView: View {
    let onTrackSelected: (VideoItem, [VideoItem]) -> Void
    let onPlaylistClick: (UserPlaylist) -> Void
    let onOpenSearch: () -> Void
    let onOpenAccountSheet: () -> Void

    @ObservedObject var userData = UserDataRepository.shared
    @State private var showNewPlaylistAlert = false
    @State private var newPlaylistTitle = ""

    @State private var showImportSheet = false
    @State private var importUrlInput = ""
    @State private var importProgress: ImportProgress? = nil
    @State private var isImporting = false
    @State private var importedPlaylistResult: UserPlaylist? = nil

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
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

                // Recent Music Activity
                if !userData.watchHistory.isEmpty {
                    VStack(alignment: .leading, spacing: 10) {
                        Text("Recent activity")
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
                                        }
                                    }
                                }
                            }
                            .padding(.horizontal, 16)
                        }
                    }
                }

                // Spotify / Amazon Music Import Banner
                Button(action: { showImportSheet = true }) {
                    HStack(spacing: 14) {
                        ZStack {
                            Circle()
                                .fill(Color(hex: 0x1DB954, alpha: 0.2))
                                .frame(width: 40, height: 40)
                            Image(systemName: "link.badge.plus")
                                .font(.system(size: 18, weight: .semibold))
                                .foregroundColor(Color(hex: 0x1DB954))
                        }

                        VStack(alignment: .leading, spacing: 2) {
                            Text("Import from Spotify / Amazon Music")
                                .font(.system(size: 14, weight: .bold))
                                .foregroundColor(.white)
                            Text("Paste playlist link to sync free into AURALIX")
                                .font(.system(size: 11))
                                .foregroundColor(Color(hex: 0xAAAAAA))
                        }

                        Spacer()

                        Image(systemName: "chevron.right")
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundColor(Color(hex: 0x888888))
                    }
                    .padding(.horizontal, 14)
                    .padding(.vertical, 12)
                    .background(Color(hex: 0x161826))
                    .cornerRadius(12)
                    .overlay(
                        RoundedRectangle(cornerRadius: 12)
                            .stroke(Color(hex: 0x1DB954).opacity(0.4), lineWidth: 1)
                    )
                }
                .padding(.horizontal, 16)

                // Playlists List
                VStack(alignment: .leading, spacing: 12) {
                    HStack {
                        Text("Playlists")
                            .font(.system(size: 17, weight: .bold))
                            .foregroundColor(.white)
                        Spacer()
                        Button(action: { showNewPlaylistAlert = true }) {
                            Label("New", systemImage: "plus")
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
                                .background(AppTheme.ytMusicRed)
                                .cornerRadius(20)
                            }
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 24)
                    } else {
                        LazyVStack(spacing: 12) {
                            ForEach(userData.playlists) { playlist in
                                Button(action: { onPlaylistClick(playlist) }) {
                                    HStack(spacing: 14) {
                                        if !playlist.thumbnailUrl.isEmpty {
                                            AsyncImage(url: URL(string: playlist.thumbnailUrl)) { img in
                                                img.resizable().aspectRatio(contentMode: .fill)
                                            } placeholder: {
                                                Rectangle().fill(AppTheme.ytMusicCardBg)
                                            }
                                            .frame(width: 60, height: 60)
                                            .cornerRadius(8)
                                            .clipped()
                                        } else {
                                            ZStack {
                                                RoundedRectangle(cornerRadius: 8)
                                                    .fill(Color.white.opacity(0.08))
                                                Image(systemName: playlist.iconType == "like" ? "hand.thumbsup.fill" : "music.note.list")
                                                    .font(.system(size: 22))
                                                    .foregroundColor(.white)
                                            }
                                            .frame(width: 60, height: 60)
                                        }

                                        VStack(alignment: .leading, spacing: 3) {
                                            Text(playlist.title)
                                                .font(.system(size: 15, weight: .semibold))
                                                .foregroundColor(.white)
                                            Text("\(playlist.videoCount) tracks")
                                                .font(.system(size: 12))
                                                .foregroundColor(AppTheme.ytMusicTextSecondary)
                                        }
                                        Spacer()
                                    }
                                    .padding(.horizontal, 16)
                                }
                            }
                        }
                    }
                }
                .padding(.bottom, 80)
            }
        }
        .alert("New Playlist", isPresented: $showNewPlaylistAlert) {
            TextField("Title", text: $newPlaylistTitle)
            Button("Create") {
                if !newPlaylistTitle.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                    userData.createPlaylist(title: newPlaylistTitle.trimmingCharacters(in: .whitespacesAndNewlines))
                    newPlaylistTitle = ""
                }
            }
            Button("Cancel", role: .cancel) { newPlaylistTitle = "" }
        }
        .sheet(isPresented: $showImportSheet) {
            NavigationView {
                VStack(alignment: .leading, spacing: 16) {
                    Text("Paste any Spotify or Amazon Music playlist/track link, or enter song titles:")
                        .font(.system(size: 13))
                        .foregroundColor(Color(hex: 0xCCCCCC))

                    VStack(alignment: .trailing, spacing: 8) {
                        TextField("https://open.spotify.com/playlist/...", text: $importUrlInput)
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
                            .foregroundColor(Color(hex: 0x1DB954))
                        }
                    }

                    if let prog = importProgress {
                        VStack(alignment: .leading, spacing: 6) {
                            if isImporting {
                                ProgressView(value: prog.total > 0 ? Double(prog.current) / Double(prog.total) : 0)
                                    .tint(Color(hex: 0x1DB954))
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
                                .foregroundColor(Color(hex: 0x1DB954))
                            Text("\(res.title) • \(res.videoCount) tracks")
                                .font(.system(size: 13))
                                .foregroundColor(.white)

                            Button(action: {
                                showImportSheet = false
                                onPlaylistClick(res)
                            }) {
                                Text("Play Playlist Now")
                                    .font(.system(size: 14, weight: .bold))
                                    .foregroundColor(.black)
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, 10)
                                    .background(Color(hex: 0x1DB954))
                                    .cornerRadius(8)
                            }
                            .padding(.top, 6)
                        }
                        .padding(12)
                        .background(Color(hex: 0x1DB954, alpha: 0.15))
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
                            Text(isImporting ? "Importing..." : "Start Import")
                                .font(.system(size: 16, weight: .bold))
                                .foregroundColor(.black)
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 14)
                                .background(importUrlInput.isEmpty || isImporting ? Color.gray : Color(hex: 0x1DB954))
                                .cornerRadius(12)
                        }
                        .disabled(importUrlInput.isEmpty || isImporting)
                    }
                }
                .padding(20)
                .background(Color(hex: 0x10121C).ignoresSafeArea())
                .navigationTitle("Import Playlist")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("Close") {
                            showImportSheet = false
                            importProgress = nil
                            importedPlaylistResult = nil
                        }
                        .foregroundColor(.white)
                    }
                }
            }
        }
    }
}
