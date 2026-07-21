# Tests

All tests run on the JVM — no emulator, no instrumented tests.

```bash
./gradlew :app:testFossDebugUnitTest                                      # everything (CI gate)
./gradlew :app:testFossDebugUnitTest --tests "com.micasong.player.smart.*" # one package
```

## Conventions

- Test packages mirror the feature packages (`data/smart/` → `test/.../smart/`). One test
  class per engine/parser.
- Two kinds of tests:
  - **Pure JVM** — plain JUnit 4 for the Android-free engines. No Robolectric, no mocks of
    your own code; build real inputs (see `TestTracks.kt` for shared track fixtures).
  - **Runtime** (`runtime/` package) — real Android components on the JVM via Robolectric:
    annotate `@RunWith(RobolectricTestRunner::class)` + `@Config(sdk = [34])`. Used for
    Room/SQLite, MediaStore (shadow ContentResolver), and end-to-end provider sync against
    a `MockWebServer` that fakes the server's JSON.
- Kotlin backtick test names describing behavior: `` fun `ok status passes`() ``.
- Coroutines: `runBlocking` (or `kotlinx-coroutines-test`) — keep tests synchronous and
  deterministic.
- JSON in tests uses the real `org.json` artifact (the android.jar stub throws) — already
  wired in `build.gradle.kts`.
- Don't weaken or delete a failing test to make it pass; fix the code or discuss the change
  with the user.
