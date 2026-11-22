import React, { useState } from 'react'
import { Search, Play, Heart, Clock } from 'lucide-react'
import { searchYouTube } from '../services/youtubeApi'
import { Song } from '../types/music'
import { usePlayerStore } from '../store/usePlayerStore'
import { useLibraryStore } from '../store/useLibraryStore'

export const SearchView: React.FC = () => {
  const [query, setQuery] = useState('')
  const [results, setResults] = useState<Song[]>([])
  const [loading, setLoading] = useState(false)
  const [hasSearched, setHasSearched] = useState(false)

  const { playSong, currentSong, isPlaying } = usePlayerStore()
  const { toggleLike, isLiked } = useLibraryStore()

  const handleSearch = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!query.trim()) return

    setLoading(true)
    setHasSearched(true)
    try {
      const data = await searchYouTube(query)
      setResults(data)
    } finally {
      setLoading(false)
    }
  }

  const genrePresets = ['Pop Hits', 'Hip Hop', 'Rock Classics', 'EDM / Dance', 'Lofi & Chill', 'Bollywood & Hindi', 'Anime OST', 'Acoustic']

  return (
    <div className="p-8 max-w-7xl mx-auto space-y-6 select-none overflow-y-auto">
      {/* Search Bar */}
      <form onSubmit={handleSearch} className="relative max-w-2xl">
        <Search className="absolute left-4 top-1/2 -translate-y-1/2 w-5 h-5 text-gray-400" />
        <input
          type="text"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Search any song, artist, album, or vibe..."
          className="w-full bg-[#13141F] border border-white/[0.08] focus:border-purple-500 rounded-2xl pl-12 pr-28 py-3.5 text-sm text-white placeholder-gray-500 focus:outline-none transition-all shadow-inner"
        />
        <button
          type="submit"
          disabled={loading || !query.trim()}
          className="absolute right-2 top-1/2 -translate-y-1/2 px-4 py-2 bg-purple-600 hover:bg-purple-500 disabled:bg-gray-700 text-white text-xs font-semibold rounded-xl transition-all cursor-pointer"
        >
          {loading ? 'Searching...' : 'Search'}
        </button>
      </form>

      {/* Quick Genre Chips */}
      <div className="flex flex-wrap gap-2 pt-1">
        {genrePresets.map((genre) => (
          <button
            key={genre}
            onClick={() => {
              setQuery(genre)
              setLoading(true)
              setHasSearched(true)
              searchYouTube(genre).then(setResults).finally(() => setLoading(false))
            }}
            className="px-3.5 py-1.5 rounded-full bg-white/[0.04] hover:bg-purple-600/30 hover:border-purple-500/40 border border-white/[0.06] text-xs text-gray-300 transition-all"
          >
            {genre}
          </button>
        ))}
      </div>

      {/* Results List */}
      <div className="pt-4">
        {loading ? (
          <div className="flex flex-col items-center justify-center py-20 text-gray-400 gap-3">
            <div className="w-8 h-8 border-2 border-purple-500/30 border-t-purple-500 rounded-full animate-spin" />
            <span className="text-sm">Searching YouTube Music...</span>
          </div>
        ) : results.length > 0 ? (
          <div className="space-y-1.5">
            <div className="grid grid-cols-12 px-4 py-2 text-xs font-semibold text-gray-500 uppercase tracking-wider border-b border-white/[0.06]">
              <span className="col-span-1">#</span>
              <span className="col-span-6">Title</span>
              <span className="col-span-3">Artist</span>
              <span className="col-span-2 text-right flex items-center justify-end gap-1">
                <Clock className="w-3.5 h-3.5" />
              </span>
            </div>

            {results.map((song, idx) => {
              const isThisPlaying = currentSong?.id === song.id && isPlaying
              const liked = isLiked(song.id)

              return (
                <div
                  key={song.id}
                  onClick={() => playSong(song, results)}
                  className={`grid grid-cols-12 items-center px-4 py-2.5 rounded-xl transition-all cursor-pointer group ${
                    isThisPlaying
                      ? 'bg-purple-600/20 border border-purple-500/40 text-purple-300'
                      : 'hover:bg-white/[0.04] text-gray-300'
                  }`}
                >
                  <span className="col-span-1 text-xs text-gray-500 font-mono flex items-center">
                    <span className="group-hover:hidden">{idx + 1}</span>
                    <Play className="w-3.5 h-3.5 text-purple-400 fill-current hidden group-hover:block" />
                  </span>

                  <div className="col-span-6 flex items-center gap-3 min-w-0 pr-4">
                    <img
                      src={song.artworkUrl}
                      alt={song.title}
                      className="w-10 h-10 rounded-lg object-cover flex-shrink-0"
                    />
                    <div className="truncate">
                      <div className="text-sm font-medium text-white truncate">{song.title}</div>
                      {song.views && (
                        <div className="text-[10px] text-gray-400">{song.views}</div>
                      )}
                    </div>
                  </div>

                  <span className="col-span-3 text-xs text-gray-400 truncate">{song.artist}</span>

                  <div className="col-span-2 flex items-center justify-end gap-3 text-xs text-gray-400 font-mono">
                    <button
                      onClick={(e) => {
                        e.stopPropagation()
                        toggleLike(song)
                      }}
                      className="p-1 hover:text-pink-500 transition-colors"
                    >
                      <Heart
                        className={`w-4 h-4 ${liked ? 'fill-pink-500 text-pink-500' : 'text-gray-500'}`}
                      />
                    </button>
                    <span>{song.durationFormatted}</span>
                  </div>
                </div>
              )
            })}
          </div>
        ) : hasSearched ? (
          <div className="text-center py-20 text-gray-500 text-sm">
            No tracks found matching "{query}". Try a different keyword!
          </div>
        ) : null}
      </div>
    </div>
  )
}
