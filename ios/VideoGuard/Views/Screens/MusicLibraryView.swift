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
                        ForEach(["All", "Playlists", "Liked Songs", "Recently Played", "Made For You"], id: \.self) { chip in
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

                            Divider().background(Color.white.opacity(0.08))

                            // Apple Music
                            importServiceRow(
                                title: "Apple Music",
                                brandColor: Color(hex: 0xFA243C),
                                iconName: "music.quarternote.3"
                            ) {
                                openServiceImport(service: "Apple Music")
                            }

                            Divider().background(Color.white.opacity(0.08))

                            // Amazon Music
                            importServiceRow(
                                title: "Amazon Music",
                                brandColor: Color(hex: 0x00A8E1),
                                iconName: "headphones"
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

                        Text("By selecting a service above, you are choosing to transfer your followed artists, liked tracks, albums and playlists to AURALIX via TuneMyMusic or Direct Sync.")
                            .font(.system(size: 10))
                            .foregroundColor(Color(hex: 0x6E7180))
                            .lineSpacing(3)
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
                                Text("Create a new playlist or import from Spotify / Apple Music / Amazon Music")
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
                    Circle()
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
            case "Amazon Music": return Color(hex: 0x00A8E1)
            default: return AppTheme.ytMusicRedAccent
            }
        }()

        let authUrlStr: String = {
            switch service {
            case "Spotify": return "https://accounts.spotify.com/login"
            case "Apple Music": return "https://music.apple.com/login"
            case "Amazon Music": return "https://music.amazon.com"
            default: return "https://www.tunemymusic.com"
            }
        }()

        NavigationView {
            VStack(alignment: .leading, spacing: 16) {
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
                        service == "Apple Music" ? "https://music.apple.com/.../playlist/..." :
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

    var body: some View {
        NavigationView {
            VStack(spacing: 0) {
                InAppWKWebView(urlString: initialUrl, onUrlChange: { url in
                    currentUrl = url
                })

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
                            let urlToSync = currentUrl.isEmpty ? initialUrl : currentUrl
                            onSyncUrl(urlToSync)
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
            }
        }
    }
}

struct InAppWKWebView: UIViewRepresentable {
    let urlString: String
    let onUrlChange: (String) -> Void

    func makeCoordinator() -> Coordinator {
        Coordinator(onUrlChange: onUrlChange)
    }

    func makeUIView(context: Context) -> WKWebView {
        let config = WKWebViewConfiguration()
        let webView = WKWebView(frame: .zero, configuration: config)
        webView.navigationDelegate = context.coordinator
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
