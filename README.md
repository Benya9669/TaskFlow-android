# TaskFlow Android

Android client for TaskFlow, licensed under [Apache-2.0](LICENSE).

## Current status

The `0.3.0-beta.4` foundation includes a Kotlin + Jetpack Compose shell, a web-aligned TaskFlow design system and adaptive launcher icon, encrypted session storage, Room cache, bounded pull sync, durable task/project outboxes with explicit conflict resolution, task search/sort/detail editing, project management, a drag-and-drop Kanban board with edge auto-scroll and manual ordering, and profile settings.

Open the `android/` directory in Android Studio, allow Gradle to sync, then run the `app` configuration on an Android 8.0+ device or emulator. For terminal builds use the Embedded JDK from Android Studio.

The web and API repository is located next to this repository in `../web`.
Its stable mobile contract is documented in `../web/docs/openapi.json` for `/api/v1`.

## Compatibility baseline

- API: `/api/v1`
- Server release: `v0.3.1`
- SQLite schema: `17`
- Sync: bounded pages using `since`, `cursor`, `limit`, `snapshot`, and `next_cursor`
- Authentication: short-lived bearer access tokens with rotating refresh tokens

Keep this client independent: do not add the web repository as a Git submodule or commit its sources here.

## Android versioning and releases

Android releases are independent from server releases. `versionName` and `versionCode` are defined in `app/build.gradle.kts`.

- `versionName` uses SemVer: `MAJOR.MINOR.PATCH` with optional prerelease suffix, for example `0.3.0-beta.4`.
- `versionCode` increases for every distributable Android build and is never reused.
- A client release declares its minimum compatible server API in this README and the release notes; it does not inherit the server version.
- A release is prepared only after the Android roadmap gate for that version passes. It requires a clean debug/release build, tested migrations, and no keys or user data in the diff.
- Git tags are optional release markers and are created only when explicitly requested.

Build the debug APK with:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :app:assembleDebug
```
