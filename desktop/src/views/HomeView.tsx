import React, { useEffect, useState } from 'react'
import { Play, Sparkles, TrendingUp, Flame } from 'lucide-react'
import { getTrendingMusic, searchYouTube } from '../services/youtubeApi'
import { Song } from '../types/music'
import { usePlayerStore } from '../store/usePlayerStore'

export const HomeView: React.FC = () => {
  const [trending, setTrending] = useState<Song[]>([])
  const [madeForYou, setMadeForYou] = useState<Song[]>([])
  const [lofiVibes, setLofiVibes] = useState<Song[]>([])
  const [loading, setLoading] = useState(true)

  const { playSong, currentSong, isPlaying } = usePlayerStore()

  useEffect(() => {
    let active = true

    async function loadData() {
      try {
        const [trendData, popData, lofiData] = await Promise.all([
          getTrendingMusic(),
          searchYouTube('popular music 2026'),
          searchYouTube('lofi hip hop chill beats')
        ])
        if (active) {
          setTrending(trendData)
          setMadeForYou(popData.slice(0, 8))
          setLofiVibes(lofiData.slice(0, 8))
          setLoading(false)
        }
      } catch {
        if (active) setLoading(false)
      }
    }

    loadData()
    return () => {
      active = false
    }
  }, [])

  return (
    <div className="p-8 space-y-10 max-w-7xl mx-auto overflow-y-auto select-none">
      {/* Hero Banner */}
      <div className="relative rounded-3xl overflow-hidden bg-gradient-to-r from-purple-900/60 via-indigo-900/40 to-[#0B0C10] border border-white/[0.08] p-8 shadow-2xl">
        <div className="max-w-xl relative z-10 space-y-3">
          <div className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-purple-500/20 border border-purple-500/30 text-purple-300 text-xs font-semibold">
            <Sparkles className="w-3.5 h-3.5" />
            <span>Auralix for Desktop</span>
          </div>
          <h1 className="text-3xl font-extrabold text-white tracking-tight leading-tight">
            Unlimited Music. No Ads. <br />
            <span className="bg-gradient-to-r from-purple-400 to-indigo-300 bg-clip-text text-transparent">
              Hardware-tuned Audio on PC.
            </span>
          </h1>
          <p className="text-xs text-gray-300 leading-relaxed">
            Stream high-fidelity music from YouTube Music, tune frequencies with the Tunex DSP Equalizer,
            and sing along with live word-synced karaoke lyrics.
          </p>
          <div className="pt-2 flex items-center gap-3">
            <button
              onClick={() => trending.length > 0 && playSong(trending[0], trending)}
              className="px-5 py-2.5 rounded-full bg-purple-600 hover:bg-purple-500 active:scale-95 text-white text-xs font-bold flex items-center gap-2 shadow-lg shadow-purple-600/40 transition-all cursor-pointer"
            >
              <Play className="w-4 h-4 fill-current" />
              <span>Play Top Hits</span>
            </button>
          </div>
        </div>

        {/* Ambient background decoration */}
        <div className="absolute right-0 top-0 bottom-0 w-1/2 bg-gradient-to-l from-purple-600/20 to-transparent pointer-events-none" />
      </div>

      {/* Section 1: Quick Picks / Trending */}
      <section className="space-y-4">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <Flame className="w-5 h-5 text-purple-400" />
            <h2 className="text-lg font-bold text-white">Trending Hits</h2>
          </div>
        </div>

        <div className="grid grid-cols-2 md:grid-cols-4 lg:grid-cols-6 gap-4">
          {loading
            ? Array.from({ length: 6 }).map((_, i) => (
                <div key={i} className="animate-pulse space-y-2">
                  <div className="aspect-square bg-white/[0.05] rounded-2xl" />
                  <div className="h-4 bg-white/[0.05] rounded w-3/4" />
                  <div className="h-3 bg-white/[0.05] rounded w-1/2" />
                </div>
              ))
            : trending.slice(0, 12).map((song) => {
                const isThisPlaying = currentSong?.id === song.id && isPlaying
                return (
                  <div
                    key={song.id}
                    onClick={() => playSong(song, trending)}
                    className="group bg-[#13141F]/60 hover:bg-[#181A28] border border-white/[0.05] hover:border-purple-500/30 rounded-2xl p-3 transition-all cursor-pointer relative shadow-md hover:shadow-xl hover:shadow-purple-950/20"
                  >
                    <div className="aspect-square relative rounded-xl overflow-hidden mb-3">
                      <img
                        src={song.artworkUrl}
                        alt={song.title}
                        className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-300"
                      />
                      <div
                        className={`absolute inset-0 bg-black/40 flex items-center justify-center transition-opacity ${
                          isThisPlaying ? 'opacity-100' : 'opacity-0 group-hover:opacity-100'
                        }`}
                      >
                        <div className="w-10 h-10 rounded-full bg-purple-600 text-white flex items-center justify-center shadow-lg shadow-purple-600/40">
                          <Play className="w-4 h-4 fill-current translate-x-0.5" />
                        </div>
                      </div>
                    </div>
                    <h3 className="text-xs font-semibold text-white truncate leading-snug">
                      {song.title}
                    </h3>
                    <p className="text-[11px] text-gray-400 truncate mt-0.5">{song.artist}</p>
                  </div>
                )
              })}
        </div>
      </section>

      {/* Section 2: Made For You */}
      <section className="space-y-4">
        <div className="flex items-center gap-2">
          <Sparkles className="w-5 h-5 text-indigo-400" />
          <h2 className="text-lg font-bold text-white">Made For You</h2>
        </div>

        <div className="grid grid-cols-2 md:grid-cols-4 lg:grid-cols-6 gap-4">
          {madeForYou.map((song) => (
            <div
              key={song.id}
              onClick={() => playSong(song, madeForYou)}
              className="group bg-[#13141F]/60 hover:bg-[#181A28] border border-white/[0.05] hover:border-purple-500/30 rounded-2xl p-3 transition-all cursor-pointer relative shadow-md hover:shadow-xl"
            >
              <div className="aspect-square relative rounded-xl overflow-hidden mb-3">
                <img
                  src={song.artworkUrl}
                  alt={song.title}
                  className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-300"
                />
                <div className="absolute inset-0 bg-black/40 flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity">
                  <div className="w-10 h-10 rounded-full bg-purple-600 text-white flex items-center justify-center shadow-lg shadow-purple-600/40">
                    <Play className="w-4 h-4 fill-current translate-x-0.5" />
                  </div>
                </div>
              </div>
              <h3 className="text-xs font-semibold text-white truncate">{song.title}</h3>
              <p className="text-[11px] text-gray-400 truncate mt-0.5">{song.artist}</p>
            </div>
          ))}
        </div>
      </section>

      {/* Section 3: Chill & Lofi */}
      <section className="space-y-4">
        <div className="flex items-center gap-2">
          <TrendingUp className="w-5 h-5 text-emerald-400" />
          <h2 className="text-lg font-bold text-white">Chill & Focus Beats</h2>
        </div>

        <div className="grid grid-cols-2 md:grid-cols-4 lg:grid-cols-6 gap-4">
          {lofiVibes.map((song) => (
            <div
              key={song.id}
              onClick={() => playSong(song, lofiVibes)}
              className="group bg-[#13141F]/60 hover:bg-[#181A28] border border-white/[0.05] hover:border-purple-500/30 rounded-2xl p-3 transition-all cursor-pointer relative shadow-md hover:shadow-xl"
            >
              <div className="aspect-square relative rounded-xl overflow-hidden mb-3">
                <img
                  src={song.artworkUrl}
                  alt={song.title}
                  className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-300"
                />
                <div className="absolute inset-0 bg-black/40 flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity">
                  <div className="w-10 h-10 rounded-full bg-purple-600 text-white flex items-center justify-center shadow-lg shadow-purple-600/40">
                    <Play className="w-4 h-4 fill-current translate-x-0.5" />
                  </div>
                </div>
              </div>
              <h3 className="text-xs font-semibold text-white truncate">{song.title}</h3>
              <p className="text-[11px] text-gray-400 truncate mt-0.5">{song.artist}</p>
            </div>
          ))}
        </div>
      </section>
    </div>
  )
}
