import React from 'react'
import { Play, Pause, SkipForward, SkipBack, X, Maximize2 } from 'lucide-react'
import { usePlayerStore } from '../store/usePlayerStore'

export const MiniPlayer: React.FC = () => {
  const { currentSong, isPlaying, togglePlay, nextSong, prevSong, isMiniPlayer, setMiniPlayer } =
    usePlayerStore()

  if (!isMiniPlayer || !currentSong) return null

  return (
    <div className="fixed bottom-6 right-6 w-72 bg-[#12131D]/95 backdrop-blur-2xl border border-white/[0.12] rounded-2xl p-3.5 shadow-2xl z-50 flex items-center gap-3.5 animate-fade-in shadow-purple-950/40 select-none">
      <img
        src={currentSong.artworkUrl}
        alt={currentSong.title}
        className="w-14 h-14 rounded-xl object-cover shadow-md border border-white/10"
      />

      <div className="flex-1 min-w-0">
        <h4 className="text-xs font-bold text-white truncate">{currentSong.title}</h4>
        <p className="text-[11px] text-gray-400 truncate">{currentSong.artist}</p>

        {/* Mini Controls */}
        <div className="flex items-center gap-3 mt-1.5">
          <button onClick={prevSong} className="text-gray-300 hover:text-white">
            <SkipBack className="w-3.5 h-3.5 fill-current" />
          </button>
          <button
            onClick={togglePlay}
            className="w-6 h-6 rounded-full bg-purple-600 hover:bg-purple-500 text-white flex items-center justify-center shadow-md shadow-purple-600/30"
          >
            {isPlaying ? (
              <Pause className="w-3 h-3 fill-current" />
            ) : (
              <Play className="w-3 h-3 fill-current translate-x-0.5" />
            )}
          </button>
          <button onClick={nextSong} className="text-gray-300 hover:text-white">
            <SkipForward className="w-3.5 h-3.5 fill-current" />
          </button>
        </div>
      </div>

      <div className="flex flex-col gap-1.5 self-start">
        <button
          onClick={() => setMiniPlayer(false)}
          className="p-1 text-gray-400 hover:text-white hover:bg-white/[0.08] rounded-md transition-colors"
          title="Restore Player"
        >
          <Maximize2 className="w-3.5 h-3.5" />
        </button>
        <button
          onClick={() => setMiniPlayer(false)}
          className="p-1 text-gray-400 hover:text-red-400 hover:bg-white/[0.08] rounded-md transition-colors"
          title="Close"
        >
          <X className="w-3.5 h-3.5" />
        </button>
      </div>
    </div>
  )
}
