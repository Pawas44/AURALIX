import { EqualizerPresetName, EqualizerState } from '../types/music'

export const EQUALIZER_PRESETS: Record<EqualizerPresetName, [number, number, number, number, number]> = {
  'Flat': [0, 0, 0, 0, 0],
  'Bass Boost': [7, 5, 1, 0, -1],
  'Rock': [5, 3, -1, 3, 5],
  'Pop': [-1, 2, 5, 2, -1],
  'Hip Hop': [6, 4, 0, 2, 3],
  'Electronic': [5, 4, -1, 2, 4],
  'Jazz': [4, 2, -2, 2, 4],
  'Classical': [5, 3, -2, 3, 4],
  'Vocal Boost': [-2, -1, 5, 3, 0],
  'Custom': [0, 0, 0, 0, 0]
}

export const FREQUENCY_LABELS = ['60 Hz', '230 Hz', '910 Hz', '3.6 kHz', '14 kHz']
const FREQUENCIES = [60, 230, 910, 3600, 14000]

class AudioEngine {
  private audioCtx: AudioContext | null = null
  private audioElement: HTMLAudioElement = new Audio()
  private sourceNode: MediaElementAudioSourceNode | null = null
  private bandFilters: BiquadFilterNode[] = []
  private bassBoostFilter: BiquadFilterNode | null = null
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

      // Create 5-band Peaking Equalizer Filters
      this.bandFilters = FREQUENCIES.map((freq) => {
        const filter = this.audioCtx!.createBiquadFilter()
        filter.type = 'peaking'
        filter.frequency.value = freq
        filter.Q.value = 1.4
        filter.gain.value = 0
        return filter
      })

      // Bass Boost low-shelf filter (centered at 100 Hz)
      this.bassBoostFilter = this.audioCtx.createBiquadFilter()
      this.bassBoostFilter.type = 'lowshelf'
      this.bassBoostFilter.frequency.value = 100
      this.bassBoostFilter.gain.value = 0

      // Master output gain
      this.gainNode = this.audioCtx.createGain()
      this.gainNode.gain.value = 1.0

      // Chain the nodes: Source -> BassBoost -> Band 0 -> 1 -> 2 -> 3 -> 4 -> Gain -> Destination
      let lastNode: AudioNode = this.sourceNode
      if (this.bassBoostFilter) {
        lastNode.connect(this.bassBoostFilter)
        lastNode = this.bassBoostFilter
      }

      this.bandFilters.forEach((filter) => {
        lastNode.connect(filter)
        lastNode = filter
      })

      lastNode.connect(this.gainNode)
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

    // Apply 5 bands
    this.bandFilters.forEach((filter, index) => {
      const targetGain = isEnabled ? (state.bands[index] || 0) : 0
      filter.gain.setTargetAtTime(targetGain, this.audioCtx!.currentTime, 0.02)
    })

    // Apply Bass Boost (0 to 100% maps to 0 to +10 dB low-shelf gain)
    if (this.bassBoostFilter) {
      const bassGain = isEnabled ? (state.bassBoost / 100) * 10 : 0
      this.bassBoostFilter.gain.setTargetAtTime(bassGain, this.audioCtx.currentTime, 0.02)
    }
  }

  public setVolume(vol: number) {
    this.audioElement.volume = Math.max(0, Math.min(1, vol))
  }
}

export const audioEngine = new AudioEngine()
