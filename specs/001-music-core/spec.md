# Spec 001 – Music Core

Status: Draft · Owner: Anders · Date: 2026-11-05

## 1. Summary
A minimal Android music player that **streams** from the user's own Navidrome server (Subsonic/OpenSubsonic API) on the home network, replacing Substreamer. It signs in, lets the user browse Artists → Albums → Tracks, and plays with play/pause, previous/next and seeking. The look (monochrome, typeface, text size, light/dark) matches Athena Launcher and Athena Podcasts and follows the launcher's settings.

## 2. Goals / Non-goals
**Goals**
- Replace Substreamer for everyday listening, with the least that works.
- Same look and appearance configuration as the other Athena apps.

**Non-goals (v1)**
- An audio disk cache and automatic reconnect logic: considered after a patchy-coverage train trip and deliberately **not** specified now; the first steps are a longer buffer (FR-030) and a lower quality on mobile data (FR-070..074). Revisit only if stalls persist.
- A separate Wi-Fi quality setting (Wi-Fi is always Original), choosing the transcoding format (the server decides), per-track or per-album quality, cellular-data counters.
- Favourites/stars, bookmarks, ratings, scrobbling, playlists, internet radio, podcasts.
- Equaliser, gapless/crossfade settings, ReplayGain settings.
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

### US6 – Fewer stalls on a patchy connection (P1)
Riding through areas with weak coverage, the music should keep playing instead of pausing for a few seconds while it waits for data.
- Acceptance: while the connection is good the player fills a buffer of a couple of minutes; when coverage drops for a short while, playback continues from the buffer without a pause.

### US7 – Browse without a connection (P1)
When the connection is gone, I can still open the artists, albums and tracks I have looked at before. Lists show at once, even on a good connection, and quietly update themselves.
- Acceptance: open the app in airplane mode: the artist list I saw last time is there immediately, with the search and my scroll position working; open an artist and an album I have visited: both show (playing needs the connection); the only hint is a quiet "Offline – showing saved list" line. Pages I have never opened say "Could not reach the server" with Retry, as today.

### US8 – Fewer stalls on mobile data (P1)
On mobile data the music should keep playing in weak-signal areas, even if that costs some sound quality. At home on Wi-Fi it is always the original file.
- Acceptance: on mobile data with the default 192 kbps, a FLAC album uses roughly a fifth of the data of the original and keeps playing where the original stalls (128 kbps for weaker coverage, 320 kbps or Original for better sound); the same album on Wi-Fi is the original; moving between the two switches quality at the next track without stopping the music.

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
- FR-021 Streams use `stream`; the original file is requested on an unmetered network, and the quality chosen in FR-070 on a metered one (FR-071, FR-072). Credentials go by salted token (`u`,`t`,`s`), never the plain password (see plan §3.2).
- FR-022 Foreground media service with notification/lock-screen controls: play/pause, previous, next, seek. Tapping the notification or lock-screen widget opens the app on the player screen.
- FR-023 Pause on headphone unplug and on audio-focus loss; resume rules as Media3 defaults.
- FR-024 Player screen: title, artist, album, position/duration, seek bar, Previous, Play/Pause, Next. A now-playing strip is shown under the library screens while something is loaded.
- FR-025 The queue is not persisted across app restarts in v1.

**Stream quality** (US8; extends FR-021)
- FR-070 **Setting.** "Stream quality on mobile data": Original, 320, 192 or 128 kbps; default **192 kbps**. There is no setting for Wi-Fi: on an unmetered network the original is always played. Lives under a "Playback" heading in Settings.
- FR-071 **Decided per track.** When a track's stream is first opened the app looks at the default network: unmetered (Wi-Fi, ethernet; `NET_CAPABILITY_NOT_METERED`) → Original; anything else, including a metered Wi-Fi such as a phone hotspot, or an unknown network → the FR-070 setting. The decision then holds for the whole track (also if the stream is re-opened by a seek). The next track decides afresh, so a queue started at home switches to mobile quality once you leave the Wi-Fi, and back, without interrupting the song that is playing.
- FR-072 **Request.** For a lower quality the app sends `maxBitRate=<N>` and `estimateContentLength=true` and leaves the output format to the server. The server only converts files above N, so low-bitrate MP3s are sent as they are. "Original" sends neither. If the server refuses the converted request (an error response, not a network failure) the same track is requested once as Original, silently.
- FR-073 **Seeking** works in converted tracks as in original ones (NFR-008).
- FR-074 **Quiet quality line.** The player screen shows one muted line under the album: the source (`FLAC`, `MP3 · 128 kbps`, …) and what is being played, e.g. "FLAC → 192 kbps · mobile data", "MP3 · 128 kbps", "FLAC · original · Wi-Fi". It uses the format and bitrate the server reports for the song; parts it does not know are left out.

