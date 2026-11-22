import React, { useState, useEffect } from 'react'
import { Minus, Square, X, Music, Copy } from 'lucide-react'

interface TitleBarProps {
  onSearchFocus?: () => void
}

export const TitleBar: React.FC<TitleBarProps> = ({ onSearchFocus }) => {
  const [isMax, setIsMax] = useState(false)

  useEffect(() => {
    if (window.auralixAPI?.isMaximized) {
      window.auralixAPI.isMaximized().then(setIsMax)
    }
  }, [])

  const handleMin = () => window.auralixAPI?.minimizeWindow()
  const handleMax = async () => {
    window.auralixAPI?.maximizeWindow()
    if (window.auralixAPI?.isMaximized) {
      setIsMax(await window.auralixAPI.isMaximized())
    }
  }
  const handleClose = () => window.auralixAPI?.closeWindow()

  return (
    <header className="h-10 bg-[#0B0C10] border-b border-white/[0.06] flex items-center justify-between px-3 select-none [-webkit-app-region:drag] z-50">
      {/* App branding */}
      <div className="flex items-center gap-2 [-webkit-app-region:no-drag]">
        <div className="w-5 h-5 rounded-md bg-gradient-to-tr from-purple-600 to-indigo-500 flex items-center justify-center shadow-lg shadow-purple-500/25">
          <Music className="w-3.5 h-3.5 text-white" />
        </div>
        <span className="font-bold text-xs tracking-wider bg-gradient-to-r from-purple-400 via-violet-300 to-white bg-clip-text text-transparent">
          AURALIX
        </span>
        <span className="text-[10px] text-purple-400/80 font-mono px-1.5 py-0.5 rounded bg-purple-500/10 border border-purple-500/20">
          PC
        </span>
      </div>

      {/* Center Search Trigger */}
      <div className="flex-1 max-w-md mx-4 [-webkit-app-region:no-drag]">
        <button
          onClick={onSearchFocus}
          className="w-full h-7 bg-[#141520] hover:bg-[#1A1C2B] border border-white/[0.08] hover:border-purple-500/40 rounded-full px-3.5 flex items-center justify-between text-xs text-gray-400 transition-all cursor-pointer shadow-inner"
        >
          <span>Search songs, albums, artists...</span>
          <kbd className="text-[10px] bg-white/[0.08] text-gray-300 px-1.5 py-0.5 rounded font-mono">Ctrl + K</kbd>
        </button>
      </div>

      {/* Windows Window Control Buttons */}
      <div className="flex items-center [-webkit-app-region:no-drag]">
        <button
          onClick={handleMin}
          title="Minimize"
          className="w-9 h-7 flex items-center justify-center text-gray-400 hover:text-white hover:bg-white/[0.08] rounded transition-colors"
        >
          <Minus className="w-3.5 h-3.5" />
        </button>
        <button
          onClick={handleMax}
          title={isMax ? 'Restore' : 'Maximize'}
          className="w-9 h-7 flex items-center justify-center text-gray-400 hover:text-white hover:bg-white/[0.08] rounded transition-colors"
        >
          {isMax ? <Copy className="w-3 h-3 rotate-180" /> : <Square className="w-3 h-3" />}
        </button>
        <button
          onClick={handleClose}
          title="Close"
          className="w-9 h-7 flex items-center justify-center text-gray-400 hover:text-white hover:bg-red-600/90 rounded transition-colors"
        >
          <X className="w-4 h-4" />
        </button>
      </div>
    </header>
  )
}
