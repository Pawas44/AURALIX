# AURALIX — Multi-Platform Client (Android & iOS)

A sleek, dual-mode client inspired by **YouTube** and **YouTube Music**, available natively for both **Android** and **Apple iOS**.

---

## 📂 Project Architecture

```
AURALIX/
├── .github/
│   └── workflows/
│       └── build-ios.yml       # Cloud CI/CD: Automated builds for Android APK & iOS IPA
├── android/                    # Android Native App (Kotlin + Jetpack Compose)
│   ├── app/
│   │   └── src/main/java/com/videoguard/app/
│   │       ├── data/           # Repositories & Search Engines
│   │       ├── model/          # Data Models (VideoItem, UserAccount, etc.)
│   │       ├── service/        # Playback controllers & background services
│   │       ├── ui/
│   │       │   ├── components/ # TopBar, BottomBar, MiniPlayer, Cards
│   │       │   ├── screens/    # Feed, Music Home, Player, You, Search
│   │       │   └── theme/      # Colors, Typography & LiquidGlass design
│   │       └── MainActivity.kt
│   ├── build.gradle.kts
│   └── gradlew
├── ios/                        # iOS Native App (Swift 5 + SwiftUI)
│   ├── VideoGuard.xcodeproj    # Native Xcode Project (Target iOS 16+)
│   └── VideoGuard/
│       ├── Data/               # Repositories & Search Engine
│       ├── Models/             # AppMode, VideoItem, UserPlaylist, etc.
│       ├── Services/           # MediaPlaybackService (ObservableObject)
│       ├── Theme/              # Colors & Dark Mode schemes
│       ├── Views/
│       │   ├── Components/     # TopBar, MiniPlayer, BottomNav, Controls
│       │   └── Screens/        # YouTubeFeed, MusicHome, Player, Search, etc.
│       ├── Assets.xcassets     # App Icons & Colors
│       ├── Info.plist
│       └── VideoGuardApp.swift # SwiftUI App Entry Point
├── .gitignore
└── README.md
```

---

## ✨ Features (Both Android & iOS)

- 🌓 **Dual Modes**: Seamlessly switch between **YouTube** and **YouTube Music** styling on the fly.
- 🎬 **Video Feed**: Category filters, responsive video cards with duration badges, view counts, and channel metadata.
- 🎵 **Music Player**:
  - Full-screen player with high-res artwork, lyrics drawer, progress slider, and seek controls.
  - Shuffle, Repeat One, Repeat All, Next, and Previous queue navigation.
- 📱 **Mini Player**: Floating mini-player at the bottom across all screens with live playback progress and controls.
- 🔍 **Live Search**: Instant search engine with trending topics, query suggestions, and category filtering.
- 📚 **Library & Channels**: Watch history, playlists (Liked Videos, Watch Later, Workout, Chill, Coding), and custom channel screens.
- ☁️ **Cloud CI/CD**: Automatic simultaneous `.apk` and `.ipa` builds using GitHub Actions — **no local Mac required!**

---

## 📲 How to Download & Install

### 1. Download Latest Builds
1. Navigate to the [Latest GitHub Release](https://github.com/Pawas44/AURALIX/releases/latest) (or check [GitHub Actions Runs](https://github.com/Pawas44/AURALIX/actions)).
2. Download directly under **Assets**:
   - **`AURALIX.apk`**: Android installer.
   - **`AURALIX.ipa`**: iOS installer.

---

### 2. Installation on Android Devices
1. Download `AURALIX.apk` directly from the latest release.
2. Transfer it to your Android device.
3. Tap on `AURALIX.apk` and select **Install**.

---

### 3. Installation on Apple iPhone / iPad (No Jailbreak Needed)

#### Using Sideloadly (Recommended)
1. Download [Sideloadly](https://sideloadly.io/) on Windows or Mac.
2. Connect your iPhone to your PC using a USB cable.
3. Download `AURALIX.ipa` directly from the latest release.
4. Drag `AURALIX.ipa` into Sideloadly, enter your Apple ID, and click **Start**.
5. Once finished, on the iPhone:
   - Go to **Settings > General > VPN & Device Management** and tap **Trust**.
   - If on iOS 16+, go to **Settings > Privacy & Security > Developer Mode** and turn it **ON** (phone reboots once).
