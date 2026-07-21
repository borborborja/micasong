# Backend providers

Rules for adding or changing a `MediaProvider` connector.

## Adding a provider — checklist

1. Add the value to `ProviderType` in `MediaProvider.kt` (many already exist unimplemented:
   SMB, CLOUD).
2. Create `<Name>Provider.kt` implementing `MediaProvider`, with mapping logic split into
   `<Name>Mappers.kt` (see `SubsonicProvider`/`SubsonicMappers` or `JellyfinProvider` as the
   reference pattern). Auth helpers go in their own file (`SubsonicAuth`, `JellyfinAuth`).
3. Declare an honest `ProviderCapabilities` matrix — `false` for anything the backend can't
   supply. The UI degrades from this; never fake a capability.
4. Generate entity IDs through `StableId` so IDs survive re-syncs and never collide across
   providers. Never use server-side raw IDs or list positions as Room primary keys.
5. Register the constructor in the `when (config.type)` factory in
   `data/repository/MediaRepository.kt`.
6. Hook it into the provider-setup settings UI (`ui/settings/`) so it can be added/tested
   from the app.
7. Tests, both kinds:
   - Pure JVM tests for mappers/auth/parsers under `app/src/test/.../provider/`.
   - A runtime sync test under `app/src/test/.../runtime/` using MockWebServer that fakes
     the server's real JSON and exercises auth → HTTP → parse → Room end to end
     (pattern: `PlexSyncRuntimeTest`, `KodiSyncRuntimeTest`, `SubsonicConnectionRuntimeTest`).

## Invariants

- `sync()` returns a full `ProviderSnapshot`; differential logic lives in `data/sync/`
  (`SyncDiffer` + `ServerSyncMerge`), not in the provider. A provider must never delete or
  overwrite local user state (favorites, ratings, play counts, playlists).
- Streaming-URL resolution is separate from `sync()` so playback honors per-connection
  bitrate/transcode settings.
- Use `ServerUrl` for URL normalization (trailing slashes, scheme, ports) instead of string
  concatenation.
- Providers are plain classes built from `ProviderConfig` (no Android/Hilt dependencies) —
  that's what makes them testable on the JVM. Keep them that way.
- Connection errors must surface as clear messages (see `SubsonicConnectionRuntimeTest`),
  never silent failures.
