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
  | 'Vocal Clarity'
  | 'Rock'
  | 'Pop'
  | 'Hip-Hop'
  | 'Electronic / EDM'
  | 'Jazz'
  | 'Classical'
  | 'Acoustic'
  | 'Treble Boost'
  | 'Custom'

export interface EqualizerState {
  enabled: boolean
  preset: EqualizerPresetName
  bands: number[] // 10 ISO Bands: 31Hz to 16kHz (-10 to +10 dB)
  bassBoost: number // 0 to 100%
  virtualizer: number // 0 to 100%
}
