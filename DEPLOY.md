# DEPLOY.md — releasing MiCaSong

How a release goes from this repo to installable APKs on GitHub. The whole pipeline is
`.github/workflows/build.yml`; there is nothing to deploy to a server — a "deploy" here means
publishing a GitHub Release with the two APKs attached.

## TL;DR

```bash
# 1. Bump the version in app/build.gradle.kts (versionCode +1, versionName x.y.z)
# 2. Commit everything and push main
git add -A && git commit -m "Release vX.Y.Z: <summary>"
git push origin main

# 3. Tag and push the tag — this is what triggers the release
git tag vX.Y.Z
git push origin vX.Y.Z
```

Minutes later the release appears at
<https://github.com/borborborja/micasong/releases> with `MiCaSong-foss.apk` and
`MiCaSong-full.apk` attached.

## What CI does on a `v*` tag

1. Sets up JDK 17 + Android SDK (platform 35, build-tools 35).
2. Installs the shared signing key from the `SIGNING_KEYSTORE_B64` secret (base64 of
   `micasong.keystore`). With it, every release is signed identically, so users upgrade
   in place without uninstalling. Without the secret, CI falls back to a debug key —
   builds still work, but signatures won't match previous releases.
3. Runs the unit-test suite (`testFossDebugUnitTest`). A red suite kills the release.
4. Builds both flavors (`assembleFossDebug` + `assembleFullDebug`) and renames them to
   `MiCaSong-foss.apk` / `MiCaSong-full.apk`.
5. Publishes a GitHub Release for the tag with both APKs and auto-generated notes.

Pushes to `main` and PRs run the same test/build steps and upload the APKs as workflow
artifacts — only tags create a Release.

## Version bump checklist

In `app/build.gradle.kts` → `defaultConfig`:

- `versionCode`: +1 on every release (integer, monotonic — the in-app updater and Android
  both compare it).
- `versionName`: human version `x.y.z`, must match the tag (tag `v0.0.16` ↔ name `0.0.16`).
  The in-app updater compares the release tag against `BuildConfig.VERSION_NAME`, so a
  mismatched tag/name breaks update detection.

## The two APKs

| APK | Flavor | Contents |
|---|---|---|
| `MiCaSong-foss.apk` | foss | 100% FOSS, no Google dependencies (F-Droid clean) |
| `MiCaSong-full.apk` | full | adds Google Cast |

Both are `debug`-type builds signed with the shared key (release-type minification is
disabled), installable side by side (`.foss` application-id suffix).

## In-app updates

The app checks `https://api.github.com/repos/borborborja/micasong/releases/latest`
(`UpdateManager`), compares the tag with the running version, downloads the asset matching
its flavor (exact names `MiCaSong-foss.apk` / `MiCaSong-full.apk` — don't rename them in
the workflow without updating `UpdateManager`), and hands it to the system installer.

## Local release build (optional)

```bash
./gradlew :app:testFossDebugUnitTest        # must be green
./gradlew :app:assembleFossDebug :app:assembleFullDebug
# → app/build/outputs/apk/{foss,full}/debug/
```

Place `micasong.keystore` in `app/` (or set `SIGNING_KEYSTORE_FILE` + `SIGNING_STORE_PASSWORD`
/ `SIGNING_KEY_ALIAS` / `SIGNING_KEY_PASSWORD`) to sign with the shared key locally. Never
commit the keystore.

## If a release goes wrong

- **CI failed on tests/build**: fix, push to `main`, then `git tag -f vX.Y.Z && git push -f origin vX.Y.Z`
  (or better: delete the tag and release on GitHub and cut vX.Y.Z+1).
- **Wrong signature (forgot the secret)**: users can't upgrade in place. Re-run with the
  keystore secret configured and republish; affected users must uninstall once.
- **Release published but updater doesn't see it**: check the tag is `v`-prefixed and the
  asset names are exactly `MiCaSong-foss.apk` / `MiCaSong-full.apk`.
