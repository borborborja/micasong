# AGENTS.md — MiCaSong

Guidance for coding agents (Claude Code, opencode, Copilot, Codex, Cursor, …).
This file is canonical; `CLAUDE.md` just imports it. Nested `AGENTS.md` files
(closest one wins) add area-specific rules:

- `app/src/main/java/com/micasong/player/data/provider/AGENTS.md` — adding/changing backend providers
- `app/src/test/AGENTS.md` — test conventions

## Project

MiCaSong is an offline-first, multi-provider music & audiobook player for Android and
Android Auto. Single Gradle module (`:app`).

**Stack:** Kotlin 2.0.21 · Jetpack Compose (BOM 2024.12, Material 3) · Media3/ExoPlayer 1.5.1 ·
Room 2.6.1 (KSP) · Hilt 2.52 · DataStore · Coroutines/Flow · Coil. AGP 8.7.3, JDK 17,
compile/target SDK 35, min SDK 26. Dependencies go through `gradle/libs.versions.toml`
(version catalog) — don't hardcode versions in `build.gradle.kts` unless flavor-scoped.

## Commands

```bash
./gradlew :app:testFossDebugUnitTest                 # full JVM test suite (~300+ tests) — the CI gate
./gradlew :app:testFossDebugUnitTest --tests "com.micasong.player.smart.FilterEngineTest"   # one class
./gradlew :app:assembleFossDebug                     # FOSS APK  → app/build/outputs/apk/foss/debug/
./gradlew :app:assembleFullDebug                     # Full APK (Google Cast)
./gradlew :app:installFossDebug                      # install on connected device/emulator
./gradlew :app:compileFossDebugKotlin                # fast compile check without building an APK
```

All tests run on the JVM — no emulator or instrumented tests exist. **Definition of done:
`testFossDebugUnitTest` passes.** If you touched flavor-specific code or dependencies, also
compile the *other* flavor (`compileFullDebugKotlin` / `compileFossDebugKotlin`).

Drive the app from adb via the broadcast API (broadcasts must target the package, `-p`):

```bash
adb shell am broadcast -p com.micasong.player -a com.micasong.api.MEDIA_COMMAND --es COMMAND play
```

## Architecture

Everything lives under `com.micasong.player` in `app/src/main/java/`:

- `data/provider/` — backend connectors. `MediaProvider` interface + per-provider
  `ProviderCapabilities` matrix (UI degrades from it). Implemented: Local (MediaStore),
  Subsonic, Jellyfin, Plex, Emby, Kodi, AudioBookShelf, WebDAV.
- `data/repository/MediaRepository.kt` — **single source of truth** (Hilt `@Singleton`).
  Owns providers, drives sync into Room, exposes the catalog as domain `Flow`s.
  UI and playback talk to it — never to providers or DAOs directly.
- `data/sync/` — differential sync (`SyncDiffer`, `ServerSyncMerge`). Server syncs must
  never clobber local user state (favorites, ratings, play counts).
- `data/db/` — Room entities/DAOs (`MiCaSongDatabase`). Domain models in `data/model/`
  are provider-agnostic with nullable fields; map via `toEntity`/`toDomain`.
- `data/*` engines (smart filters/playlists/mixes, audio: equalizer/AutoEQ/ReplayGain/
  fades/transcode/output-negotiation, templates, tags, backup, queue, lyrics, radio, …) —
  **pure logic, deliberately Android-free**, each with a matching JVM test class.
- `playback/` — `PlaybackService` (Media3 `MediaLibraryService`: notification, session,
  Android Auto), `MediaTree` (browse tree for Auto/Wear), `PlaybackConnection` (UI-side
  `MediaController` wrapper exposing `StateFlow`s), `AudioEffectsController`.
- `ui/` — Compose screens per feature; `widget/` home-screen widget; `api/` broadcast
  automation receiver; `di/` Hilt modules.

Code comments reference a design spec by section (`spec §9`, `§44`). Keep those references
when editing nearby code and use them to understand intent.

## Flavors: foss vs full

The `distribution` flavor dimension isolates proprietary code:

- **foss** (`.foss` suffix, `BuildConfig.IS_FOSS = true`) — F-Droid clean, zero proprietary deps.
- **full** — adds Google Cast via `"fullImplementation"` dependencies only.

Cast is abstracted with flavor source sets: interface `CastSessionManager` in `src/main`,
`NoopCastSessionManager` in `src/foss`, `RealCastSessionManager` in `src/full`, each bound by
its flavor's `di/CastModule.kt` (plus per-flavor `ui/CastButton.kt`). Follow this pattern for
anything proprietary. **Never import Cast/GMS/proprietary classes from `src/main`.**

## Code style

- Match the existing style: KDoc on public classes explaining the *why* (often with a
  `spec §N` reference), minimal inline comments, expressive Kotlin (data classes, sealed
  types, extension mappers, `Flow`).
- New business logic goes in an Android-free engine class under `data/` + a JVM test —
  not inside a Composable, ViewModel, or Service.
- Compiler flags in `app/build.gradle.kts` are load-bearing: `-Xjvm-default=all` (Room
  `@Transaction` default methods in DAO interfaces) and module-wide opt-in to Media3's
  `@UnstableApi`. Don't remove them.
- UI strings visible to users are in Spanish; code, comments, and commits are in English.

## Git workflow

- Work on branches, not `main`. Never push, and never run destructive git commands,
  unless explicitly asked.
- Commits: short imperative summary in English (`Fix: …`, `Add …`, optionally the spec
  section, e.g. `C2: configurable Android Auto tabs (§38)`).
- Releases: bump `versionCode`/`versionName` in `app/build.gradle.kts`, tag `v*` — CI
  (`.github/workflows/build.yml`) tests, builds both APKs, and publishes a GitHub Release.
- Signing: with `micasong.keystore` (or `SIGNING_*` env vars) present, builds are signed
  with the shared key; otherwise the default debug key. Never commit a keystore.

## Boundaries

✅ **Always**
- Run `./gradlew :app:testFossDebugUnitTest` before considering any change done.
- Add/extend a JVM test when you touch an engine, parser, provider, or sync logic.
- Preserve local user state in any sync/merge path (`ServerSyncMerge` is the pattern).
- Use the version catalog for any new dependency; FOSS-incompatible deps must be
  `"fullImplementation"`.

⚠️ **Ask first / flag loudly**
- Room schema changes: `MiCaSongDatabase` uses `fallbackToDestructiveMigration()`, so a
  `version` bump **wipes the user's entire library DB** (favorites, play counts, playlists)
  on upgrade. Bump the version if you change entities, and say so in your summary.
- Changing `MediaProvider`/`ProviderCapabilities` signatures (fans out to all 8 providers).
- Anything touching signing, `applicationId`, permissions in the manifests, or the CI workflow.
- Adding dependencies to the shared (`implementation`) scope.

🚫 **Never**
- Import proprietary (Cast/GMS) code from `src/main` or add proprietary deps to the foss flavor.
- Bypass `MediaRepository` (UI/playback reaching into DAOs or providers directly).
- Put business logic in Composables/Services where it can't be JVM-tested.
- Commit secrets, keystores, or server credentials; log passwords/tokens.
- `git push`, force-push, or history rewrites without an explicit request.
