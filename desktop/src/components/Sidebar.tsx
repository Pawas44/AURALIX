import React from 'react'
import {
  Home,
  Search,
  Library,
  Heart,
  Folder,
  Sliders,
  Mic2,
  Send,
  Plus
} from 'lucide-react'
import { usePlayerStore } from '../store/usePlayerStore'
import { useLibraryStore } from '../store/useLibraryStore'

interface SidebarProps {
  currentView: 'home' | 'search' | 'library' | 'settings'
  setCurrentView: (view: 'home' | 'search' | 'library' | 'settings') => void
}

export const Sidebar: React.FC<SidebarProps> = ({ currentView, setCurrentView }) => {
  const { showEqualizer, setShowEqualizer, showLyrics, setShowLyrics, equalizer } = usePlayerStore()
  const { playlists, createPlaylist } = useLibraryStore()

  const handleCreatePlaylist = () => {
    const title = prompt('Enter playlist name:', 'My Chill Mix')
    if (title) {
      createPlaylist(title)
    }
  }

  const handleOpenLocalFiles = async () => {
    if (window.auralixAPI?.openAudioFiles) {
      const files = await window.auralixAPI.openAudioFiles()
      if (files && files.length > 0) {
        const localSongs = files.map((filePath: string, index: number) => {
          const fileName = filePath.split(/[/\\]/).pop() || 'Track'
          return {
            id: `local_${Date.now()}_${index}`,
            title: fileName.replace(/\.[^/.]+$/, ''),
            artist: 'Local File',
            duration: 180,
            durationFormatted: '3:00',
            artworkUrl: 'https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=300&q=80',
            isLocal: true,
            localPath: filePath
          }
        })
        useLibraryStore.getState().addLocalSongs(localSongs)
        setCurrentView('library')
      }
    }
  }

  return (
    <aside className="w-60 bg-[#0E0F16] border-r border-white/[0.06] flex flex-col justify-between p-3 select-none">
      <div className="space-y-6">
        {/* Navigation list */}
        <div className="space-y-1">
          <button
            onClick={() => setCurrentView('home')}
            className={`w-full flex items-center gap-3 px-3.5 py-2.5 rounded-xl text-sm font-medium transition-all ${
              currentView === 'home'
                ? 'bg-purple-600 text-white shadow-lg shadow-purple-600/30'
                : 'text-gray-400 hover:text-white hover:bg-white/[0.05]'
            }`}
          >
            <Home className="w-4 h-4" />
            <span>Home</span>
          </button>

          <button
            onClick={() => setCurrentView('search')}
            className={`w-full flex items-center gap-3 px-3.5 py-2.5 rounded-xl text-sm font-medium transition-all ${
              currentView === 'search'
                ? 'bg-purple-600 text-white shadow-lg shadow-purple-600/30'
                : 'text-gray-400 hover:text-white hover:bg-white/[0.05]'
            }`}
          >
            <Search className="w-4 h-4" />
            <span>Explore & Search</span>
          </button>

          <button
            onClick={() => setCurrentView('library')}
            className={`w-full flex items-center gap-3 px-3.5 py-2.5 rounded-xl text-sm font-medium transition-all ${
              currentView === 'library'
                ? 'bg-purple-600 text-white shadow-lg shadow-purple-600/30'
                : 'text-gray-400 hover:text-white hover:bg-white/[0.05]'
            }`}
          >
            <Library className="w-4 h-4" />
            <span>Library</span>
          </button>
        </div>

        {/* Audio Tools & Quick Toggles */}
        <div>
          <div className="px-3.5 mb-2 text-[11px] font-semibold text-gray-500 uppercase tracking-wider">
            Sound Tools
          </div>
          <div className="space-y-1">
            <button
              onClick={() => setShowEqualizer(!showEqualizer)}
              className={`w-full flex items-center justify-between px-3.5 py-2 rounded-xl text-xs font-medium transition-all ${
                showEqualizer
                  ? 'bg-purple-500/20 text-purple-300 border border-purple-500/40'
                  : 'text-gray-400 hover:text-white hover:bg-white/[0.05]'
              }`}
            >
              <div className="flex items-center gap-2.5">
                <Sliders className="w-4 h-4 text-purple-400" />
                <span>Equalizer (Tunex)</span>
              </div>
              <span className={`text-[10px] px-1.5 py-0.5 rounded font-mono ${
                equalizer.enabled ? 'bg-purple-500/30 text-purple-300' : 'bg-gray-800 text-gray-500'
              }`}>
                {equalizer.enabled ? equalizer.preset : 'OFF'}
              </span>
            </button>

            <button
              onClick={() => setShowLyrics(!showLyrics)}
              className={`w-full flex items-center justify-between px-3.5 py-2 rounded-xl text-xs font-medium transition-all ${
                showLyrics
                  ? 'bg-purple-500/20 text-purple-300 border border-purple-500/40'
                  : 'text-gray-400 hover:text-white hover:bg-white/[0.05]'
              }`}
            >
              <div className="flex items-center gap-2.5">
                <Mic2 className="w-4 h-4 text-indigo-400" />
                <span>Live Synced Lyrics</span>
              </div>
            </button>

            <button
              onClick={handleOpenLocalFiles}
              className="w-full flex items-center gap-2.5 px-3.5 py-2 rounded-xl text-xs font-medium text-gray-400 hover:text-white hover:bg-white/[0.05] transition-all"
            >
              <Folder className="w-4 h-4 text-emerald-400" />
              <span>Import Local Files</span>
            </button>
          </div>
        </div>

        {/* Playlists list */}
        <div>
          <div className="px-3.5 mb-2 flex items-center justify-between">
            <span className="text-[11px] font-semibold text-gray-500 uppercase tracking-wider">
              Playlists
            </span>
            <button
              onClick={handleCreatePlaylist}
              className="text-gray-400 hover:text-white p-1 rounded hover:bg-white/[0.05]"
              title="Create Playlist"
            >
              <Plus className="w-3.5 h-3.5" />
            </button>
          </div>

          <div className="space-y-0.5 max-h-48 overflow-y-auto pr-1">
            <button
              onClick={() => setCurrentView('library')}
              className="w-full flex items-center gap-2.5 px-3.5 py-1.5 rounded-lg text-xs text-gray-300 hover:text-white hover:bg-white/[0.04]"
            >
              <Heart className="w-3.5 h-3.5 text-pink-500" />
              <span>Liked Songs</span>
            </button>

            {playlists.filter(p => p.id !== 'favorites').map((pl) => (
              <button
                key={pl.id}
                onClick={() => setCurrentView('library')}
                className="w-full text-left truncate px-3.5 py-1.5 rounded-lg text-xs text-gray-400 hover:text-white hover:bg-white/[0.04]"
              >
                {pl.title}
              </button>
            ))}
          </div>
        </div>
      </div>

      {/* Community / Telegram Badge */}
      <div className="pt-3 border-t border-white/[0.06]">
        <a
          href="https://t.me/auralix_music"
          target="_blank"
          rel="noreferrer"
          className="flex items-center gap-2.5 px-3 py-2 rounded-xl bg-gradient-to-r from-purple-900/40 to-indigo-900/40 border border-purple-500/20 hover:border-purple-500/40 text-purple-300 text-xs transition-all group"
        >
          <Send className="w-3.5 h-3.5 text-purple-400 group-hover:translate-x-0.5 transition-transform" />
          <div className="flex flex-col">
            <span className="font-semibold leading-none">Auralix Telegram</span>
            <span className="text-[10px] text-gray-400">Join community & updates</span>
          </div>
        </a>
      </div>
    </aside>
  )
}
