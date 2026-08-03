# TaskFlow Android

Android client for TaskFlow.

## Current status

The `0.3.0` beta foundation is in place: a Kotlin + Jetpack Compose app shell and the shared TaskFlow visual system. The next increment adds authentication and a durable local task store.

Open the `android/` directory in Android Studio, allow Gradle to sync, then run the `app` configuration on an Android 8.0+ device or emulator. The Gradle wrapper will be added with the first reproducible Android build environment; it is not generated locally because Gradle is not installed in this workspace.

The web and API repository is located next to this repository in `../web`.
Its stable mobile contract is documented in `../web/docs/openapi.json` for `/api/v1`.

## Compatibility baseline

- API: `/api/v1`
- Server release: `v0.2.0`
- SQLite schema: `17`
- Sync: bounded pages using `since`, `cursor`, `limit`, `snapshot`, and `next_cursor`
- Authentication: short-lived bearer access tokens with rotating refresh tokens

Keep this client independent: do not add the web repository as a Git submodule or commit its sources here.
