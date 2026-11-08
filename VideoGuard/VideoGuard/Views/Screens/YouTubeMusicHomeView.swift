import SwiftUI

struct YouTubeMusicHomeView: View {
    let onSearchClick: () -> Void
    let onProfileClick: () -> Void
    let onTrackClick: (VideoItem) -> Void

    @State private var selectedMood: String = "Energize"

    var body: some View {
        let musicCategories = VideoRepository.musicCategories
        let musicTracks = VideoRepository.getMusicByMood(selectedMood)
        let allMusicTracks = VideoRepository.getMusicTracks()

        VStack(spacing: 0) {
            YouTubeTopBar(
                appMode: .youtubeMusic,
                onSearchClick: onSearchClick,
                onProfileClick: onProfileClick
            )

            // Mood Chips
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(musicCategories, id: \.self) { mood in
                        let isSelected = mood == selectedMood
                        Button(action: { selectedMood = mood }) {
                            Text(mood)
                                .font(.system(size: 13, weight: .medium))
                                .foregroundColor(isSelected ? AppTheme.ytMusicChipActiveText : AppTheme.ytMusicTextPrimary)
                                .padding(.horizontal, 16)
                                .padding(.vertical, 8)
                                .background(
                                    RoundedRectangle(cornerRadius: 20)
                                        .fill(isSelected ? AppTheme.ytMusicChipActiveBg : AppTheme.ytMusicChipBg)
                                )
                        }
                    }
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 8)
            }

            // Main Feed
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    // Quick picks
                    Text("Quick picks")
                        .font(.system(size: 22, weight: .bold))
                        .foregroundColor(AppTheme.ytMusicTextPrimary)
                        .padding(.horizontal, 16)
                        .padding(.top, 8)

                    VStack(spacing: 8) {
                        ForEach(musicTracks.prefix(4)) { track in
                            MusicTrackRow(track: track) {
                                onTrackClick(track)
                            }
                        }
                    }

                    // Listen again horizontal scroll
                    Text("Listen again")
                        .font(.system(size: 22, weight: .bold))
                        .foregroundColor(AppTheme.ytMusicTextPrimary)
                        .padding(.horizontal, 16)
                        .padding(.top, 12)

                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 14) {
                            ForEach(allMusicTracks) { track in
                                MusicAlbumCard(track: track) {
                                    onTrackClick(track)
                                }
                            }
                        }
                        .padding(.horizontal, 16)
                    }

                    // Recommended
                    Text("Recommended")
                        .font(.system(size: 22, weight: .bold))
                        .foregroundColor(AppTheme.ytMusicTextPrimary)
                        .padding(.horizontal, 16)
                        .padding(.top, 12)

                    VStack(spacing: 8) {
                        ForEach(allMusicTracks.reversed()) { track in
                            MusicTrackRow(track: track) {
                                onTrackClick(track)
                            }
                        }
                    }
                }
                .padding(.bottom, 100)
            }
        }
        .background(AppTheme.ytMusicBlack.ignoresSafeArea())
    }
}

struct MusicTrackRow: View {
    let track: VideoItem
    let onClick: () -> Void

    var body: some View {
        Button(action: onClick) {
            HStack(spacing: 12) {
                AsyncImage(url: URL(string: track.thumbnailUrl)) { image in
                    image.resizable().aspectRatio(contentMode: .fill)
                } placeholder: {
                    RoundedRectangle(cornerRadius: 6).fill(Color.gray.opacity(0.3))
                }
                .frame(width: 48, height: 48)
                .cornerRadius(6)
                .clipped()

                VStack(alignment: .leading, spacing: 3) {
                    Text(track.title)
                        .font(.system(size: 14, weight: .medium))
                        .foregroundColor(AppTheme.ytMusicTextPrimary)
                        .lineLimit(1)
                    Text("\(track.artist) • \(track.album)")
                        .font(.system(size: 12))
                        .foregroundColor(AppTheme.ytMusicTextSecondary)
                        .lineLimit(1)
                }

                Spacer()

                Text(track.duration)
                    .font(.system(size: 12))
                    .foregroundColor(AppTheme.ytMusicTextSecondary)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 4)
        }
    }
}

struct MusicAlbumCard: View {
    let track: VideoItem
    let onClick: () -> Void

    var body: some View {
        Button(action: onClick) {
            VStack(alignment: .leading, spacing: 6) {
                AsyncImage(url: URL(string: track.thumbnailUrl)) { image in
                    image.resizable().aspectRatio(contentMode: .fill)
                } placeholder: {
                    RoundedRectangle(cornerRadius: 8).fill(Color.gray.opacity(0.3))
                }
                .frame(width: 140, height: 140)
                .cornerRadius(8)
                .clipped()

                Text(track.title)
                    .font(.system(size: 13, weight: .medium))
                    .foregroundColor(AppTheme.ytMusicTextPrimary)
                    .lineLimit(2)
                    .frame(width: 140, alignment: .leading)

                Text(track.artist)
                    .font(.system(size: 12))
                    .foregroundColor(AppTheme.ytMusicTextSecondary)
                    .lineLimit(1)
            }
        }
    }
}

