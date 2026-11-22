import { EqualizerPresetName, EqualizerState } from '../types/music'

export const FREQUENCIES = [25, 40, 63, 100, 160, 250, 400, 630, 1000, 1600, 2500, 4000, 6300, 10000, 16000]

export const FREQUENCY_LABELS = [
  '25 Hz', '40 Hz', '63 Hz', '100 Hz', '160 Hz',
  '250 Hz', '400 Hz', '630 Hz', '1 kHz', '1.6 kHz',
  '2.5 kHz', '4 kHz', '6.3 kHz', '10 kHz', '16 kHz'
]

export function getCategoryForFreq(freqHz: number): string {
  if (freqHz <= 40) return 'SUB'
  if (freqHz <= 100) return 'BASS'
  if (freqHz <= 250) return 'LOW-MID'
  if (freqHz <= 1000) return 'MID'
  if (freqHz <= 2500) return 'HIGH-MID'
  if (freqHz <= 6300) return 'PRES'
  return 'AIR'
}

export const EQUALIZER_PRESETS: Record<EqualizerPresetName, number[]> = {
  'Studio Master': [3.0, 2.5, 1.5, 0.5, -0.5, -1.0, -0.5, 0.0, 0.5, 1.0, 1.5, 2.0, 2.5, 3.0, 3.5],
  'Acoustic': [1.0, 1.5, 1.0, 0.5, 0.5, 1.0, 1.5, 1.5, 1.0, 1.5, 2.0, 2.5, 2.0, 1.5, 1.0],
  'Vocal Clarity': [-1.5, -1.0, -0.5, 0.0, 0.5, 1.0, 2.0, 3.0, 3.5, 3.0, 2.0, 1.5, 1.0, 0.5, 0.0],
  'Classical': [2.5, 2.0, 1.5, 1.0, 0.0, -0.5, 0.0, 0.5, 1.0, 1.5, 2.0, 2.5, 2.5, 2.0, 1.5],
  'Jazz': [1.5, 2.0, 1.5, 1.0, 0.5, 0.5, 0.5, 1.0, 1.5, 1.5, 1.0, 1.0, 1.5, 2.0, 2.0],
  'Rock': [3.5, 3.0, 2.0, 1.0, -0.5, -1.0, 0.0, 1.0, 1.5, 2.0, 2.5, 3.0, 3.5, 3.5, 3.0],
  'Pop': [2.0, 2.5, 2.0, 1.0, 0.0, 0.5, 1.5, 2.0, 2.0, 1.5, 1.0, 1.5, 2.0, 2.5, 2.0],
  'Treble Air': [-1.0, -1.0, -0.5, 0.0, 0.0, 0.5, 0.5, 1.0, 1.5, 2.0, 3.0, 4.0, 5.0, 6.0, 6.5],
  'Deep Bass Clean': [6.0, 5.5, 4.0, 2.5, 0.5, -1.0, -1.5, -1.0, 0.0, 0.5, 0.5, 1.0, 1.0, 1.5, 1.5],
  'Electronic': [4.5, 4.0, 2.5, 1.0, -0.5, 0.0, 0.5, 1.0, 1.5, 2.0, 2.5, 3.0, 3.5, 4.0, 4.0],
  'R&B': [4.0, 3.5, 2.5, 1.5, 0.5, -0.5, 0.0, 0.5, 1.0, 1.5, 2.0, 2.5, 2.5, 3.0, 3.0],
  'Default (Flat)': [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0],
  'Custom': [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0]
}

class AudioEngine {
  private audioCtx: AudioContext | null = null
  private audioElement: HTMLAudioElement = new Audio()
  private sourceNode: MediaElementAudioSourceNode | null = null
  private bandFilters: BiquadFilterNode[] = []
  private bassBoostFilter: BiquadFilterNode | null = null
  private limiterNode: DynamicsCompressorNode | null = null
  private gainNode: GainNode | null = null
  private isInitialized = false

  constructor() {
    this.audioElement.crossOrigin = 'anonymous'
  }

  public getAudioElement(): HTMLAudioElement {
    return this.audioElement
  }

  public initAudioContext() {
    if (this.isInitialized) return

    try {
      const AudioCtxClass = window.AudioContext || (window as any).webkitAudioContext
      this.audioCtx = new AudioCtxClass()

      this.sourceNode = this.audioCtx.createMediaElementSource(this.audioElement)

      // Create 15-band Peaking Equalizer Filters
      this.bandFilters = FREQUENCIES.map((freq) => {
        const filter = this.audioCtx!.createBiquadFilter()
        filter.type = 'peaking'
        filter.frequency.value = freq
        filter.Q.value = 1.414
        filter.gain.value = 0
        return filter
      })

      // Bass Boost low-shelf filter (centered at 80 Hz)
      this.bassBoostFilter = this.audioCtx.createBiquadFilter()
      this.bassBoostFilter.type = 'lowshelf'
      this.bassBoostFilter.frequency.value = 80
      this.bassBoostFilter.gain.value = 0

      // Studio Peak Limiter (-1 dBFS threshold to prevent digital clipping)
      this.limiterNode = this.audioCtx.createDynamicsCompressor()
      this.limiterNode.threshold.value = -1.0
      this.limiterNode.knee.value = 0.0
      this.limiterNode.ratio.value = 20.0
      this.limiterNode.attack.value = 0.001
      this.limiterNode.release.value = 0.1

      // Master output gain
      this.gainNode = this.audioCtx.createGain()
      this.gainNode.gain.value = 1.0

      // Chain: Source -> BassBoost -> 15 Bands -> Limiter -> Gain -> Destination
      let lastNode: AudioNode = this.sourceNode
      if (this.bassBoostFilter) {
        lastNode.connect(this.bassBoostFilter)
        lastNode = this.bassBoostFilter
      }

      this.bandFilters.forEach((filter) => {
        lastNode.connect(filter)
        lastNode = filter
      })

      lastNode.connect(this.limiterNode)
      this.limiterNode.connect(this.gainNode)
      this.gainNode.connect(this.audioCtx.destination)

      this.isInitialized = true
    } catch (e) {
      console.warn('Audio Context initialization error:', e)
    }
  }

  public resumeContext() {
    if (this.audioCtx && this.audioCtx.state === 'suspended') {
      this.audioCtx.resume()
    }
  }

  public applyEqualizer(state: EqualizerState) {
    this.initAudioContext()
    if (!this.audioCtx) return

    const isEnabled = state.enabled

    // Apply 15 bands
    this.bandFilters.forEach((filter, index) => {
      const targetGain = isEnabled ? (state.bands[index] || 0) : 0
      filter.gain.setTargetAtTime(targetGain, this.audioCtx!.currentTime, 0.015)
    })

    // Apply Bass Boost (0 to +10 dB shelf)
    if (this.bassBoostFilter) {
      const bassGain = isEnabled ? (state.bassBoost / 100) * 10 : 0
      this.bassBoostFilter.gain.setTargetAtTime(bassGain, this.audioCtx!.currentTime, 0.02)
    }
  }
}

export const audioEngine = new AudioEngine()
