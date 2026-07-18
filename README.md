# MiCaSong

**MiCaSong** is an offline-first, multi-provider **music & audiobook player for Android and
Android Auto**. It aggregates your local library and remote servers (Subsonic/OpenSubsonic,
Jellyfin, …) into one unified, fast, fully-offline-capable library, with deep personalization and
an advanced audio engine.

- **Offline-first** — everything syncs to a local database; browse and play without a connection.
- **Multi-provider** — local device, Subsonic/OpenSubsonic/Navidrome, Jellyfin, with a clean
  connector interface for adding more.
- **Personalization** — configurable home, library, detail pages; custom themes (JSON), app styles,
  profiles.
- **Advanced audio** — gapless, equalizer (GEQ 5/10/15/31 + AutoEQ), ReplayGain, smart fades,
  multiple queues, Smart Queue/Flow, weighted shuffle.
- **Ecosystem** — Android Auto, home-screen widget, encrypted backup/restore, broadcast automation
  API (Tasker), Wear OS configuration.

## Download

Grab the latest APK from **[Releases](https://github.com/borborborja/micasong/releases)**. Two
editions are published:

- **MiCaSong-foss.apk** — 100% free/open-source, no proprietary dependencies (F-Droid clean).
- **MiCaSong-full.apk** — adds proprietary components (Google Cast).

They can be installed side by side (the FOSS edition uses the `.foss` application-id suffix).

---

## Features

**Library & providers**
- Local device provider (MediaStore) with background scan into a unified Room database.
- Subsonic/OpenSubsonic/Navidrome and Jellyfin server providers (salted-token / MediaBrowser auth).
- Add/remove servers from Settings; connections persist across restarts.
- Differential server sync that never clobbers local user state (favorites, ratings, play counts).
- Custom tag post-processing (multi-value, separators, MusicBrainz IDs, MusicBee Love mapping),
  Artist Information Folder (`artist.nfo`) parsing, M3U/PLS playlist import, internet radio.

**Playback & audio**
- Media3/ExoPlayer engine: gapless, media session, notification, audio focus.
- Full equalizer: per-output profiles, presets (5/10/15/31 bands), preamp, bass boost, virtualizer,
  applied live (local playback only), plus AutoEQ (`GraphicEQ.txt` / EqualizerAPO) import.
- ReplayGain, crossfade curves, smart fades (waveform silence analysis), transcode decision engine.
- Multiple independent queues, Smart Queue, Smart Flow (7 modes), weighted shuffle.
- Sleep timer, audiobook chapters, resume points with rollback, playback-marking thresholds.
- Audiophile output negotiation (bit-perfect / DSD native / DoP / resample) — testable core.

**Smart & personalization**
- Smart filters (nestable rule groups), smart playlists, personal mixes (bucket algorithm).
- String templates for Now Playing (fields, conditional blocks, formatting) driven by live state.
- Themes (Material 3 + Material You + light/dark/black), custom themes (JSON, Material Theme Builder
  compatible), 9 app-style presets, profiles (one-tap mode switching).

**Ecosystem**
- Android Auto (`MediaLibraryService` browse tree + voice search).
- Home-screen widget with playback controls.
- Encrypted backup/restore (`.micabkp`: ZIP + AES-256-GCM with PBKDF2, selectable content).
- Broadcast automation API (Tasker-friendly): media control, sync, mixes, provider switching.
- Wear OS configuration, renderer/cast model.

---

## Architecture

```
com.micasong.player
├─ data
│  ├─ db          → Room: entities, DAOs, unified database
│  ├─ model       → domain models (provider-agnostic, nullable fields)
│  ├─ provider    → MediaProvider + capabilities + Local / Subsonic / Jellyfin
│  ├─ repository  → MediaRepository (single source of truth, orchestrates providers ↔ DB)
│  ├─ smart       → filters, smart playlists, mixes, Smart Queue/Flow, weighted shuffle
│  ├─ audio       → equalizer, AutoEQ, ReplayGain, fades, transcode, chapters, output negotiation
│  ├─ backup / theme / tags / sync / cache / radio / …
│  └─ settings    → SettingsRepository (DataStore)
├─ playback
│  ├─ PlaybackService        → MediaLibraryService (UI + notification + Android Auto)
│  ├─ MediaTree              → browsable tree for Auto/Wear
│  ├─ PlaybackConnection     → UI-side MediaController (StateFlow)
│  └─ AudioEffectsController → equalizer applied to the audio session
├─ api            → broadcast automation API
├─ widget         → home-screen widget
└─ ui             → Compose (theme, navigation, home, library, album, artist, search, settings, …)
```

**Stack:** Kotlin · Jetpack Compose (Material 3) · Media3/ExoPlayer · Room · Hilt · Coil ·
DataStore · Navigation Compose · Coroutines/Flow.

---

## Editions (FOSS vs full)

MiCaSong builds in **two product flavors**:

| Flavor | `applicationId` | Contents |
|--------|-----------------|----------|
| **foss** | `com.micasong.player.foss` | 100% free/open-source, no proprietary dependencies |
| **full** | `com.micasong.player`      | adds proprietary components (Google Cast) |

`BuildConfig.IS_FOSS` distinguishes the two at compile time.

## Building

Requirements: JDK 17, Android SDK (platform 35, build-tools 35).

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
export ANDROID_SDK_ROOT="$HOME/Library/Android/sdk"

# Build both debug APKs
./gradlew :app:assembleFossDebug   # → app/build/outputs/apk/foss/debug/app-foss-debug.apk
./gradlew :app:assembleFullDebug   # → app/build/outputs/apk/full/debug/app-full-debug.apk

# Install on a connected device/emulator
./gradlew :app:installFossDebug    # or installFullDebug

# Run the unit tests
./gradlew :app:testFossDebugUnitTest
```

## Continuous integration & releases

`.github/workflows/build.yml` builds **both APKs**, runs the unit tests on every push/PR, and
uploads the APKs as artifacts. Pushing a `v*` tag (e.g. `v0.0.1`) also creates a **GitHub Release**
with both APKs attached for download.

## Tests

A **JVM unit-test suite** (~283 tests, all green) covers the pure-logic engines — smart filters,
playlists, mixes, string templates, LRC lyrics, ReplayGain, Smart Queue/Flow, fade curves, multiple
queues, tag/NFO/M3U-PLS parsers, offline cache, Subsonic/Jellyfin auth, differential + server sync,
AutoEQ, equalizer model, backup crypto, waveform/smart-fades, playback marking, sleep timer,
chapters, Now Playing fields, transcode, renderers, weighted shuffle, media-session actions,
internet radio, app styles/themes/profiles, and audiophile output negotiation.

```bash
./gradlew :app:testFossDebugUnitTest   # → 283 passing, 0 failures
```

## Using the app

Grant the audio-access permission and press **Sync** to scan the device's music and populate the
library. Add remote servers from **Settings → Media providers**. Connect the phone to Android Auto
(or the Desktop Head Unit) and MiCaSong appears with the Home/Recent/Library/Favorites tabs.

Drive playback via the broadcast API (Tasker / adb):

```bash
adb shell am broadcast -a com.micasong.api.MEDIA_COMMAND --es COMMAND play
adb shell am broadcast -a com.micasong.api.MEDIA_COMMAND --es COMMAND next
adb shell am broadcast -a com.micasong.api.MEDIA_SYNC
```

## License

MIT — see [LICENSE](LICENSE).
