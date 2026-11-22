import React, { useState } from 'react'
import { TitleBar } from './components/TitleBar'
import { Sidebar } from './components/Sidebar'
import { PlayerBar } from './components/PlayerBar'
import { EqualizerModal } from './components/EqualizerModal'
import { LyricsModal } from './components/LyricsModal'
import { MiniPlayer } from './components/MiniPlayer'
import { HomeView } from './views/HomeView'
import { SearchView } from './views/SearchView'
import { LibraryView } from './views/LibraryView'
import { SettingsView } from './views/SettingsView'

export const App: React.FC = () => {
  const [currentView, setCurrentView] = useState<'home' | 'search' | 'library' | 'settings'>('home')

  return (
    <div className="flex flex-col h-screen w-screen bg-[#0B0C10] text-white overflow-hidden select-none">
      {/* 1. Custom Frameless Titlebar */}
      <TitleBar onSearchFocus={() => setCurrentView('search')} />

      {/* 2. Main Body with Sidebar + Active View */}
      <div className="flex flex-1 overflow-hidden">
        <Sidebar currentView={currentView} setCurrentView={setCurrentView} />

        <main className="flex-1 bg-[#0B0C10] overflow-y-auto">
          {currentView === 'home' && <HomeView />}
          {currentView === 'search' && <SearchView />}
          {currentView === 'library' && <LibraryView />}
          {currentView === 'settings' && <SettingsView />}
        </main>
      </div>

      {/* 3. Persistent Bottom Player Bar */}
      <PlayerBar />

      {/* 4. Overlays & Modals */}
      <EqualizerModal />
      <LyricsModal />
      <MiniPlayer />
    </div>
  )
}
export default App
