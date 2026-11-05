# Spec 001 – Music Core

Status: Draft · Owner: Anders · Date: 2026-11-05

## 1. Summary
A minimal Android music player that **streams** from the user's own Navidrome server (Subsonic/OpenSubsonic API) on the home network, replacing Substreamer. It signs in, lets the user browse Artists → Albums → Tracks, and plays with play/pause, previous/next and seeking. The look (monochrome, typeface, text size, light/dark) matches Athena Launcher and Athena Podcasts and follows the launcher's settings.

## 2. Goals / Non-goals
**Goals**
- Replace Substreamer for everyday listening, with the least that works.
- Same look and appearance configuration as the other Athena apps.

**Non-goals (v1)**
- Favourites/stars, bookmarks, ratings, scrobbling, playlists, internet radio, podcasts.
- Equaliser, gapless/crossfade settings, ReplayGain settings, transcoding settings.
- Searching albums or songs, offline downloads of music, Android Auto, cast, widgets.
- Multiple servers or accounts. Android 15 or older. Play Store distribution.

## 3. User stories
### US1 – Sign in (P1)
I enter server address, username and password once; the app checks them and remembers them (password encrypted).
- Acceptance: wrong credentials or unreachable server → clear message, nothing saved. Sign out removes everything stored.

### US2 – Browse (P1)
I see artists A–Z, open an artist to see albums, open an album to see its tracks in disc/track order.

### US3 – Play (P1)
I tap a track (or "Play album") and music streams. Controls: play/pause, previous, next, seek bar. The album continues to the next track automatically.
- Acceptance: audio continues with the screen off; controls work from notification, lock screen, headset and Bluetooth.

### US5 – Cover art (P2)
I see the album's cover in album lists, and the song's own cover on the now-playing screen, strip and lock screen. Some albums have a different picture per song. Where the server has no picture, the Athena Music icon is shown instead.

### US4 – Appearance like the launcher (P2)
Theme, typeface and text size follow Athena Launcher; local settings apply when the launcher is absent.

## 4. Functional requirements
**Account**
- FR-001 Sign-in screen: server address (http or https; a bare host:port gets `http://`), username, password. Validated with Subsonic `ping`.
- FR-002 Password stored in the encrypted secret store; address and username in DataStore. Shown masked, never logged.
- FR-003 Sign out (Settings) clears address, username and password and returns to sign-in.
- FR-004 If the server later refuses the credentials, the app returns to sign-in with a message.

**Library**
- FR-010 Artists list (`getArtists`), alphabetical, as returned by the server.
- FR-011 Artist screen: albums (`getArtist`), with year and track count.
- FR-012 Album screen: tracks (`getAlbum`) ordered by disc then track number; shows number, title, artist if it differs from the album artist, duration.
- FR-014 **Artist search.** A search field above the artist list filters it as you type: case- and accent-insensitive, every word typed must occur in the name ("arc fire" finds "The Arcade Fire"; "bjork" finds "Björk"). A plain "Clear" button empties it; a note says when nothing matches. The filter is on the already loaded list (no extra server call) and applies to artists only; searching albums and songs is a later spec. The text is kept while you open an artist and come back.
- FR-015 **Scroll position.** Going back to a list (artists, an artist's albums, an album's tracks) shows it exactly where it was left, with the same search text. A screen you left with Back starts at the top the next time it is opened. Sign-out forgets all positions.
- FR-013 Errors (offline, server down) show a plain message and a Retry button. Lists are loaded on demand and held in memory only.

**Playback**
- FR-020 Tapping a track queues the album from that track and plays; "Play album" starts at track 1.
- FR-021 Streams use `stream` with the original format (no transcoding parameters); credentials by salted token (`u`,`t`,`s`), never the plain password (see plan §3.2).
- FR-022 Foreground media service with notification/lock-screen controls: play/pause, previous, next, seek. Tapping the notification or lock-screen widget opens the app on the player screen.
- FR-023 Pause on headphone unplug and on audio-focus loss; resume rules as Media3 defaults.
- FR-024 Player screen: title, artist, album, position/duration, seek bar, Previous, Play/Pause, Next. A now-playing strip is shown under the library screens while something is loaded.
- FR-025 The queue is not persisted across app restarts in v1.

**Cover art** (modelled on Athena Podcasts FR-040..043)
- FR-040 Album rows (artist screen) and the album screen header show the album's cover (`coverArt` of the album). The player screen, now-playing strip, notification and lock screen show the **song's** cover (`coverArt` of the song; falls back to the album's when the song has none), so albums with a different picture per song show the right one for each track.
- FR-041 No cover on the server, or it cannot be fetched → the Athena Music icon (the launcher-icon ring and note on black) is drawn instead, at the same size. Never an empty gap.
- FR-042 Art style setting (default **Grayscale**: colour removed, contrast reduced, same matrix as Podcasts; or **Original**). The fallback icon is unaffected. Applies live, also to the notification art on the next track.
- FR-043 Pictures are fetched once (`getCoverArt`, 800 px) and kept in app-private storage, decoded at the size drawn, with a small in-memory cache; max 5 MB each. At most four downloads run at once. They are removed on sign-out. Their URLs carry a token and are never logged.
- FR-044 Playback metadata carries the art as an `athenaart:<coverArtId>` URI that the service resolves through the same store, so notification and lock-screen art follows FR-041/042.

**Appearance & settings**
- FR-060 Style: monochrome, same colour tokens as `athena-launcher`/`athena-podcasts` `Theme.kt`; no dynamic colour.
- FR-061 Theme (system/light/dark), typeface (same twelve), text size 80–140 %.
- FR-062 **Follow launcher** (default on): read the launcher's appearance bridge (`content://se.anders.athenalauncher.appearance/appearance`, signature permission); fall back to local values field by field. The last values read are cached locally so a cold start looks right before the launcher answers (it can take several seconds to wake); the cache is dropped only when the launcher is known to be missing or to refuse access, and a slow or empty answer never changes the look.
- FR-064 Settings screen: Server (address, sign out), Appearance (follow launcher, theme, typeface, text size, cover art style), About (Open-source licences).
- FR-066 Licences screen listing the app, bundled fonts and libraries.

## 5. Non-functional requirements
- NFR-001 Android 16 (API 36) and 17 (API 37) only.
- NFR-002 Network: only the configured server. Cleartext http is allowed because the home server is plain http on the LAN; the sign-in screen warns once for http addresses that are not private-network addresses.
- NFR-003 Android 17 local-network protection: the app declares `ACCESS_LOCAL_NETWORK` and asks for it just in time at sign-in.
- NFR-004 Secrets never appear in logs, errors or UI (constitution §4).
- NFR-005 Start to first sound under 3 s on the home Wi-Fi (server-bound).
- NFR-006 Accessibility: all controls have content descriptions; works at 140 % text.

## 6. Data
- Settings (DataStore): serverUrl, username, passwordRef, themeMode, typeface, textScalePercent, followLauncher.
- Secret store: password. Nothing else persisted.

## 7. Open questions
- [NEEDS CLARIFICATION] Account credentials: entered by the user at first run (no credentials are stored in the repo).
- Should the sign-in default the address to `http://192.168.1.180:4533`? Currently yes (prefilled, editable).
- Album/song search and offline downloads are the natural next specs.
