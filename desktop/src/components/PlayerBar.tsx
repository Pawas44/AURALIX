import React from 'react'
import {
  Play,
  Pause,
  SkipBack,
  SkipForward,
  Shuffle,
  Repeat,
  Repeat1,
  Volume2,
  VolumeX,
  Sliders,
  Mic2,
  Heart,
  PictureInPicture2
} from 'lucide-react'
import { usePlayerStore } from '../store/usePlayerStore'
import { useLibraryStore } from '../store/useLibraryStore'

function formatTime(sec: number): string {
  if (!sec || isNaN(sec)) return '0:00'
  const m = Math.floor(sec / 60)
  const s = Math.floor(sec % 60)
  return `${m}:${s < 10 ? '0' : ''}${s}`
}

export const PlayerBar: React.FC = () => {
  const {
    currentSong,
    isPlaying,
    currentTime,
    duration,
    volume,
    isMuted,
    isShuffle,
    repeatMode,
    showLyrics,
    showEqualizer,
    equalizer,
    togglePlay,
    nextSong,
    prevSong,
    seekTo,
    setVolume,
    toggleMute,
    toggleShuffle,
    cycleRepeat,
    setShowLyrics,
    setShowEqualizer,
    setMiniPlayer
  } = usePlayerStore()

  const { isLiked, toggleLike } = useLibraryStore()

  const liked = currentSong ? isLiked(currentSong.id) : false
  const progressPercent = duration > 0 ? (currentTime / duration) * 100 : 0

  return (
    <footer className="h-20 bg-[#0E0F17]/95 backdrop-blur-xl border-t border-white/[0.08] px-4 flex items-center justify-between z-40 select-none">
      {/* LEFT: Current Track Info */}
      <div className="flex items-center gap-3.5 w-1/4 min-w-[200px]">
        {currentSong ? (
          <>
            <img
              src={currentSong.artworkUrl}
              alt={currentSong.title}
              className="w-12 h-12 rounded-xl object-cover shadow-md shadow-black/40 border border-white/10"
            />
            <div className="min-w-0 flex-1">
              <h4 className="text-sm font-semibold text-white truncate leading-tight">
                {currentSong.title}
              </h4>
              <p className="text-xs text-gray-400 truncate mt-0.5">
                {currentSong.artist}
              </p>
            </div>
            <button
              onClick={() => toggleLike(currentSong)}
              className="p-1.5 text-gray-400 hover:text-pink-500 transition-colors"
              title={liked ? 'Unlike' : 'Like'}
            >
              <Heart
                className={`w-4 h-4 transition-transform active:scale-125 ${
                  liked ? 'fill-pink-500 text-pink-500' : ''
                }`}
              />
            </button>
          </>
        ) : (
          <div className="flex items-center gap-3 text-gray-500 text-xs">
            <div className="w-12 h-12 rounded-xl bg-white/[0.03] border border-white/[0.06] flex items-center justify-center">
              🎵
            </div>
            <span>No track selected</span>
          </div>
        )}
      </div>

      {/* CENTER: Player Transport Controls & Progress Bar */}
      <div className="flex flex-col items-center gap-1.5 w-2/4 max-w-xl">
        {/* Buttons */}
        <div className="flex items-center gap-5">
          <button
            onClick={toggleShuffle}
            title="Shuffle"
            className={`p-1.5 rounded-full transition-colors ${
              isShuffle ? 'text-purple-400' : 'text-gray-400 hover:text-white'
            }`}
          >
            <Shuffle className="w-4 h-4" />
          </button>

          <button
            onClick={prevSong}
            title="Previous"
            className="p-1.5 text-gray-300 hover:text-white transition-colors"
          >
            <SkipBack className="w-5 h-5 fill-current" />
          </button>

          <button
            onClick={togglePlay}
            title={isPlaying ? 'Pause' : 'Play'}
            className="w-10 h-10 rounded-full bg-purple-600 hover:bg-purple-500 active:scale-95 text-white flex items-center justify-center shadow-lg shadow-purple-600/40 transition-all"
          >
            {isPlaying ? (
              <Pause className="w-5 h-5 fill-current" />
            ) : (
              <Play className="w-5 h-5 fill-current translate-x-0.5" />
            )}
          </button>

          <button
            onClick={nextSong}
            title="Next"
            className="p-1.5 text-gray-300 hover:text-white transition-colors"
          >
            <SkipForward className="w-5 h-5 fill-current" />
          </button>

          <button
            onClick={cycleRepeat}
            title={`Repeat: ${repeatMode}`}
            className={`p-1.5 rounded-full transition-colors ${
              repeatMode !== 'off' ? 'text-purple-400' : 'text-gray-400 hover:text-white'
            }`}
          >
            {repeatMode === 'one' ? <Repeat1 className="w-4 h-4" /> : <Repeat className="w-4 h-4" />}
          </button>
        </div>

        {/* Timeline Scrubber */}
        <div className="w-full flex items-center gap-2.5 text-[11px] text-gray-400 font-mono">
          <span className="w-8 text-right">{formatTime(currentTime)}</span>
          <div
            className="flex-1 h-1.5 bg-white/[0.08] hover:h-2 rounded-full cursor-pointer relative overflow-hidden transition-all group"
            onClick={(e) => {
              const rect = e.currentTarget.getBoundingClientRect()
              const pos = (e.clientX - rect.left) / rect.width
              seekTo(pos * (duration || 180))
            }}
          >
            <div
              className="h-full bg-gradient-to-r from-purple-600 to-indigo-500 rounded-full relative transition-all"
              style={{ width: `${progressPercent}%` }}
            >
              <div className="absolute right-0 top-1/2 -translate-y-1/2 w-2.5 h-2.5 bg-white rounded-full shadow-md opacity-0 group-hover:opacity-100 transition-opacity" />
            </div>
          </div>
          <span className="w-8">{formatTime(duration)}</span>
        </div>
      </div>

      {/* RIGHT: Volume, Lyrics, Tunex EQ, MiniPlayer */}
      <div className="flex items-center justify-end gap-3 w-1/4 min-w-[200px]">
        {/* Lyrics */}
        <button
          onClick={() => setShowLyrics(!showLyrics)}
          title="Synced Lyrics"
          className={`p-2 rounded-xl transition-all ${
            showLyrics
              ? 'bg-purple-600/30 text-purple-300 border border-purple-500/40'
              : 'text-gray-400 hover:text-white hover:bg-white/[0.06]'
          }`}
        >
          <Mic2 className="w-4 h-4" />
        </button>

        {/* Tunex Equalizer */}
        <button
          onClick={() => setShowEqualizer(!showEqualizer)}
          title="Tunex Equalizer"
          className={`p-2 rounded-xl transition-all relative ${
            showEqualizer || equalizer.enabled
              ? 'bg-purple-600/30 text-purple-300 border border-purple-500/40 shadow-sm shadow-purple-500/20'
              : 'text-gray-400 hover:text-white hover:bg-white/[0.06]'
          }`}
        >
          <Sliders className="w-4 h-4" />
          {equalizer.enabled && (
            <span className="absolute top-1.5 right-1.5 w-1.5 h-1.5 rounded-full bg-purple-400 animate-pulse" />
          )}
        </button>

        {/* Mini Player */}
        <button
          onClick={() => setMiniPlayer(true)}
          title="Mini Player"
          className="p-2 text-gray-400 hover:text-white hover:bg-white/[0.06] rounded-xl transition-all"
        >
          <PictureInPicture2 className="w-4 h-4" />
        </button>

        {/* Volume */}
        <div className="flex items-center gap-2">
          <button
            onClick={toggleMute}
            className="text-gray-400 hover:text-white transition-colors"
          >
            {isMuted || volume === 0 ? (
              <VolumeX className="w-4 h-4" />
            ) : (
              <Volume2 className="w-4 h-4" />
            )}
          </button>
          <input
            type="range"
            min="0"
            max="1"
            step="0.01"
            value={isMuted ? 0 : volume}
            onChange={(e) => setVolume(parseFloat(e.target.value))}
            className="w-20 h-1 bg-white/[0.12] rounded-lg accent-purple-500 cursor-pointer"
          />
        </div>
      </div>
    </footer>
  )
}
