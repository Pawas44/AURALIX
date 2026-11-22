import { create } from 'zustand'
import { Song, EqualizerPresetName, EqualizerState } from '../types/music'
import { audioEngine, EQUALIZER_PRESETS } from '../services/audioEngine'
import { resolveAudioStreamUrl } from '../services/youtubeApi'

interface PlayerStore {
  currentSong: Song | null
  queue: Song[]
  queueIndex: number
  isPlaying: boolean
  currentTime: number
  duration: number
  volume: number
  isMuted: boolean
  isShuffle: boolean
  repeatMode: 'off' | 'all' | 'one'
  isLoadingAudio: boolean
  isMiniPlayer: boolean
  showLyrics: boolean
  showEqualizer: boolean

  // Equalizer
  equalizer: EqualizerState

  // Actions
  playSong: (song: Song, newQueue?: Song[]) => Promise<void>
  togglePlay: () => void
  pause: () => void
  resume: () => void
  nextSong: () => void
  prevSong: () => void
  seekTo: (time: number) => void
  setVolume: (vol: number) => void
  toggleMute: () => void
  toggleShuffle: () => void
  cycleRepeat: () => void
  setMiniPlayer: (mini: boolean) => void
  setShowLyrics: (show: boolean) => void
  setShowEqualizer: (show: boolean) => void

  // Equalizer actions
  setEqualizerPreset: (preset: EqualizerPresetName) => void
  setEqualizerBand: (bandIndex: number, gainDb: number) => void
  setBassBoost: (percent: number) => void
  setVirtualizer: (percent: number) => void
  toggleEqualizer: () => void
}

const defaultEq: EqualizerState = {
  enabled: true,
  preset: 'Rock',
  bands: EQUALIZER_PRESETS['Rock'],
  bassBoost: 30,
  virtualizer: 20
}

