export interface Song {
  id: string
  title: string
  artist: string
  album?: string
  duration: number // in seconds
  durationFormatted: string // e.g. "3:45"
  artworkUrl: string
  audioUrl?: string
  isLocal?: boolean
  localPath?: string
  views?: string
}

export interface LyricLine {
  time: number // in seconds
  text: string
}

export interface Playlist {
  id: string
  title: string
  description?: string
  coverUrl?: string
  tracks: Song[]
  isCustom?: boolean
}

export type EqualizerPresetName =
  | 'Flat'
  | 'Bass Boost'
  | 'Rock'
  | 'Pop'
  | 'Hip Hop'
  | 'Electronic'
  | 'Jazz'
  | 'Classical'
  | 'Vocal Boost'
  | 'Custom'

export interface EqualizerState {
  enabled: boolean
  preset: EqualizerPresetName
  bands: [number, number, number, number, number] // 60Hz, 230Hz, 910Hz, 3.6kHz, 14kHz (-12 to +12 dB)
  bassBoost: number // 0 to 100%
  virtualizer: number // 0 to 100%
}
