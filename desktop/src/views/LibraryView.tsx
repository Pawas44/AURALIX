import React, { useState } from 'react'
import { Heart, Folder, ListMusic, Play, Trash2, FolderPlus } from 'lucide-react'
import { useLibraryStore } from '../store/useLibraryStore'
import { usePlayerStore } from '../store/usePlayerStore'

export const LibraryView: React.FC = () => {
  const [activeTab, setActiveTab] = useState<'liked' | 'playlists' | 'local'>('liked')

  const { likedSongs, playlists, localSongs, addLocalSongs, removeSongFromPlaylist } = useLibraryStore()
  const { playSong } = usePlayerStore()

  const handleImportFiles = async () => {
    if (window.auralixAPI?.openAudioFiles) {
      const files = await window.auralixAPI.openAudioFiles()
      if (files && files.length > 0) {
        const parsed = files.map((filePath: string, index: number) => {
          const fileName = filePath.split(/[/\\]/).pop() || 'Track'
          return {
            id: `local_${Date.now()}_${index}`,
            title: fileName.replace(/\.[^/.]+$/, ''),
            artist: 'Local Audio',
            duration: 180,
            durationFormatted: '3:00',
            artworkUrl: 'https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=300&q=80',
            isLocal: true,
            localPath: filePath
          }
        })
        addLocalSongs(parsed)
      }
    }
  }

  return (
    <div className="p-8 max-w-7xl mx-auto space-y-6 select-none overflow-y-auto">
      {/* Header Tabs */}
      <div className="flex items-center justify-between border-b border-white/[0.08] pb-4">
        <div className="flex items-center gap-3">
          <button
            onClick={() => setActiveTab('liked')}
            className={`flex items-center gap-2 px-4 py-2 rounded-xl text-xs font-semibold transition-all ${
              activeTab === 'liked'
                ? 'bg-purple-600 text-white shadow-md shadow-purple-600/30'
                : 'bg-white/[0.04] text-gray-400 hover:text-white'
            }`}
          >
            <Heart className="w-4 h-4 fill-pink-500 text-pink-500" />
            <span>Liked Songs ({likedSongs.length})</span>
          </button>

          <button
            onClick={() => setActiveTab('local')}
            className={`flex items-center gap-2 px-4 py-2 rounded-xl text-xs font-semibold transition-all ${
              activeTab === 'local'
                ? 'bg-purple-600 text-white shadow-md shadow-purple-600/30'
                : 'bg-white/[0.04] text-gray-400 hover:text-white'
            }`}
          >
            <Folder className="w-4 h-4 text-emerald-400" />
            <span>Local Files ({localSongs.length})</span>
          </button>

          <button
            onClick={() => setActiveTab('playlists')}
            className={`flex items-center gap-2 px-4 py-2 rounded-xl text-xs font-semibold transition-all ${
              activeTab === 'playlists'
                ? 'bg-purple-600 text-white shadow-md shadow-purple-600/30'
                : 'bg-white/[0.04] text-gray-400 hover:text-white'
            }`}
          >
            <ListMusic className="w-4 h-4 text-indigo-400" />
            <span>Playlists ({playlists.length})</span>
          </button>
        </div>

        {activeTab === 'local' && (
          <button
            onClick={handleImportFiles}
            className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-emerald-600/20 hover:bg-emerald-600/30 text-emerald-300 border border-emerald-500/30 text-xs font-medium transition-all"
          >
            <FolderPlus className="w-4 h-4" />
            <span>Add Audio Files</span>
          </button>
        )}
      </div>

      {/* Tab Content */}
      {activeTab === 'liked' && (
        <div className="space-y-2">
          {likedSongs.length === 0 ? (
            <div className="text-center py-24 text-gray-500 text-sm">
              <Heart className="w-12 h-12 text-gray-700 mx-auto mb-3" />
              <span>No liked songs yet. Click the heart icon while listening to save tracks!</span>
            </div>
          ) : (
            likedSongs.map((song, i) => (
              <div
                key={song.id}
                onClick={() => playSong(song, likedSongs)}
                className="flex items-center justify-between p-3 rounded-xl hover:bg-white/[0.04] transition-all cursor-pointer group"
              >
                <div className="flex items-center gap-3.5 min-w-0">
                  <span className="text-xs text-gray-500 font-mono w-5">{i + 1}</span>
                  <img
                    src={song.artworkUrl}
                    alt={song.title}
                    className="w-11 h-11 rounded-lg object-cover"
                  />
                  <div className="truncate">
                    <div className="text-sm font-semibold text-white truncate">{song.title}</div>
                    <div className="text-xs text-gray-400 truncate">{song.artist}</div>
                  </div>
                </div>

                <div className="flex items-center gap-4 text-xs text-gray-400 font-mono">
                  <span>{song.durationFormatted}</span>
                  <button
                    onClick={(e) => {
                      e.stopPropagation()
                      useLibraryStore.getState().toggleLike(song)
                    }}
                    className="p-1 hover:text-red-400 transition-colors"
                  >
                    <Trash2 className="w-4 h-4" />
                  </button>
                </div>
              </div>
            ))
          )}
        </div>
      )}

      {activeTab === 'local' && (
        <div className="space-y-2">
          {localSongs.length === 0 ? (
            <div className="text-center py-24 text-gray-500 text-sm">
              <Folder className="w-12 h-12 text-gray-700 mx-auto mb-3" />
              <span>No local files imported yet. Click "Add Audio Files" above to play MP3, FLAC, M4A or WAV from your PC!</span>
            </div>
          ) : (
            localSongs.map((song, i) => (
              <div
                key={song.id}
                onClick={() => playSong(song, localSongs)}
                className="flex items-center justify-between p-3 rounded-xl hover:bg-white/[0.04] transition-all cursor-pointer group"
              >
                <div className="flex items-center gap-3.5 min-w-0">
                  <span className="text-xs text-gray-500 font-mono w-5">{i + 1}</span>
                  <div className="w-11 h-11 rounded-lg bg-emerald-950/60 border border-emerald-500/20 flex items-center justify-center">
                    <Folder className="w-5 h-5 text-emerald-400" />
                  </div>
                  <div className="truncate">
                    <div className="text-sm font-semibold text-white truncate">{song.title}</div>
                    <div className="text-xs text-gray-400 truncate">{song.localPath}</div>
                  </div>
                </div>

                <div className="flex items-center gap-4 text-xs text-gray-400 font-mono">
                  <Play className="w-4 h-4 text-purple-400 fill-current opacity-0 group-hover:opacity-100 transition-opacity" />
                  <span>Local</span>
                </div>
              </div>
            ))
          )}
        </div>
      )}

      {activeTab === 'playlists' && (
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
          {playlists.map((pl) => (
            <div
              key={pl.id}
              className="bg-[#13141F] border border-white/[0.06] rounded-2xl p-4 space-y-3"
            >
              <div className="aspect-square bg-purple-900/30 border border-purple-500/20 rounded-xl flex items-center justify-center">
                <ListMusic className="w-10 h-10 text-purple-400" />
              </div>
              <div>
                <h4 className="text-sm font-bold text-white truncate">{pl.title}</h4>
                <p className="text-xs text-gray-400">{pl.tracks.length} tracks</p>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
