import { create } from 'zustand'
import { Song, Playlist } from '../types/music'

interface LibraryStore {
  likedSongs: Song[]
  history: Song[]
  playlists: Playlist[]
  localSongs: Song[]

  toggleLike: (song: Song) => void
  isLiked: (songId: string) => boolean
  addToHistory: (song: Song) => void
  createPlaylist: (title: string, description?: string) => void
  addSongToPlaylist: (playlistId: string, song: Song) => void
  removeSongFromPlaylist: (playlistId: string, songId: string) => void
  addLocalSongs: (songs: Song[]) => void
}

const STORAGE_KEY = 'auralix_desktop_library'

function loadFromStorage(): any {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    return raw ? JSON.parse(raw) : {}
  } catch {
    return {}
  }
}

function saveToStorage(state: any) {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify({
      likedSongs: state.likedSongs,
      history: state.history.slice(0, 50),
      playlists: state.playlists,
      localSongs: state.localSongs
    }))
  } catch {}
}

const initialData = loadFromStorage()

export const useLibraryStore = create<LibraryStore>((set, get) => ({
  likedSongs: initialData.likedSongs || [],
  history: initialData.history || [],
  playlists: initialData.playlists || [
    {
      id: 'favorites',
      title: 'Liked Songs',
      description: 'Your favorite tracks in Auralix',
      tracks: initialData.likedSongs || [],
      isCustom: false
    }
  ],
  localSongs: initialData.localSongs || [],

  toggleLike: (song: Song) => {
    const { likedSongs } = get()
    const exists = likedSongs.some((s) => s.id === song.id)
    const updated = exists ? likedSongs.filter((s) => s.id !== song.id) : [song, ...likedSongs]

    set((state) => {
      const nextPlaylists = state.playlists.map((pl) =>
        pl.id === 'favorites' ? { ...pl, tracks: updated } : pl
      )
      const next = { likedSongs: updated, playlists: nextPlaylists }
      saveToStorage({ ...state, ...next })
      return next
    })
  },

  isLiked: (songId: string) => {
    return get().likedSongs.some((s) => s.id === songId)
  },

  addToHistory: (song: Song) => {
    set((state) => {
      const filtered = state.history.filter((s) => s.id !== song.id)
      const next = { history: [song, ...filtered].slice(0, 50) }
      saveToStorage({ ...state, ...next })
      return next
    })
  },

  createPlaylist: (title: string, description?: string) => {
    const newPl: Playlist = {
      id: `pl_${Date.now()}`,
      title,
      description,
      tracks: [],
      isCustom: true
    }
    set((state) => {
      const next = { playlists: [...state.playlists, newPl] }
      saveToStorage({ ...state, ...next })
      return next
    })
  },

  addSongToPlaylist: (playlistId: string, song: Song) => {
    set((state) => {
      const next = {
        playlists: state.playlists.map((pl) =>
          pl.id === playlistId && !pl.tracks.some((t) => t.id === song.id)
            ? { ...pl, tracks: [...pl.tracks, song] }
            : pl
        )
      }
      saveToStorage({ ...state, ...next })
      return next
    })
  },

  removeSongFromPlaylist: (playlistId: string, songId: string) => {
    set((state) => {
      const next = {
        playlists: state.playlists.map((pl) =>
          pl.id === playlistId
            ? { ...pl, tracks: pl.tracks.filter((t) => t.id !== songId) }
            : pl
        )
      }
      saveToStorage({ ...state, ...next })
      return next
    })
  },

  addLocalSongs: (songs: Song[]) => {
    set((state) => {
      const existingIds = new Set(state.localSongs.map((s) => s.id))
      const fresh = songs.filter((s) => !existingIds.has(s.id))
      const next = { localSongs: [...state.localSongs, ...fresh] }
      saveToStorage({ ...state, ...next })
      return next
    })
  }
}))
