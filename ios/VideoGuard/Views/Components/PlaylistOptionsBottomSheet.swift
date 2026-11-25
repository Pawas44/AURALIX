import SwiftUI

struct PlaylistOptionsBottomSheet: View {
    let playlist: UserPlaylist
    let onDownload: () -> Void
    let onClear: () -> Void
    let onDelete: () -> Void
    let onShare: () -> Void
    let onDismiss: () -> Void
    
    @State private var showDeleteConfirmation = false
    @State private var showClearConfirmation = false
    
    var body: some View {
        VStack(spacing: 0) {
            // Drag Handle
            Capsule()
                .fill(Color.white.opacity(0.25))
                .frame(width: 36, height: 4)
                .padding(.top, 10)
                .padding(.bottom, 16)
            
            // Playlist Header Info
            HStack(spacing: 14) {
                // Artwork / Icon
                let coverUrl = playlist.customCoverUrl ?? (playlist.thumbnailUrl.isEmpty ? (playlist.videos.first?.thumbnailUrl ?? "") : playlist.thumbnailUrl)
                if !coverUrl.isEmpty {
                    AsyncImage(url: URL(string: coverUrl)) { img in
                        img.resizable().aspectRatio(contentMode: .fill)
                    } placeholder: {
                        Rectangle().fill(Color(hex: 0xFF2A2A32))
                    }
                    .frame(width: 56, height: 56)
                    .cornerRadius(8)
                    .clipped()
                } else {
                    ZStack {
                        RoundedRectangle(cornerRadius: 8)
                            .fill(LinearGradient(
                                colors: [Color(hex: 0xFF4C1D95), Color(hex: 0xFF1E1B4B)],
                                startPoint: .topLeading,
                                endPoint: .bottomTrailing
                            ))
                        Image(systemName: playlist.iconType == "like" ? "heart.fill" : "music.note.list")
                            .font(.system(size: 24))
                            .foregroundColor(Color(hex: 0xFFC4B5FD))
                    }
                    .frame(width: 56, height: 56)
                }
                
                VStack(alignment: .leading, spacing: 4) {
                    Text(playlist.title)
                        .font(.system(size: 17, weight: .semibold))
                        .foregroundColor(.white)
                        .lineLimit(1)
                    
                    Text("\(playlist.videos.count) songs")
                        .font(.system(size: 13, weight: .regular))
                        .foregroundColor(Color(hex: 0xFF9E9EA7))
                }
                
                Spacer()
            }
            .padding(.horizontal, 20)
            .padding(.bottom, 18)
            
            // Divider
            Divider()
                .background(Color.white.opacity(0.08))
                .padding(.horizontal, 20)
            
            // Actions List
            VStack(spacing: 2) {
                // Action 1: Download Songs
                Button(action: {
                    onDownload()
                    onDismiss()
                }) {
                    HStack(spacing: 16) {
                        Image(systemName: "arrow.down.circle")
                            .font(.system(size: 20, weight: .light))
                            .foregroundColor(.white)
                            .frame(width: 28, height: 28)
                        
                        Text("Download Songs")
                            .font(.system(size: 15, weight: .medium))
                            .foregroundColor(.white)
                        
                        Spacer()
                    }
                    .padding(.horizontal, 20)
                    .padding(.vertical, 14)
                    .contentShape(Rectangle())
                }
                .buttonStyle(PlainButtonStyle())
                
                // Thin divider
                Divider()
                    .background(Color.white.opacity(0.05))
                    .padding(.leading, 64)
                    .padding(.trailing, 20)
                
                // Action 2: Clear Songs
                Button(action: {
                    showClearConfirmation = true
                }) {
                    HStack(spacing: 16) {
                        Image(systemName: "minus.circle")
                            .font(.system(size: 20, weight: .light))
                            .foregroundColor(.white)
                            .frame(width: 28, height: 28)
                        
                        Text("Clear Songs")
                            .font(.system(size: 15, weight: .medium))
                            .foregroundColor(.white)
                        
                        Spacer()
                    }
                    .padding(.horizontal, 20)
                    .padding(.vertical, 14)
                    .contentShape(Rectangle())
                }
                .buttonStyle(PlainButtonStyle())
                
                // Thin divider
                Divider()
                    .background(Color.white.opacity(0.05))
                    .padding(.leading, 64)
                    .padding(.trailing, 20)
                
                // Action 3: Delete Playlist (if not system)
                if !playlist.isSystem {
                    Button(action: {
                        showDeleteConfirmation = true
                    }) {
                        HStack(spacing: 16) {
                            Image(systemName: "trash")
                                .font(.system(size: 20, weight: .light))
                                .foregroundColor(Color(hex: 0xFFF87171))
                                .frame(width: 28, height: 28)
                            
                            Text("Delete Playlist")
                                .font(.system(size: 15, weight: .medium))
                                .foregroundColor(Color(hex: 0xFFF87171))
                            
                            Spacer()
                        }
                        .padding(.horizontal, 20)
                        .padding(.vertical, 14)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(PlainButtonStyle())
                    
                    // Thin divider
                    Divider()
                        .background(Color.white.opacity(0.05))
                        .padding(.leading, 64)
                        .padding(.trailing, 20)
                }
                
                // Action 4: Share Playlist
                Button(action: {
                    onShare()
                    onDismiss()
                }) {
                    HStack(spacing: 16) {
                        Image(systemName: "square.and.arrow.up")
                            .font(.system(size: 20, weight: .light))
                            .foregroundColor(.white)
                            .frame(width: 28, height: 28)
                        
                        Text("Share Playlist")
                            .font(.system(size: 15, weight: .medium))
                            .foregroundColor(.white)
                        
                        Spacer()
                    }
                    .padding(.horizontal, 20)
                    .padding(.vertical, 14)
                    .contentShape(Rectangle())
                }
                .buttonStyle(PlainButtonStyle())
            }
            .padding(.top, 6)
            
            Spacer().frame(height: 24)
        }
        .background(
            Color(hex: 0xFF14131A)
                .ignoresSafeArea()
        )
        .cornerRadius(24, corners: [.topLeft, .topRight])
        .alert("Clear Playlist?", isPresented: $showClearConfirmation) {
            Button("Cancel", role: .cancel) {}
            Button("Clear All", role: .destructive) {
                onClear()
                onDismiss()
            }
        } message: {
            Text("Are you sure you want to remove all songs from \"\(playlist.title)\"?")
        }
        .alert("Delete Playlist?", isPresented: $showDeleteConfirmation) {
            Button("Cancel", role: .cancel) {}
            Button("Delete", role: .destructive) {
                onDelete()
                onDismiss()
            }
        } message: {
            Text("Are you sure you want to delete \"\(playlist.title)\"? This action cannot be undone.")
        }
    }
}

// RoundedCorner helper
private struct RoundedCorner: Shape {
    var radius: CGFloat = .infinity
    var corners: UIRectCorner = .allCorners

    func path(in rect: CGRect) -> Path {
        let path = UIBezierPath(roundedRect: rect, byRoundingCorners: corners, cornerRadii: CGSize(width: radius, height: radius))
        return Path(path.cgPath)
    }
}

private extension View {
    func cornerRadius(_ radius: CGFloat, corners: UIRectCorner) -> some View {
        clipShape(RoundedCorner(radius: radius, corners: corners))
    }
}
