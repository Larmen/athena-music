# Music Constitution

Non-negotiable principles. Specs, plans and code must comply; if one cannot, amend this file first.
Shares its spirit with the Athena Launcher and Athena Podcasts constitutions.

## 1. Calm by default
A quiet, monochrome app: no badges, no recommendations, no ads, no autoplay of "suggested" music, no decorative animation.

## 2. Essentials first
Sign in, browse the library, play music; cover art and artist search are the extras (spec FR-040, FR-014). Everything else (favourites, bookmarks, playlists, equaliser, album/song search, offline downloads, scrobbling, cast) is out of v1 unless a spec says otherwise.

## 2b. Buttons, not gestures
No swipe, pull-to-refresh, long-press or drag. Every action is a visible button on the item it applies to.

## 3. One server, nothing else
The only network peer is the user's own music server (Navidrome / Subsonic API at the address the user enters, e.g. `http://your-server:4533` on the home network). No analytics, no crash-reporting SDKs, no ads, no third-party services.

## 4. Secrets stay secret
The server password is stored app-private, encrypted with an Android Keystore key. Stream and API URLs carry an auth token: never log them, never show them in the UI; log through the redaction helper only.

## 5. Quiet background work
Only the foreground media service while audio plays. No periodic jobs, no wake locks beyond Media3's, no polling.

## 6. Latest two Android versions only
`minSdk` 36 (Android 16), `targetSdk`/`compileSdk` 37 (Android 17). No compatibility shims.

## 7. Modern, boring Android
Kotlin, Jetpack Compose, single activity, unidirectional data flow, Media3, DataStore. Every new dependency needs a one-line justification in `plan.md`.

## 8. Spec first
No feature code without a spec section and tasks. Behaviour changes update `spec.md` in the same commit.

## 9. Own the code, respect licences
Fresh MIT-licensed implementation. Do not copy GPL code (Substreamer is MIT but is used only as a behavioural reference; Navidrome is GPL-3.0 and only its public API is used). Every bundled font/library is listed on the Licences screen.

## 10. One look across Athena apps
Same monochrome tokens, typefaces, text scale and light/dark behaviour as Athena Launcher and Athena Podcasts; appearance follows the launcher (bridge) and falls back to local settings.

## 11. Tested where it hurts
Unit tests for domain logic (Subsonic auth token, URL building, response parsing, queue rules, redaction). Manual checklist for lock-screen/headset/Bluetooth behaviour.
