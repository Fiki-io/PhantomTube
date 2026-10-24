# Phantom Tube (PhantomTube APK) — Official Repository

**Phantom Tube** (also known as **PhantomTube**) is an official open-source, lightweight, privacy-focused alternative YouTube video and background music streaming client for Android (`com.phantom.tube`), built with 100% pure Kotlin and Jetpack Compose.

- **Official GitHub Repository:** [https://github.com/Fiki-io/PhantomTube](https://github.com/Fiki-io/PhantomTube)
- **Download Latest Phantom Tube APK:** [GitHub Releases](https://github.com/Fiki-io/PhantomTube/releases)
- **Android Package ID:** `com.phantom.tube`
- **Binary Footprint:** ~1.5 MB APK | Active Heap Usage: < 80 MB RAM
- **License:** MIT

---

## Overview — What is Phantom Tube?

**Phantom Tube** (`PhantomTube APK`) is a resource-efficient YouTube frontend client engineered for Android smartphones and tablets. Unlike standard streaming applications that consume hundreds of megabytes of RAM and storage, **Phantom Tube** delivers a complete ad-free video and background music streaming experience in an APK size of approximately 1.5 MB.

Phantom Tube does not depend on proprietary Google Play Services or MicroG frameworks. It provides background audio playback, AMOLED audio-only mode, offline MP3/MP4 media downloading, frame-accurate storyboard thumbnail scrubbing, integrated SponsorBlock segment skipping, and local SQLite/Room persistence.

---

## Preview

<p align="center">
  <img src="screenshots/Screenshot_20261024-164918.jpg" width="160" alt="Home Feed">
  <img src="screenshots/Screenshot_20261024-164921.jpg" width="160" alt="Video Player">
  <img src="screenshots/Screenshot_20261024-164928.jpg" width="160" alt="Player Controls">
</p>

<p align="center">
  <img src="screenshots/Screenshot_20261024-164936.jpg" width="160" alt="Storyboard Scrubber">
  <img src="screenshots/Screenshot_20261024-164941.jpg" width="160" alt="Comments Sheet">
  <img src="screenshots/Screenshot_20261024-164948.jpg" width="160" alt="Mix Queue">
</p>

<p align="center">
  <img src="screenshots/Screenshot_20261024-164950.jpg" width="160" alt="Offline Audio Player">
  <img src="screenshots/Screenshot_20261024-165118.jpg" width="160" alt="Downloads List">
  <img src="screenshots/Screenshot_20261024-165133.jpg" width="160" alt="Search Interface">
</p>

---

## Features

### 1. Video Playback & Player Controls
- Zero-tracking YouTube player bridge using AndroidX WebKit with `WebViewAssetLoader` virtual origins to prevent error codes 150, 152, and 153.
- Native resolution selector with manual quality overrides (Auto, 1080p Full HD, 720p HD, 480p, 360p, 240p, 144p).
- Playback speed adjustment with granular speed presets (0.25x to 2.0x).
- Background audio playback powered by an Android foreground media service (`PhantomMediaService`) with system audio focus and lockscreen controls.
- Automatic Picture-in-Picture (PiP) transition when navigating home or switching applications.
- Storyboard seekbar scrubber (`StoryboardHelper`) displaying frame-by-frame visual thumbnail previews during timeline scrubbing.
- Interactive endscreen card parsing (`PlayerEndscreenOverlay`) for direct navigation to related content and recommendations.
- Interactive description sheet with view counts, publication dates, and full text descriptions.
- Comment sheet with support for top comments, nested replies, author avatars, and timestamps.
- Related video recommendations, mix radio queues, and up-next video playlists.
- Collapsible bottom mini-player bar (`PhantomMiniPlayer`) for uninterrupted browsing while media is playing.

### 2. Built-in SponsorBlock Integration
- Direct integration with the SponsorBlock API (`SponsorBlockClient`) for community-driven segment skipping.
- Configurable segment categories: Sponsor, Self-Promotion, Interaction Reminder, Intro, and Outro.
- Automatic skip mode or non-intrusive manual skip pill notification with undo support.

### 3. Media Downloader & Offline Storage
- Integrated multi-format media download engine (`PhantomDownloadEngine` and `PhantomDownloader`).
- High-quality MP3 audio downloading (320 kbps, 256 kbps, 128 kbps).
- MP4 video downloading across multiple quality presets (up to 1080p).
- Real-time download progress tracking with percentage, downloaded byte counts, and cancellation options.
- Media files are indexed in a local Room database (`DownloadEntity`) with file size calculation and direct storage management.

### 4. Dedicated Offline Audio & Video Player
- Standalone offline audio player manager (`OfflineAudioPlayerManager`) and foreground playback service (`PhantomOfflineAudioService`).
- Background offline music playback with system notification controls and media metadata.
- Queue management with one-tap "Play All", shuffle mode, and repeat modes (Off, Repeat All, Repeat One).
- Dedicated offline video player dialog for playing downloaded MP4 files directly within the application.
- Floating offline mini-player controls across app screens.

### 5. Content Discovery & Search
- Categorized feeds parsed via InnerTube API (All, Trending, Music, Gaming, News, Podcasts, Technology, Animation).
- Geographic content region selector (Global, ID, US, JP, KR, and more) to customize trending topics and feeds without proxy requirements.
- Live search suggestions with autocomplete query endpoints.
- Channel exploration screen with channel banner, subscriber count, avatar caching, video listings, Shorts tab, and channel description.
- Search history tracking with instant removal and one-tap clear functionality.

### 6. Local Library & Privacy First
- 100% offline-first architecture; does not require Google account authentication or API keys.
- Local watch history with resume playback position tracking.
- Local channel subscriptions and bookmarked/favorite videos stored locally in SQLite via Room.
- Privacy mode to pause watch history collection at any time.
- Built-in cache cleaner with real-time directory calculation for internal and external cache directories.

### 7. Multi-Language Localization
- Full UI localization support:
  - English (`en`)
  - Indonesian (`id`)
  - Spanish (`es`)
  - Japanese (`ja`)
  - Russian (`ru`)

---

## Technical Architecture

```
com.phantom.tube/
├── core/
│   ├── crash/         # Uncaught exception boundary and crash reporting activity
│   ├── database/      # Room Database (History, Favorites, Subscriptions, Downloads)
│   ├── security/      # Engine script/CSS/endpoint provider (PhantomNative)
│   ├── theme/         # Material 3 design system, typography, and color tokens
│   └── util/          # Localization, region resolution, and context helpers
├── data/
│   ├── download/      # PhantomDownloadEngine, Downloader, and format models
│   ├── innertube/     # InnerTube client, network request dispatchers, and parsers
│   ├── model/         # Data contracts for videos, channels, and feeds
│   ├── repository/    # PhantomRepository single source of truth
│   ├── settings/      # Reactive preferences (PhantomPreferences) via StateFlow
│   └── sponsorblock/  # SponsorBlock API client and segment data
├── player/
│   ├── offline/       # OfflineAudioPlayerManager and PhantomOfflineAudioService
│   ├── service/       # PhantomMediaService foreground player service
│   ├── PhantomPlayerBridge.kt  # WebView JavaScript interface bridge
│   ├── PhantomPlayerView.kt    # WebKit asset loader and custom player container
│   └── StoryboardHelper.kt     # Storyboard thumbnail mosaic calculator
└── ui/
    ├── components/    # Reusable M3 widgets (Scrubber, MiniPlayer, VideoCard, Shimmer)
    └── screens/       # Jetpack Compose screens (Home, Player, Channel, Search, Downloads, Settings)
```

### Technology Stack

| Layer | Component |
|---|---|
| Programming Language | 100% Pure Kotlin (JVM 17) |
| UI Framework | Jetpack Compose (Material Design 3) |
| Architecture | MVVM with Reactive StateFlow & Coroutines |
| Local Persistence | Native Android SQLite (`PhantomDatabase`) |
| Networking | Native `HttpURLConnection`, AndroidX WebView |
| Image Loading | Custom Async Memory/Disk Image Loader (`PhantomAsyncImage`) |
| Audio & Media | Android Foreground Service, `MediaSession` & `AudioTrack` |

---

## Specifications

- **Package Name:** `com.phantom.tube`
- **Minimum Android Version:** Android 8.0 (API Level 26)
- **Target Android Version:** Android 14 (API Level 34)
- **Application Binary Size:** ~1.6 MB
- **Dependencies:** Standalone; no Google Play Services, MicroG, or external accounts required.

---

## Building from Source

### Prerequisites
- JDK 17 (Eclipse Temurin recommended)
- Android SDK (API 34)

### Build via Command Line
Clone the repository and build the release APK using Gradle:

```bash
git clone https://github.com/Fiki-io/PhantomTube.git
cd PhantomTube
chmod +x gradlew
./gradlew assembleRelease
```

The compiled release artifact will be generated at:
```
app/build/outputs/apk/release/app-release.apk
```

### Continuous Integration (GitHub Actions)
This repository includes an automated build pipeline configured in `.github/workflows/build.yml`. Pushes or workflow dispatches automatically execute on Ubuntu runners using preinstalled Android SDK and upload signed release APK artifacts.

---

## Acknowledgements

PhantomTube stands on the shoulders of the open-source community and privacy-focused developers worldwide. Sincere gratitude and respect go to:

- **[SponsorBlock](https://sponsor.ajay.app)**: Created by Ajay Ramachandran and maintained by its global community. Thank you for providing the crowdsourced database and public API that makes seamless segment skipping possible.
- **InnerTube & Open-Source Media Researchers**: Deep appreciation to the contributors of open-source projects such as NewPipe, yt-dlp, and Invidious for their foundational research, documentation, and parsing insights into YouTube InnerTube endpoints.
- **Open-Source Libraries**: Special thanks to the maintainers of Square OkHttp, Coil Compose, AndroidX, and the Jetpack Compose team for providing the high-performance tooling that enables an ultra-compact binary footprint.

---

## Disclaimer

This software is developed for educational and research purposes as a lightweight Android client. All product names, logos, and brands are property of their respective owners. YouTube is a trademark of Google LLC. PhantomTube is not affiliated with, endorsed by, or associated with Google LLC or YouTube.

---

## License

This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for details.