**Saved library** (US7)
- FR-050 **Show saved first.** The artist list, each artist's albums and each album's tracks are kept on disk after they have been loaded. Opening any of these screens shows the saved copy at once (no "Loading…"); only a screen never opened before shows "Loading…".
- FR-051 **Quiet refresh.** Each time a screen opens, the app asks the server for a fresh copy in the background, unless the saved copy is under 60 seconds old. When it arrives the list is replaced in place: items keep their identity (no jump), the scroll position (FR-015) and search text (FR-014) stay, and the saved copy is overwritten. Something removed on the server disappears at this point.
- FR-052 **Offline.** If the refresh fails with a network or server error, the saved copy stays on screen and a plain line "Offline – showing saved list" appears under the title (not an error screen, no Retry needed; the next open or the Refresh button tries again). If there is nothing saved, FR-013 applies unchanged (message and Retry). A refused login still goes to sign-in (FR-004), even when a saved copy exists.
- FR-053 **Refresh button.** A plain "Refresh" button next to Settings on the artist screen fetches the artist list again now (for a library that changed while the app was open). No pull-to-refresh (constitution §2b).
- FR-054 **What is saved, and for whom.** Only what the server returned for those three requests (names, ids, counts, cover ids; never a password, token or URL), under the signed-in server and user. Another account never sees it. Sign-out deletes it. Size is capped (20 MB; oldest album/artist pages go first), unreadable or old-format files are ignored and replaced. Excluded from backup.
- FR-055 **Settings.** A "Storage" heading in Settings with a plain "Clear saved library" button.
- FR-056 **Playing needs the connection.** Browsing saved lists works offline; starting playback offline fails exactly as it does today (no audio is stored). Stream and cover URLs are built when needed, never saved. Covers already on the phone show offline; those not yet fetched show the Athena Music icon and are fetched when the connection is back.

**Playback buffering** (US6)
- FR-030 **Buffer ahead.** While the network is up the player buffers well ahead of the playback position: target at least 2 minutes, at most 5. The last 60 seconds already played are kept, so a short rewind needs no new request. Playback starts after about 2.5 s of audio and, after a stall, resumes once about 5 s is buffered. Nothing else about playback changes (no audio disk cache, no extra reconnect logic, no quality switching: see Non-goals). To be judged on a real journey; the numbers are meant to be tuned.

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
- FR-064 Settings screen: Server (address, sign out), Playback (FR-070), Storage (FR-055), Appearance (follow launcher, theme, typeface, text size, cover art style), About (Open-source licences).
- FR-066 Licences screen listing the app, bundled fonts and libraries.

## 5. Non-functional requirements
- NFR-001 Android 16 (API 36) and 17 (API 37) only.
- NFR-002 Network: only the configured server. Cleartext http is allowed because the home server is plain http on the LAN; the sign-in screen warns once for http addresses that are not private-network addresses.
- NFR-003 Android 17 local-network protection: the app declares `ACCESS_LOCAL_NETWORK` and asks for it just in time at sign-in.
- NFR-004 Secrets never appear in logs, errors or UI (constitution §4).
- NFR-005 Start to first sound under 3 s on the home Wi-Fi (server-bound).
- NFR-006 Accessibility: all controls have content descriptions; works at 140 % text.
- NFR-008 **Seeking in a converted track.** A seek into the buffered or kept-behind part is instant. Beyond it the stream is re-requested at that position and playback resumes within about 3 s of the target (on a decent connection). How this is done depends on what the server supports (plan §8, cases A–D, settled by the spike T10.1). If the server cannot seek in converted streams at all, seeking is limited to the buffered part: the seek bar shows how far it can go, and the quality line says "seeking limited at this quality"; choosing Original in Settings lifts the limit. No dialogs, and no silent switch to the original on mobile data.
- NFR-009 Quality changes between tracks never interrupt playback.
- NFR-007 A saved artist list of 5,000 artists is on screen within 0.5 s of the app starting, before any network answer; reading and parsing happen off the main thread.

## 6. Data
- Settings (DataStore): serverUrl, username, passwordRef, themeMode, typeface, textScalePercent, followLauncher.
- Settings (DataStore): also mobileStreamKbps (0 = original; default 192).
- Secret store: password.
- Saved library: `filesDir/library-cache/` (JSON, one file per request), capped at 20 MB (FR-054); excluded from backup. Nothing else persisted.

## 7. Open questions
- [NEEDS CLARIFICATION] Account credentials: entered by the user at first run (no credentials are stored in the repo).
- The sign-in address is never prefilled: the field shows the placeholder `http://your-server:4533`. The user's real address stays out of the repository.
- Album/song search and offline downloads are the natural next specs.
