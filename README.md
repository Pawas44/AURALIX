# 🛡️ VideoGuard for iOS (YouTube & YouTube Music Dual Mode)

VideoGuard is a sleek, modern, native iOS client inspired by YouTube and YouTube Music, built with **SwiftUI** for iOS 16+.

---

## ✨ Features

- 🌓 **Dual Modes**: Seamlessly switch between **YouTube** and **YouTube Music** styling on the fly.
- 🎬 **Video Feed**: Category filters, responsive video cards with duration badges, view counts, and channel metadata.
- 🎵 **Music Player**:
  - Full-screen player with high-res artwork, lyrics drawer, progress slider, and seek controls.
  - Shuffle, Repeat One, Repeat All, Next, and Previous queue navigation.
- 📱 **Mini Player**: Floating mini-player at the bottom across all screens with live playback progress and controls.
- 🔍 **Live Search**: Integrated instant search engine with trending topics, query suggestions, and category filtering.
- 📚 **Library & Channels**: Watch history, playlists (Liked Videos, Watch Later, Workout, Chill, Coding), and custom channel screens.
- ☁️ **Cloud CI/CD**: Automatic `.ipa` builds using GitHub Actions on macOS runners — **no local Mac required!**

---

## 📲 How to Install on iPhone / iPad

### Method 1: Sideloadly (Recommended)
1. Download [Sideloadly](https://sideloadly.io/) on Windows or Mac.
2. Connect your iPhone via USB.
3. Download the latest `VideoGuard.ipa` from the [GitHub Actions Releases / Artifacts](https://github.com/Pawas44/videoguard-ios/actions).
4. Drag `VideoGuard.ipa` into Sideloadly, enter your Apple ID, and click **Start**.
5. On your iPhone:
   - Go to **Settings > General > VPN & Device Management** and tap **Trust**.
   - If on iOS 16+, go to **Settings > Privacy & Security > Developer Mode** and turn it **ON**.

### Method 2: On-Device Install (Scarlet / SideStore - No PC)
1. Download the `VideoGuard.ipa` to the iPhone's **Files** app.
2. Install [Scarlet](https://usescarlet.com/) or SideStore on your device.
3. Open Scarlet, tap the Sideload icon, import `VideoGuard.ipa`, and tap Install.

---

## 🛠️ Architecture

- **Language**: Swift 5.0
- **Framework**: SwiftUI
- **Minimum iOS Target**: iOS 16.0+
- **Pattern**: MVVM + Singleton Repositories (`ObservableObject` state flows)
- **CI/CD**: GitHub Actions (`.github/workflows/build-ios.yml`)
