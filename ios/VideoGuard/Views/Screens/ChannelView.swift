import SwiftUI

struct ChannelView: View {
    let channelName: String
    let channelAvatarUrl: String
    var subscriberCount: String = "10M subscribers"
    let onBack: () -> Void
    let onVideoSelected: (VideoItem, [VideoItem]) -> Void
    let onPlaylistClick: (UserPlaylist) -> Void

    @ObservedObject var userData = UserDataRepository.shared

    @State private var selectedTab = "Videos"
    @State private var channelVideos: [VideoItem] = []
    @State private var videosToken: String? = nil
    @State private var isLoadingVideos = true
    @State private var isLoadingMoreVideos = false

    @State private var channelPlaylists: [UserPlaylist] = []
    @State private var playlistsToken: String? = nil
    @State private var isLoadingPlaylists = false

    @State private var channelShorts: [VideoItem] = []
    @State private var shortsToken: String? = nil
    @State private var isLoadingShorts = false

    private let tabs = ["Videos", "Shorts", "Playlists"]

    var body: some View {
        let isSubscribed = userData.isSubscribed(channelName)

        VStack(spacing: 0) {
            // Top Header
            HStack(spacing: 12) {
                Button(action: onBack) {
                    Image(systemName: "chevron.left")
                        .font(.system(size: 18, weight: .bold))
                        .foregroundColor(.white)
                        .padding(8)
                }

                Text(channelName)
                    .font(.system(size: 18, weight: .bold))
                    .foregroundColor(.white)
                    .lineLimit(1)

                Spacer()
            }
            .padding(.horizontal, 8)
            .padding(.vertical, 6)

            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    // Channel Banner + Profile
                    VStack(spacing: 12) {
                        LinearGradient(
                            colors: [Color(hex: 0x3A2040), Color(hex: 0x161B2E)],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        )
                        .frame(height: 96)
                        .overlay(
                            Text(channelName.uppercased())
                                .font(.system(size: 20, weight: .heavy))
                                .foregroundColor(.white.opacity(0.25))
                        )

                        HStack(spacing: 14) {
                            AsyncImage(url: URL(string: channelAvatarUrl)) { img in
                                img.resizable().aspectRatio(contentMode: .fill)
                            } placeholder: {
                                Circle().fill(Color.gray.opacity(0.4))
                            }
                            .frame(width: 68, height: 68)
                            .clipShape(Circle())

                            VStack(alignment: .leading, spacing: 4) {
                                Text(channelName)
                                    .font(.system(size: 20, weight: .bold))
                                    .foregroundColor(.white)
                                Text(subscriberCount)
                                    .font(.system(size: 13))
                                    .foregroundColor(AppTheme.youTubeTextSecondary)
                            }
                            Spacer()
                        }
                        .padding(.horizontal, 16)

                        Button(action: { userData.toggleSubscription(channelName) }) {
                            Text(isSubscribed ? "Subscribed" : "Subscribe")
                                .font(.system(size: 14, weight: .bold))
                                .foregroundColor(isSubscribed ? .white : .black)
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 10)
                                .background(isSubscribed ? Color.white.opacity(0.15) : Color.white)
                                .cornerRadius(22)
                        }
                        .padding(.horizontal, 16)
                    }

                    // Tabs
                    HStack(spacing: 0) {
                        ForEach(tabs, id: \.self) { tab in
                            Button(action: { selectedTab = tab }) {
                                VStack(spacing: 8) {
                                    Text(tab)
                                        .font(.system(size: 14, weight: selectedTab == tab ? .bold : .medium))
                                        .foregroundColor(selectedTab == tab ? .white : AppTheme.youTubeTextSecondary)
                                    Rectangle()
                                        .fill(selectedTab == tab ? Color.white : Color.clear)
                                        .frame(height: 2)
                                }
                                .frame(maxWidth: .infinity)
                            }
                        }
                    }
                    .padding(.top, 16)

                    Divider().background(Color.white.opacity(0.1))

                    // Tab Content
                    if selectedTab == "Videos" {
                        if isLoadingVideos && channelVideos.isEmpty {
                            HStack {
                                Spacer()
                                ProgressView()
                                    .progressViewStyle(CircularProgressViewStyle(tint: .white))
                                    .padding(40)
                                Spacer()
                            }
                        } else {
                            LazyVStack(spacing: 14) {
                                ForEach(Array(channelVideos.enumerated()), id: \.element.id) { idx, vid in
                                    YouTubeVideoCard(
                                        video: vid,
                                        onClick: { onVideoSelected(vid, channelVideos) }
                                    )
                                    .onAppear {
                                        if idx >= channelVideos.count - 3 {
                                            loadMoreVideos()
                                        }
                                    }
                                }
                                if isLoadingMoreVideos {
                                    ProgressView()
                                        .progressViewStyle(CircularProgressViewStyle(tint: .white))
                                        .padding()
                                }
                            }
                            .padding(.top, 10)
                            .padding(.bottom, 80)
                        }
                    } else if selectedTab == "Shorts" {
                        if isLoadingShorts && channelShorts.isEmpty {
                            HStack {
                                Spacer()
                                ProgressView()
                                    .progressViewStyle(CircularProgressViewStyle(tint: .white))
                                    .padding(40)
                                Spacer()
                            }
                        } else if channelShorts.isEmpty {
                            Text("No Shorts found for this channel.")
                                .foregroundColor(AppTheme.youTubeTextSecondary)
                                .padding(32)
                        } else {
                            LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 10) {
                                ForEach(channelShorts) { shortVid in
                                    Button(action: { onVideoSelected(shortVid, channelShorts) }) {
                                        ZStack(alignment: .bottomLeading) {
                                            AsyncImage(url: URL(string: shortVid.thumbnailUrl)) { img in
                                                img.resizable().aspectRatio(contentMode: .fill)
                                            } placeholder: {
                                                Rectangle().fill(AppTheme.youTubeCardBg)
                                            }
                                            .frame(height: 240)
                                            .cornerRadius(10)
                                            .clipped()

                                            Text(shortVid.title)
                                                .font(.system(size: 12, weight: .semibold))
                                                .foregroundColor(.white)
                                                .lineLimit(2)
                                                .padding(8)
                                                .frame(maxWidth: .infinity, alignment: .leading)
                                                .background(Color.black.opacity(0.55))
                                        }
                                    }
                                }
                            }
                            .padding(12)
                            .padding(.bottom, 80)
                        }
                    } else {
                        if isLoadingPlaylists && channelPlaylists.isEmpty {
                            HStack {
                                Spacer()
                                ProgressView()
                                    .progressViewStyle(CircularProgressViewStyle(tint: .white))
                                    .padding(40)
                                Spacer()
                            }
                        } else if channelPlaylists.isEmpty {
                            Text("No public playlists found.")
                                .foregroundColor(AppTheme.youTubeTextSecondary)
                                .padding(32)
                        } else {
                            LazyVStack(spacing: 12) {
                                ForEach(channelPlaylists) { pl in
                                    Button(action: { onPlaylistClick(pl) }) {
                                        HStack(spacing: 12) {
                                            AsyncImage(url: URL(string: pl.thumbnailUrl)) { img in
                                                img.resizable().aspectRatio(16/9, contentMode: .fill)
                                            } placeholder: {
                                                Rectangle().fill(AppTheme.youTubeCardBg)
                                            }
                                            .frame(width: 120, height: 68)
                                            .cornerRadius(8)
                                            .clipped()

                                            VStack(alignment: .leading, spacing: 4) {
                                                Text(pl.title)
                                                    .font(.system(size: 14, weight: .semibold))
                                                    .foregroundColor(.white)
                                                    .lineLimit(2)
                                                Text(pl.description.isEmpty ? "Playlist" : pl.description)
                                                    .font(.system(size: 12))
                                                    .foregroundColor(AppTheme.youTubeTextSecondary)
                                            }
                                            Spacer()
                                        }
                                        .padding(.horizontal, 14)
                                    }
                                }
                            }
                            .padding(.top, 12)
                            .padding(.bottom, 80)
                        }
                    }
                }
            }
        }
        .background(LiquidGlassDefaults.liquidCanvas.ignoresSafeArea())
        .task(id: channelName) {
            isLoadingVideos = true
            let (vids, token) = await YouTubeSyncEngine.shared.fetchChannelVideos(
                channelNameOrId: channelName,
                continuationToken: nil,
                fallbackAvatar: channelAvatarUrl
            )
            channelVideos = vids
            videosToken = token
            isLoadingVideos = false

            isLoadingPlaylists = true
            let (pls, pTok) = await YouTubeSyncEngine.shared.fetchChannelPlaylists(channelNameOrId: channelName)
            channelPlaylists = pls
            playlistsToken = pTok
            isLoadingPlaylists = false
        }
        .onChange(of: selectedTab) { newTab in
            if newTab == "Shorts" && channelShorts.isEmpty && !isLoadingShorts {
                isLoadingShorts = true
                Task {
                    let (shorts, sTok) = await YouTubeSyncEngine.shared.fetchChannelShorts(
                        channelNameOrId: channelName,
                        continuationToken: nil,
                        fallbackAvatar: channelAvatarUrl
                    )
                    await MainActor.run {
                        channelShorts = shorts
                        shortsToken = sTok
                        isLoadingShorts = false
                    }
                }
            }
        }
    }

    private func loadMoreVideos() {
        guard !isLoadingVideos && !isLoadingMoreVideos, let token = videosToken, !token.isEmpty else { return }
        isLoadingMoreVideos = true
        Task {
            let (more, nextTok) = await YouTubeSyncEngine.shared.fetchChannelVideos(
                channelNameOrId: channelName,
                continuationToken: token,
                fallbackAvatar: channelAvatarUrl
            )
            await MainActor.run {
                let existing = Set(channelVideos.map { $0.id })
                let fresh = more.filter { !existing.contains($0.id) }
                channelVideos.append(contentsOf: fresh)
                videosToken = nextTok
                isLoadingMoreVideos = false
            }
        }
    }
}
