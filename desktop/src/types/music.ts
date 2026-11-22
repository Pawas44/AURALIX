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
  | 'Studio Master'
  | 'Acoustic'
  | 'Vocal Clarity'
  | 'Classical'
  | 'Jazz'
  | 'Rock'
  | 'Pop'
  | 'Treble Air'
  | 'Deep Bass Clean'
  | 'Electronic'
  | 'R&B'
  | 'Default (Flat)'
  | 'Custom'

export interface EqualizerState {
  enabled: boolean
  preset: EqualizerPresetName
  bands: number[] // 15 ISO Bands: 25Hz to 16kHz (-8 to +8 dB)
  bassBoost: number // 0 to 100%
  virtualizer: number // 0 to 100%
}
