import React from 'react'
import { X, Sliders, Volume2, Sparkles, RotateCcw } from 'lucide-react'
import { usePlayerStore } from '../store/usePlayerStore'
import { EQUALIZER_PRESETS, FREQUENCIES, FREQUENCY_LABELS, getCategoryForFreq } from '../services/audioEngine'
import { EqualizerPresetName } from '../types/music'

export const EqualizerModal: React.FC = () => {
  const {
    showEqualizer,
    setShowEqualizer,
    equalizer,
    setEqualizerPreset,
    setEqualizerBand,
    setBassBoost,
    setVirtualizer,
    toggleEqualizer
  } = usePlayerStore()

  if (!showEqualizer) return null

  const width = 560
  const height = 110
  const maxGain = 8 // LastWave ±8 dB
  const bandCount = FREQUENCIES.length

  // Calculate live Cubic Bezier SVG path across 15 band points
  const points = (equalizer.bands || []).map((gain, i) => {
    const x = (i / (bandCount - 1)) * (width - 40) + 20
    const y = height / 2 - (gain / maxGain) * (height / 2 - 14)
    return { x, y }
  })

  let curvePath = ''
  if (points.length > 0) {
    curvePath = `M ${points[0].x} ${points[0].y}`
    for (let i = 0; i < points.length - 1; i++) {
      const p0 = points[i]
      const p1 = points[i + 1]
      const cpX = (p0.x + p1.x) / 2
      curvePath += ` C ${cpX} ${p0.y}, ${cpX} ${p1.y}, ${p1.x} ${p1.y}`
    }
  }

  const fillPath = points.length > 0
    ? `${curvePath} L ${points[points.length - 1].x} ${height} L ${points[0].x} ${height} Z`
    : ''

  const presetList = Object.keys(EQUALIZER_PRESETS) as EqualizerPresetName[]

  const getCategoryColor = (cat: string) => {
    switch (cat) {
      case 'SUB': return 'text-rose-400 border-rose-500/30 bg-rose-500/10'
      case 'BASS': return 'text-orange-400 border-orange-500/30 bg-orange-500/10'
      case 'LOW-MID': return 'text-amber-400 border-amber-500/30 bg-amber-500/10'
      case 'MID': return 'text-emerald-400 border-emerald-500/30 bg-emerald-500/10'
      case 'HIGH-MID': return 'text-sky-400 border-sky-500/30 bg-sky-500/10'
      case 'PRES': return 'text-indigo-400 border-indigo-500/30 bg-indigo-500/10'
      default: return 'text-purple-400 border-purple-500/30 bg-purple-500/10' // AIR
    }
  }

  return (
    <div className="fixed inset-0 bg-black/75 backdrop-blur-md z-50 flex items-center justify-center p-4 animate-fade-in select-none">
      <div className="w-full max-w-4xl bg-[#0F1017] border border-white/[0.08] rounded-2xl p-6 shadow-2xl relative shadow-purple-900/30 flex flex-col max-h-[92vh] overflow-hidden">
        {/* Header */}
        <div className="flex items-center justify-between pb-4 border-b border-white/[0.08]">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-purple-600/20 border border-purple-500/30 flex items-center justify-center shadow-lg shadow-purple-900/40">
              <Sliders className="w-5 h-5 text-purple-400" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h3 className="text-base font-bold text-white tracking-wide">LastWave Equalizer</h3>
                <span className="text-[10px] px-2 py-0.5 rounded-full bg-purple-500/20 text-purple-300 font-mono font-bold border border-purple-500/30">
                  15-BAND ISO
                </span>
                <span className="text-[10px] px-2 py-0.5 rounded-full bg-sky-500/15 text-sky-300 font-mono border border-sky-500/25">
                  DSP Real-time
                </span>
              </div>
              <p className="text-xs text-gray-400">
                Audiophile Studio Sound Tuning • ±8 dB Precision Range
              </p>
            </div>
          </div>

          <div className="flex items-center gap-2.5">
            {/* Quick Reset */}
            <button
              onClick={() => setEqualizerPreset('Default (Flat)')}
              className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-white/[0.06] hover:bg-white/[0.12] text-xs font-medium text-gray-300 transition-colors border border-white/[0.06]"
              title="Reset to Flat"
            >
              <RotateCcw className="w-3.5 h-3.5 text-gray-400" />
              <span>Reset</span>
            </button>

            {/* Master Toggle */}
            <button
              onClick={toggleEqualizer}
              className={`px-3.5 py-1.5 rounded-xl text-xs font-semibold transition-all ${
                equalizer.enabled
                  ? 'bg-purple-600 text-white shadow-lg shadow-purple-600/40 border border-purple-400/50'
                  : 'bg-white/[0.08] text-gray-400 border border-white/[0.06]'
              }`}
            >
              {equalizer.enabled ? 'ENABLED' : 'DISABLED'}
            </button>

            <button
              onClick={() => setShowEqualizer(false)}
              className="p-1.5 text-gray-400 hover:text-white rounded-lg hover:bg-white/[0.08] transition-colors"
            >
              <X className="w-5 h-5" />
            </button>
          </div>
        </div>

        <div className="overflow-y-auto space-y-4 py-4 pr-1 flex-1">
          {/* Dynamic Bézier Spline Curve Visualizer */}
          <div className="bg-[#090A10] border border-white/[0.06] rounded-xl p-3 relative overflow-hidden">
            <div className="absolute top-2 left-3 flex items-center gap-3 text-[10px] text-gray-400 font-mono z-10 pointer-events-none">
              <span className="text-purple-300 font-semibold">+8 dB</span>
              <span className="text-gray-500">0 dB Baseline</span>
              <span className="text-sky-300 font-semibold">-8 dB</span>
            </div>

            <svg className="w-full h-24 overflow-visible" viewBox={`0 0 ${width} ${height}`}>
              <defs>
                <linearGradient id="curveGradient" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0%" stopColor="#8B5CF6" stopOpacity="0.45" />
                  <stop offset="50%" stopColor="#38BDF8" stopOpacity="0.15" />
                  <stop offset="100%" stopColor="#38BDF8" stopOpacity="0.0" />
                </linearGradient>
                <linearGradient id="strokeGradient" x1="0" y1="0" x2="1" y2="0">
                  <stop offset="0%" stopColor="#8B5CF6" />
                  <stop offset="40%" stopColor="#C084FC" />
                  <stop offset="70%" stopColor="#38BDF8" />
                  <stop offset="100%" stopColor="#818CF8" />
                </linearGradient>
              </defs>

              {/* +8 dB Top Guideline */}
              <line
                x1="10"
                y1="10"
                x2={width - 10}
                y2="10"
                stroke="rgba(255,255,255,0.06)"
                strokeDasharray="4 4"
              />

              {/* Zero dB Center Line */}
              <line
                x1="10"
                y1={height / 2}
                x2={width - 10}
                y2={height / 2}
                stroke="rgba(255,255,255,0.2)"
                strokeDasharray="4 4"
              />

              {/* -8 dB Bottom Guideline */}
              <line
                x1="10"
                y1={height - 10}
                x2={width - 10}
                y2={height - 10}
                stroke="rgba(255,255,255,0.06)"
                strokeDasharray="4 4"
              />

              {/* Spline Fill */}
              {equalizer.enabled && fillPath && (
                <path d={fillPath} fill="url(#curveGradient)" />
              )}

              {/* Spline Stroke */}
              {equalizer.enabled && curvePath && (
                <path
                  d={curvePath}
                  fill="none"
                  stroke="url(#strokeGradient)"
                  strokeWidth="2.5"
                  strokeLinecap="round"
                />
              )}

              {/* Glowing Data Dots */}
              {equalizer.enabled &&
                points.map((pt, i) => (
                  <g key={i}>
                    <circle cx={pt.x} cy={pt.y} r="3.5" fill="#C084FC" />
                    <circle cx={pt.x} cy={pt.y} r="1.5" fill="#FFFFFF" />
                  </g>
                ))}
            </svg>
          </div>

          {/* Audiophile Presets Horizontal Row */}
          <div>
            <div className="flex items-center justify-between mb-2">
              <span className="text-[11px] font-bold text-gray-400 tracking-wider">
                AUDIOPHILE PRESETS
              </span>
              <span className="text-[11px] text-purple-400 font-medium">
                Current: {equalizer.preset}
              </span>
            </div>
            <div className="flex gap-2 overflow-x-auto pb-1.5 scrollbar-thin scrollbar-thumb-white/10">
              {presetList.map((preset) => {
                const isSelected = equalizer.preset === preset
                return (
                  <button
                    key={preset}
                    onClick={() => setEqualizerPreset(preset)}
                    className={`px-3 py-1.5 rounded-xl text-xs font-medium whitespace-nowrap transition-all ${
                      isSelected
                        ? 'bg-purple-600/30 text-purple-200 border border-purple-500 shadow-md shadow-purple-900/30 font-semibold'
                        : 'bg-white/[0.04] text-gray-400 border border-white/[0.05] hover:bg-white/[0.08] hover:text-white'
                    }`}
                  >
                    {preset}
                  </button>
                )
              })}
            </div>
          </div>

          {/* 15-Band Tactile Hardware Capsule Sliders Rack */}
          <div className="bg-[#12131D] border border-white/[0.06] rounded-xl p-4">
            <div className="flex items-center justify-between mb-3">
              <span className="text-[11px] font-bold text-gray-400 tracking-wider">
                15-BAND ISO CAPSULE CONSOLE
              </span>
              <span className="text-[11px] font-mono text-gray-400">
                Resolution: 0.1 dB
              </span>
            </div>

            <div className="flex items-end justify-between gap-1 overflow-x-auto pb-2 scrollbar-thin scrollbar-thumb-white/10">
              {FREQUENCIES.map((freq, index) => {
                const gain = equalizer.bands[index] ?? 0
                const cat = getCategoryForFreq(freq)
                const catStyle = getCategoryColor(cat)
                const formattedGain = gain > 0 ? `+${gain.toFixed(1)}` : gain.toFixed(1)

                return (
                  <div
                    key={freq}
                    className="flex flex-col items-center min-w-[48px] bg-white/[0.02] border border-white/[0.04] rounded-xl py-2 px-1 hover:bg-white/[0.04] transition-colors"
                  >
                    {/* Category Badge */}
                    <span className={`text-[8px] font-bold px-1.5 py-0.5 rounded border uppercase tracking-wider mb-1 ${catStyle}`}>
                      {cat}
                    </span>

                    {/* Gain dB value */}
                    <span className="text-[10px] font-mono font-semibold text-gray-300 mb-2">
                      {formattedGain}
                    </span>

                    {/* Vertical Slider */}
                    <div className="h-32 flex items-center justify-center my-1 relative">
                      <input
                        type="range"
                        min="-8"
                        max="8"
                        step="0.1"
                        value={gain}
                        disabled={!equalizer.enabled}
                        onChange={(e) => setEqualizerBand(index, parseFloat(e.target.value))}
                        className="h-28 w-2 appearance-none bg-[#242538] rounded-full outline-none cursor-pointer disabled:cursor-not-allowed accent-purple-400"
                        style={{
                          writingMode: 'vertical-lr',
                          direction: 'rtl'
                        }}
                      />
                    </div>

                    {/* Frequency label */}
                    <span className="text-[10px] font-bold text-gray-200 mt-2 text-center whitespace-nowrap">
                      {FREQUENCY_LABELS[index]}
                    </span>
                  </div>
                )
              })}
            </div>
          </div>

          {/* Soundstage & Enhancers */}
          <div className="grid grid-cols-2 gap-3">
            {/* Deep Bass Boost */}
            <div className="bg-[#12131D] border border-white/[0.06] rounded-xl p-3.5 flex flex-col justify-between">
              <div className="flex items-center justify-between mb-2">
                <div className="flex items-center gap-2">
                  <Volume2 className="w-4 h-4 text-purple-400" />
                  <span className="text-xs font-bold text-white">Deep Bass Boost</span>
                </div>
                <span className="text-xs font-mono font-bold text-purple-300">
                  {equalizer.bassBoost}%
                </span>
              </div>
              <input
                type="range"
                min="0"
                max="100"
                value={equalizer.bassBoost}
                disabled={!equalizer.enabled}
                onChange={(e) => setBassBoost(parseInt(e.target.value))}
                className="w-full h-1.5 bg-[#25273A] rounded-full appearance-none cursor-pointer accent-purple-500"
              />
            </div>

            {/* 3D Spatial Virtualizer */}
            <div className="bg-[#12131D] border border-white/[0.06] rounded-xl p-3.5 flex flex-col justify-between">
              <div className="flex items-center justify-between mb-2">
                <div className="flex items-center gap-2">
                  <Sparkles className="w-4 h-4 text-sky-400" />
                  <span className="text-xs font-bold text-white">3D Spatial Soundstage</span>
                </div>
                <span className="text-xs font-mono font-bold text-sky-300">
                  {equalizer.virtualizer}%
                </span>
              </div>
              <input
                type="range"
                min="0"
                max="100"
                value={equalizer.virtualizer}
                disabled={!equalizer.enabled}
                onChange={(e) => setVirtualizer(parseInt(e.target.value))}
                className="w-full h-1.5 bg-[#25273A] rounded-full appearance-none cursor-pointer accent-sky-400"
              />
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}
