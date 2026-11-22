import React from 'react'
import { Sliders, Send, Music2, Cpu, CheckCircle } from 'lucide-react'
import { usePlayerStore } from '../store/usePlayerStore'

export const SettingsView: React.FC = () => {
  const { equalizer, toggleEqualizer } = usePlayerStore()

  return (
    <div className="p-8 max-w-4xl mx-auto space-y-8 select-none overflow-y-auto">
      <div>
        <h2 className="text-2xl font-bold text-white">Settings & Preferences</h2>
        <p className="text-xs text-gray-400 mt-1">
          Configure audio streaming, Discord Rich Presence, and PC performance
        </p>
      </div>

      {/* Audio Engine */}
      <section className="bg-[#12131D] border border-white/[0.08] rounded-2xl p-6 space-y-4">
        <div className="flex items-center gap-3">
          <div className="w-8 h-8 rounded-lg bg-purple-600/20 flex items-center justify-center text-purple-400">
            <Sliders className="w-4 h-4" />
          </div>
          <div>
            <h3 className="text-sm font-bold text-white">Tunex Audio DSP Engine</h3>
            <p className="text-xs text-gray-400">Real-time Web Audio API hardware frequency filters</p>
          </div>
        </div>

        <div className="flex items-center justify-between pt-2 border-t border-white/[0.06]">
          <div>
            <span className="text-xs font-semibold text-white block">Equalizer Master Switch</span>
            <span className="text-[11px] text-gray-400">
              Active preset: <span className="text-purple-300 font-mono">{equalizer.preset}</span>
            </span>
          </div>
          <button
            onClick={toggleEqualizer}
            className={`px-3 py-1 rounded-full text-xs font-semibold transition-all ${
              equalizer.enabled
                ? 'bg-purple-600 text-white shadow-md shadow-purple-600/30'
                : 'bg-gray-800 text-gray-400'
            }`}
          >
            {equalizer.enabled ? 'ON' : 'OFF'}
          </button>
        </div>
      </section>

      {/* Discord Rich Presence */}
      <section className="bg-[#12131D] border border-white/[0.08] rounded-2xl p-6 space-y-4">
        <div className="flex items-center gap-3">
          <div className="w-8 h-8 rounded-lg bg-indigo-600/20 flex items-center justify-center text-indigo-400">
            <Cpu className="w-4 h-4" />
          </div>
          <div>
            <h3 className="text-sm font-bold text-white">Discord Rich Presence (PC)</h3>
            <p className="text-xs text-gray-400">
              Shows your active song, artist, album art, and elapsed time to your Discord friends
            </p>
          </div>
        </div>

        <div className="space-y-3 pt-2 border-t border-white/[0.06] text-xs">
          <div className="flex items-center justify-between">
            <span className="text-gray-300">Application ID</span>
            <span className="font-mono text-gray-400">1358372087017767073</span>
          </div>

          <div className="flex items-center justify-between">
            <span className="text-gray-300">Status</span>
            <span className="flex items-center gap-1.5 text-emerald-400 font-semibold">
              <CheckCircle className="w-3.5 h-3.5" />
              Connected & Active
            </span>
          </div>

          <div className="flex items-center justify-between">
            <span className="text-gray-300">Invite Button</span>
            <span className="text-purple-300 font-mono">https://t.me/auralix_music</span>
          </div>
        </div>
      </section>

      {/* Community */}
      <section className="bg-gradient-to-r from-purple-900/30 to-indigo-900/30 border border-purple-500/20 rounded-2xl p-6 flex items-center justify-between">
        <div className="flex items-center gap-3.5">
          <div className="w-10 h-10 rounded-xl bg-purple-600/30 flex items-center justify-center">
            <Send className="w-5 h-5 text-purple-300" />
          </div>
          <div>
            <h4 className="text-sm font-bold text-white">Join Auralix Telegram Community</h4>
            <p className="text-xs text-gray-400">Stay updated with PC & Mobile releases and features</p>
          </div>
        </div>
        <a
          href="https://t.me/auralix_music"
          target="_blank"
          rel="noreferrer"
          className="px-4 py-2 rounded-xl bg-purple-600 hover:bg-purple-500 text-white text-xs font-bold transition-all shadow-lg shadow-purple-600/30"
        >
          Open Channel
        </a>
      </section>

      {/* App Version Info */}
      <div className="text-center pt-6 text-[11px] text-gray-500 font-mono">
        Auralix Desktop v1.0.0 • Built with Electron, React, TypeScript & Vite
      </div>
    </div>
  )
}
