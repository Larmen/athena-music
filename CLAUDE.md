# Athena Music

A minimalist, monochrome Android music player (Kotlin + Jetpack Compose + Media3) that streams from the user's own Navidrome/Subsonic server. Sibling of Athena Launcher (`../athena-launcher`) and Athena Podcasts (`../athena-podcasts`). Replaces Substreamer.

## Spec-driven workflow (GitHub Spec Kit)
Spec Kit is installed (`.specify/`, skills in `.claude/skills`: `/speckit-specify`, `/speckit-plan`, `/speckit-tasks`, `/speckit-implement`, `/speckit-clarify`, `/speckit-analyze`). Read in this order before changing anything:
1. `.specify/memory/constitution.md` – principles that always apply
2. `specs/001-music-core/spec.md` – what and why
3. `specs/001-music-core/plan.md` – how
4. `specs/001-music-core/tasks.md` – ordered work; tick items when done

Rules: no behaviour that is not in `spec.md` (update the spec first); requirement IDs (FR-/NFR-) stay stable; mark unknowns `[NEEDS CLARIFICATION]`; new features get their own `specs/00N-…` folder.

## Targets
- minSdk 36, target/compile 37. Kotlin, Compose, single activity, Media3, DataStore. Package / application id `se.anders.athenamusic`. MIT.
- Server: Navidrome 0.62 at `http://192.168.1.180:4533` (Subsonic API 1.16.1). Credentials are entered at first run and never committed.
- Auth tokens in API/stream URLs are secrets: never log them; use `redact()`.
- v1 scope is deliberately tiny: sign in, browse Artists → Albums → Tracks, play. No favourites, bookmarks, EQ, search, downloads, cover art.
- Look and feel mirrors Athena Launcher/Podcasts (`ui/theme/Theme.kt` copied; do not diverge without updating the spec).

## Build
```
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew assembleDebug testDebugUnitTest lintDebug
```
Install debug builds signed with the shared `~/.android/debug.keystore` so the launcher appearance bridge works. Android 17 needs `adb shell pm grant se.anders.athenamusic android.permission.ACCESS_LOCAL_NETWORK` if the in-app prompt is skipped.
