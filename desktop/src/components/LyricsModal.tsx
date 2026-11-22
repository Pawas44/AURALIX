import React, { useEffect, useState, useRef } from 'react'
import { X, Mic2 } from 'lucide-react'
import { usePlayerStore } from '../store/usePlayerStore'
import { fetchSyncedLyrics } from '../services/lyricsApi'
import { LyricLine } from '../types/music'

export const LyricsModal: React.FC = () => {
  const { currentSong, currentTime, showLyrics, setShowLyrics, seekTo } = usePlayerStore()
  const [lyrics, setLyrics] = useState<LyricLine[]>([])
  const [loading, setLoading] = useState(false)
  const activeLineRef = useRef<HTMLDivElement | null>(null)
  const containerRef = useRef<HTMLDivElement | null>(null)

  useEffect(() => {
    if (!currentSong) {
      setLyrics([])
      return
    }

    let isMounted = true
    setLoading(true)

    fetchSyncedLyrics(currentSong.title, currentSong.artist, currentSong.duration).then((data) => {
      if (isMounted) {
        setLyrics(data)
        setLoading(false)
      }
    })

    return () => {
      isMounted = false
    }
  }, [currentSong?.id])

  // Find active line index
  let activeIndex = -1
  for (let i = 0; i < lyrics.length; i++) {
    if (currentTime >= lyrics[i].time) {
      activeIndex = i
    } else {
      break
    }
  }

  // Smooth auto-scroll active lyric line to center
  useEffect(() => {
    if (activeLineRef.current && containerRef.current) {
      activeLineRef.current.scrollIntoView({
        behavior: 'smooth',
        block: 'center'
      })
    }
  }, [activeIndex])

  if (!showLyrics) return null

  return (
    <div className="fixed inset-0 bg-[#0B0C10]/95 backdrop-blur-2xl z-50 flex flex-col p-8 animate-fade-in select-none">
      {/* Background ambient album art glow */}
      {currentSong && (
        <div
          className="absolute inset-0 bg-cover bg-center opacity-10 filter blur-3xl pointer-events-none"
          style={{ backgroundImage: `url(${currentSong.artworkUrl})` }}
        />
      )}

      {/* Header */}
      <div className="flex items-center justify-between pb-6 border-b border-white/[0.08] relative z-10">
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 rounded-xl bg-purple-600/30 border border-purple-500/40 flex items-center justify-center">
            <Mic2 className="w-5 h-5 text-purple-300" />
          </div>
          <div>
            <h2 className="text-lg font-bold text-white flex items-center gap-2">
              Synced Karaoke Lyrics
              <span className="text-[10px] px-1.5 py-0.5 rounded bg-purple-500/20 text-purple-300 font-mono">
                Real-time
              </span>
            </h2>
            <p className="text-xs text-gray-400">
              {currentSong ? `${currentSong.title} — ${currentSong.artist}` : 'No song playing'}
            </p>
          </div>
        </div>

        <button
          onClick={() => setShowLyrics(false)}
          className="p-2 text-gray-400 hover:text-white rounded-xl hover:bg-white/[0.08] transition-colors"
        >
          <X className="w-6 h-6" />
        </button>
      </div>

      {/* Lyrics Scrollable Area */}
      <div
        ref={containerRef}
        className="flex-1 overflow-y-auto my-6 px-4 py-16 space-y-6 max-w-3xl mx-auto w-full relative z-10 scrollbar-none"
      >
        {loading ? (
          <div className="flex flex-col items-center justify-center h-full text-gray-400 text-sm gap-3">
            <div className="w-8 h-8 border-2 border-purple-500/30 border-t-purple-500 rounded-full animate-spin" />
            <span>Finding synchronized lyrics...</span>
          </div>
        ) : lyrics.length === 0 ? (
          <div className="flex flex-col items-center justify-center h-full text-gray-400 text-sm gap-2">
            <Mic2 className="w-10 h-10 text-gray-600" />
            <span className="font-semibold text-white">No lyrics available for this track</span>
            <span className="text-xs text-gray-500">Enjoy the music!</span>
          </div>
        ) : (
          lyrics.map((line, idx) => {
            const isActive = idx === activeIndex
            const isPast = idx < activeIndex

            return (
              <div
                key={idx}
                ref={isActive ? activeLineRef : null}
                onClick={() => seekTo(line.time)}
                className={`cursor-pointer transition-all duration-300 leading-relaxed font-semibold text-center select-none ${
                  isActive
                    ? 'text-3xl text-white scale-105 font-bold tracking-tight text-transparent bg-clip-text bg-gradient-to-r from-purple-200 via-white to-purple-200 drop-shadow-[0_0_20px_rgba(168,85,247,0.5)]'
                    : isPast
                    ? 'text-xl text-gray-500 opacity-60 hover:opacity-100 hover:text-gray-300'
                    : 'text-xl text-gray-600 hover:text-gray-400'
                }`}
              >
                {line.text}
              </div>
            )
          })
        )}
      </div>
    </div>
  )
}
