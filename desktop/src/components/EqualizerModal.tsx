import React from 'react'
import { X, Sliders, Volume2, Sparkles } from 'lucide-react'
import { usePlayerStore } from '../store/usePlayerStore'
import { EQUALIZER_PRESETS, FREQUENCY_LABELS } from '../services/audioEngine'
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

  // Calculate live Cubic Bezier SVG path from the 5 band gains
  const width = 480
  const height = 110
  const points = equalizer.bands.map((gain, i) => {
    const x = (i / 4) * (width - 40) + 20
    const y = height / 2 - (gain / 12) * (height / 2 - 14)
    return { x, y }
  })

  // Build smooth SVG curve
  let curvePath = `M ${points[0].x} ${points[0].y}`
  for (let i = 0; i < points.length - 1; i++) {
    const p0 = points[i]
    const p1 = points[i + 1]
    const cpX = (p0.x + p1.x) / 2
    curvePath += ` C ${cpX} ${p0.y}, ${cpX} ${p1.y}, ${p1.x} ${p1.y}`
  }

  const fillPath = `${curvePath} L ${points[points.length - 1].x} ${height} L ${points[0].x} ${height} Z`

  return (
    <div className="fixed inset-0 bg-black/70 backdrop-blur-md z-50 flex items-center justify-center p-4 animate-fade-in select-none">
      <div className="w-full max-w-xl bg-[#11121B] border border-white/[0.08] rounded-2xl p-6 shadow-2xl relative shadow-purple-900/20">
        {/* Header */}
        <div className="flex items-center justify-between pb-4 border-b border-white/[0.08]">
          <div className="flex items-center gap-3">
            <div className="w-9 h-9 rounded-xl bg-purple-600/20 border border-purple-500/30 flex items-center justify-center">
              <Sliders className="w-5 h-5 text-purple-400" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h3 className="text-base font-bold text-white">Tunex Equalizer</h3>
                <span className="text-[10px] px-1.5 py-0.5 rounded bg-purple-500/20 text-purple-300 font-mono">
                  DSP Real-time
                </span>
              </div>
              <p className="text-xs text-gray-400">
                Studio-grade hardware sound tuning & audio enhancements
              </p>
            </div>
          </div>

          <div className="flex items-center gap-3">
            {/* Master Toggle */}
            <button
              onClick={toggleEqualizer}
              className={`px-3 py-1 rounded-full text-xs font-semibold transition-all ${
                equalizer.enabled
                  ? 'bg-purple-600 text-white shadow-lg shadow-purple-600/40'
                  : 'bg-white/[0.08] text-gray-400'
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

        {/* Live Audio Visualizer Frequency Curve */}
        <div className="my-5 bg-[#0A0B10] border border-white/[0.06] rounded-xl p-3 relative overflow-hidden">
          <svg className="w-full h-24 overflow-visible" viewBox={`0 0 ${width} ${height}`}>
            <defs>
              <linearGradient id="eqGlow" x1="0" y1="0" x2="0" y2="1">
                <stop offset="0%" stopColor="#8B5CF6" stopOpacity="0.45" />
                <stop offset="100%" stopColor="#8B5CF6" stopOpacity="0.0" />
              </linearGradient>
            </defs>
            {/* Zero dB Center Line */}
            <line
              x1="10"
              y1={height / 2}
              x2={width - 10}
              y2={height / 2}
              stroke="rgba(255,255,255,0.1)"
              strokeDasharray="4 4"
            />
            {/* Curve Fill */}
            <path d={fillPath} fill="url(#eqGlow)" />
            {/* Curve Stroke */}
            <path
              d={curvePath}
              fill="none"
              stroke="#A78BFA"
              strokeWidth="2.5"
              strokeLinecap="round"
            />
            {/* Points */}
            {points.map((pt, i) => (
              <circle
                key={i}
                cx={pt.x}
                cy={pt.y}
                r="4.5"
                fill="#C4B5FD"
                className="transition-all"
              />
            ))}
          </svg>
        </div>

        {/* 10 Presets Chips */}
        <div className="mb-6">
          <div className="text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-2">
            Sound Presets
          </div>
          <div className="flex flex-wrap gap-1.5">
            {(Object.keys(EQUALIZER_PRESETS) as EqualizerPresetName[]).map((presetName) => (
              <button
                key={presetName}
                onClick={() => setEqualizerPreset(presetName)}
                className={`px-3 py-1 rounded-full text-xs font-medium transition-all ${
                  equalizer.preset === presetName
                    ? 'bg-purple-600 text-white shadow-md shadow-purple-600/30'
                    : 'bg-white/[0.05] hover:bg-white/[0.1] text-gray-300'
                }`}
              >
                {presetName}
              </button>
            ))}
          </div>
        </div>

        {/* 5 Vertical Sliders */}
        <div className="grid grid-cols-5 gap-3 py-2 bg-[#161722]/50 border border-white/[0.05] rounded-xl p-4">
          {FREQUENCY_LABELS.map((label, idx) => {
            const gain = equalizer.bands[idx] || 0
            return (
              <div key={label} className="flex flex-col items-center gap-2">
                <span className="text-[11px] font-mono text-purple-300 font-bold">
                  {gain > 0 ? `+${gain}` : gain} dB
                </span>
                <input
                  type="range"
                  min="-12"
                  max="12"
                  step="1"
                  value={gain}
                  disabled={!equalizer.enabled}
                  onChange={(e) => setEqualizerBand(idx, parseInt(e.target.value, 10))}
                  className="h-28 -rotate-90 my-6 accent-purple-500 cursor-pointer"
                />
                <span className="text-[11px] font-medium text-gray-400">
                  {label}
                </span>
              </div>
            )
          })}
        </div>

        {/* Bass Boost & 3D Virtualizer */}
        <div className="grid grid-cols-2 gap-4 mt-5">
          <div className="bg-[#141520] border border-white/[0.06] rounded-xl p-3.5 flex flex-col gap-2">
            <div className="flex items-center justify-between text-xs">
              <span className="font-semibold text-white flex items-center gap-1.5">
                <Volume2 className="w-3.5 h-3.5 text-purple-400" />
                Bass Boost
              </span>
              <span className="font-mono text-purple-300 font-bold">{equalizer.bassBoost}%</span>
            </div>
            <input
              type="range"
              min="0"
              max="100"
              value={equalizer.bassBoost}
              disabled={!equalizer.enabled}
              onChange={(e) => setBassBoost(parseInt(e.target.value, 10))}
              className="w-full accent-purple-500 cursor-pointer"
            />
          </div>

          <div className="bg-[#141520] border border-white/[0.06] rounded-xl p-3.5 flex flex-col gap-2">
            <div className="flex items-center justify-between text-xs">
              <span className="font-semibold text-white flex items-center gap-1.5">
                <Sparkles className="w-3.5 h-3.5 text-indigo-400" />
                3D Virtualizer
              </span>
              <span className="font-mono text-indigo-300 font-bold">{equalizer.virtualizer}%</span>
            </div>
            <input
              type="range"
              min="0"
              max="100"
              value={equalizer.virtualizer}
              disabled={!equalizer.enabled}
              onChange={(e) => setVirtualizer(parseInt(e.target.value, 10))}
              className="w-full accent-indigo-500 cursor-pointer"
            />
          </div>
        </div>
      </div>
    </div>
  )
}
