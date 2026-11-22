import { EqualizerPresetName, EqualizerState } from '../types/music'

export const FREQUENCIES = [31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000]

export const FREQUENCY_LABELS = [
  '31 Hz', '62 Hz', '125 Hz', '250 Hz', '500 Hz',
  '1 kHz', '2 kHz', '4 kHz', '8 kHz', '16 kHz'
]

export const FREQUENCY_DESCRIPTIONS = [
  'Sub-Bass', 'Bass Punch', 'Warmth', 'Low-Mid', 'Mids Body',
  'Vocal Presence', 'Clarity', 'Treble Bite', 'Brilliance', 'Air'
]

export const EQUALIZER_PRESETS: Record<EqualizerPresetName, number[]> = {
  'Flat': [0, 0, 0, 0, 0, 0, 0, 0, 0, 0],
  'Bass Boost': [7, 6, 4, 2, 0, -1, 0, 1, 2, 2],
  'Vocal Clarity': [-2, -1, 0, 1, 3, 5, 4, 2, 1, 0],
  'Rock': [5, 4, 2, -1, -1, 1, 3, 4, 5, 5],
  'Pop': [2, 3, 2, 1, 2, 3, 2, 1, 2, 3],
  'Hip-Hop': [6, 5, 3, 1, 0, 1, 2, 1, 3, 3],
  'Electronic / EDM': [6, 5, 3, 0, -1, 2, 3, 4, 5, 5],
  'Jazz': [3, 2, 1, 2, -1, 1, 2, 3, 2, 2],
  'Classical': [4, 3, 2, 0, 0, 1, 2, 3, 3, 2],
  'Acoustic': [2, 2, 1, 2, 2, 2, 3, 3, 2, 2],
  'Treble Boost': [-2, -1, 0, 0, 1, 2, 4, 6, 7, 8],
  'Custom': [0, 0, 0, 0, 0, 0, 0, 0, 0, 0]
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

      // Create 10-band Peaking Equalizer Filters
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

      // Studio Peak Limiter
      this.limiterNode = this.audioCtx.createDynamicsCompressor()
      this.limiterNode.threshold.value = -1.0
      this.limiterNode.knee.value = 0.0
      this.limiterNode.ratio.value = 20.0
      this.limiterNode.attack.value = 0.001
      this.limiterNode.release.value = 0.1

      // Master output gain
      this.gainNode = this.audioCtx.createGain()
      this.gainNode.gain.value = 1.0

      // Chain: Source -> BassBoost -> 10 Bands -> Limiter -> Gain -> Destination
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

    // Apply 10 bands
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