export const usePlayerStore = create<PlayerStore>((set, get) => {
  const audio = audioEngine.getAudioElement()

  // Sync HTML5 audio events to state
  audio.ontimeupdate = () => {
    set({ currentTime: audio.currentTime })
  }

  audio.ondurationchange = () => {
    if (audio.duration && !isNaN(audio.duration)) {
      set({ duration: audio.duration })
    }
  }

  audio.onended = () => {
    const { repeatMode, nextSong, seekTo } = get()
    if (repeatMode === 'one') {
      seekTo(0)
      audio.play()
    } else {
      nextSong()
    }
  }

  audio.onplay = () => {
    set({ isPlaying: true })
    updateDiscord(get())
  }

  audio.onpause = () => {
    set({ isPlaying: false })
    updateDiscord(get())
  }

  function updateDiscord(state: PlayerStore) {
    if (window.auralixAPI?.updateDiscordPresence) {
      window.auralixAPI.updateDiscordPresence({
        title: state.currentSong?.title || '',
        artist: state.currentSong?.artist || '',
        artwork: state.currentSong?.artworkUrl,
        duration: state.duration,
        currentSeconds: state.currentTime,
        isPlaying: state.isPlaying
      })
    }
  }

  return {
    currentSong: null,
    queue: [],
    queueIndex: -1,
    isPlaying: false,
    currentTime: 0,
    duration: 0,
    volume: 0.8,
    isMuted: false,
    isShuffle: false,
    repeatMode: 'all',
    isLoadingAudio: false,
    isMiniPlayer: false,
    showLyrics: false,
    showEqualizer: false,
    equalizer: defaultEq,

    playSong: async (song: Song, newQueue?: Song[]) => {
      audioEngine.initAudioContext()
      audioEngine.resumeContext()

      let queue = get().queue
      let idx = get().queueIndex

      if (newQueue && newQueue.length > 0) {
        queue = newQueue
        idx = queue.findIndex((s) => s.id === song.id)
        if (idx === -1) {
          queue = [song, ...queue]
          idx = 0
        }
      } else if (!queue.some((s) => s.id === song.id)) {
        queue = [song, ...queue]
        idx = 0
      } else {
        idx = queue.findIndex((s) => s.id === song.id)
      }

      set({
        currentSong: song,
        queue,
        queueIndex: idx,
        currentTime: 0,
        duration: song.duration || 180,
        isLoadingAudio: true
      })

      // Update Discord RPC immediately
      updateDiscord({ ...get(), isPlaying: true })

      try {
        let streamUrl = song.audioUrl
        if (!streamUrl && !song.isLocal) {
          streamUrl = await resolveAudioStreamUrl(song.id)
        } else if (song.isLocal && song.localPath) {
          streamUrl = `file://${song.localPath}`
        }

        if (streamUrl) {
          audio.src = streamUrl
          await audio.play()
          audioEngine.applyEqualizer(get().equalizer)
        }
      } catch (err) {
        console.warn('Playback error:', err)
      } finally {
        set({ isLoadingAudio: false })
      }
    },

    togglePlay: () => {
      audioEngine.resumeContext()
      if (audio.paused) {
        audio.play().catch(() => {})
      } else {
        audio.pause()
      }
    },

    pause: () => audio.pause(),
    resume: () => {
      audioEngine.resumeContext()
      audio.play().catch(() => {})
    },

    nextSong: () => {
      const { queue, queueIndex, isShuffle, playSong } = get()
      if (queue.length === 0) return

      let nextIdx = queueIndex + 1
      if (isShuffle) {
        nextIdx = Math.floor(Math.random() * queue.length)
      } else if (nextIdx >= queue.length) {
        nextIdx = 0
      }

      const nextTrack = queue[nextIdx]
      if (nextTrack) {
        playSong(nextTrack)
      }
    },

    prevSong: () => {
      const { queue, queueIndex, currentTime, playSong, seekTo } = get()
      if (currentTime > 3) {
        seekTo(0)
        return
      }
      if (queue.length === 0) return

      let prevIdx = queueIndex - 1
      if (prevIdx < 0) prevIdx = queue.length - 1
      const prevTrack = queue[prevIdx]
      if (prevTrack) {
        playSong(prevTrack)
      }
    },

    seekTo: (time: number) => {
      audio.currentTime = time
      set({ currentTime: time })
    },

    setVolume: (vol: number) => {
      const clamped = Math.max(0, Math.min(1, vol))
      audioEngine.setVolume(clamped)
      set({ volume: clamped, isMuted: clamped === 0 })
    },

    toggleMute: () => {
      const { isMuted, volume } = get()
      if (isMuted) {
        audioEngine.setVolume(volume > 0 ? volume : 0.8)
        set({ isMuted: false })
      } else {
        audioEngine.setVolume(0)
        set({ isMuted: true })
      }
    },

    toggleShuffle: () => set((state) => ({ isShuffle: !state.isShuffle })),
    cycleRepeat: () => {
      const modes: ('off' | 'all' | 'one')[] = ['off', 'all', 'one']
      const cur = get().repeatMode
      const next = modes[(modes.indexOf(cur) + 1) % modes.length]
      set({ repeatMode: next })
    },

    setMiniPlayer: (mini: boolean) => set({ isMiniPlayer: mini }),
    setShowLyrics: (show: boolean) => set({ showLyrics: show }),
    setShowEqualizer: (show: boolean) => set({ showEqualizer: show }),

    // Equalizer updates
    setEqualizerPreset: (preset: EqualizerPresetName) => {
      const bands = EQUALIZER_PRESETS[preset]
      const nextState: EqualizerState = {
        ...get().equalizer,
        preset,
        bands: [...bands] as [number, number, number, number, number]
      }
      set({ equalizer: nextState })
      audioEngine.applyEqualizer(nextState)
    },

    setEqualizerBand: (bandIndex: number, gainDb: number) => {
      const currentBands = [...get().equalizer.bands] as [number, number, number, number, number]
      currentBands[bandIndex] = Math.max(-12, Math.min(12, gainDb))
      const nextState: EqualizerState = {
        ...get().equalizer,
        preset: 'Custom',
        bands: currentBands
      }
      set({ equalizer: nextState })
      audioEngine.applyEqualizer(nextState)
    },

    setBassBoost: (percent: number) => {
      const nextState: EqualizerState = {
        ...get().equalizer,
        bassBoost: Math.max(0, Math.min(100, percent))
      }
      set({ equalizer: nextState })
      audioEngine.applyEqualizer(nextState)
    },

    setVirtualizer: (percent: number) => {
      const nextState: EqualizerState = {
        ...get().equalizer,
        virtualizer: Math.max(0, Math.min(100, percent))
      }
      set({ equalizer: nextState })
      audioEngine.applyEqualizer(nextState)
    },

    toggleEqualizer: () => {
      const nextEnabled = !get().equalizer.enabled
      const nextState: EqualizerState = {
        ...get().equalizer,
        enabled: nextEnabled
      }
      set({ equalizer: nextState })
      audioEngine.applyEqualizer(nextState)
    }
  }
})
