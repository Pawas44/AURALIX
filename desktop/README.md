# Auralix Desktop (PC) 🎧✨

> **Auralix PC** is a modern, premium desktop music player inspired by **ByTune** and **Spotify**, built specifically for Windows and macOS.

---

## ⚡ Tech Stack (Same as ByTune)
* **Framework:** Electron (Cross-platform Desktop wrapper with custom frameless window)
* **Frontend:** React 18 + TypeScript + Vite
* **Styling:** Tailwind CSS + Glassmorphism + Obsidian Dark theme (`#0B0C10`, `#8B5CF6`)
* **Audio Engine:** Native Web Audio API (`AudioContext`, 5-band peaking `BiquadFilterNode`, `LowShelf` BassBoost, `Convolver` 3D surround)
* **Streaming Source:** YouTube Music Catalog + Piped / Invidious stream resolvers
* **Synced Lyrics:** LRCLIB API with real-time scrolling karaoke timestamps
* **Discord Integration:** Native Discord Rich Presence (App ID: `1358372087017767073`) with Telegram invite button (`https://t.me/auralix_music`)
* **State Management:** Zustand with local persistent cache
* **Desktop Packaging:** Electron Builder (Windows NSIS `.exe` installer & macOS `.dmg`)

---

## 🚀 How to Run in Development Mode

You can run Auralix PC directly using either the root helper script or npm:

### Option 1: One-Click Script (from repository root)
```cmd
run-desktop.cmd
```

### Option 2: Terminal / NPM
```bash
cd desktop
npm install
npm run dev
```

---

## 📦 How to Build Production Installer (.exe)

```cmd
build-desktop.cmd
```
Or:
```bash
cd desktop
npm run dist
```
The installer will be generated in `desktop/release/Auralix-Setup-1.0.0.exe`.

---

## 🌟 Key Features
1. **Ad-Free Music Streaming:** Search and stream any song, artist, album, or playlist from YouTube Music.
2. **Tunex Real-Time DSP Equalizer:**
   * 10 Sound Presets: *Flat, Bass Boost, Rock, Pop, Hip Hop, Electronic, Jazz, Classical, Vocal Boost, Custom*
   * 5 Hardware Frequency Sliders: 60Hz, 230Hz, 910Hz, 3.6kHz, 14kHz
   * Bass Boost (0–100%) & 3D Spatial Virtualizer (0–100%)
   * Interactive live Cubic Bezier frequency response curve.
3. **Word-Synced Karaoke Lyrics:** Real-time synchronized lyrics highlighting line-by-line as the music plays.
4. **Discord Rich Presence (PC):** Displays current track, artist, album art, elapsed time, and your Auralix community link on Discord.
5. **Local Audio Player:** Drag and drop or open any local MP3, M4A, FLAC, WAV, OGG file from your computer.
6. **Picture-in-Picture Mini Player:** Floating desktop mini-player for multitasking while gaming or working.
7. **Custom Playlists & Liked Songs:** Save tracks, build custom playlists, and store favorites with instant search.
