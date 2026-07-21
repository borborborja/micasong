# Copilot instructions

Follow the repository's canonical agent guide in [`AGENTS.md`](../AGENTS.md), plus the nested
`AGENTS.md` files under `app/src/` (closest to the edited file wins).

Non-negotiables, in short:

- Verify with `./gradlew :app:testFossDebugUnitTest` (all tests are JVM tests).
- Never import proprietary (Cast/GMS) code from `app/src/main` — it lives in the `full`
  flavor source set only; the `foss` flavor must stay F-Droid clean.
- Go through `MediaRepository` (single source of truth); sync must never clobber local user
  state (favorites, ratings, play counts).
- Room uses destructive migrations: entity changes require a `MiCaSongDatabase` version bump
  and wipe user data on upgrade — flag this loudly in any PR that does it.
