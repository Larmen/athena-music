# Tasks 001 – Music Core

Tick when done. Each task leaves the app building.

## Phase 0 – Setup
- [x] T0.1 Spec Kit initialised (`.specify/`, `.claude/skills`), constitution, spec, plan
- [x] T0.2 Gradle project, version catalog, manifest, theme/fonts copied from Podcasts (FR-060, NFR-001)
- [x] T0.3 CI workflow

## Phase 1 – Server client (FR-001, 021)
- [x] T1.1 `SubsonicAuth` (salt/token) + unit test
- [x] T1.2 `SubsonicClient`: ping, getArtists, getArtist, getAlbum, streamUrl + unit tests (MockWebServer)
- [x] T1.3 `Redact` helper + test (NFR-004)

## Phase 2 – Account & settings (FR-001..004, 061, 062)
- [x] T2.1 Settings + secret store + Account
- [x] T2.2 Launcher appearance bridge client, effective appearance
- [x] T2.3 Sign-in screen, sign-out, settings screen, licences screen

## Phase 3 – Library (FR-010..013)
- [x] T3.1 Artists, Artist, Album screens with error/retry

## Phase 4 – Playback (FR-020..025)
- [x] T4.1 PlaybackService + PlayerController
- [x] T4.2 Player screen + now-playing strip
- [ ] T4.3 Manual QA (`docs/manual-qa.md`): on emulator 2026-11-05 verified sign-in, artists, album, play, next, pause, seek; also verified: media keys, notification, wrong password, offline + Retry; still to do: real Bluetooth/headset hardware, FR-004 (credentials changed on server), mid-stream network loss

## Phase 5 – Done
- [x] T5.1 Build, unit tests, lint green; README updated (verified 2026-11-05 on API 37 emulator: sign-in screen only)

## Phase 6 – Cover art (FR-040..044)
- [x] T6.1 Model: `coverArt` on Album/Track/AlbumDetail; `coverArtUrl`; tests
- [x] T6.2 `ArtworkStore`, `ArtImage` with icon fallback, art-style setting + settings UI
- [x] T6.3 Album rows, album header, player, now-playing strip
- [x] T6.4 Notification/lock-screen art via `athenaart:` URI and `BitmapLoader`
- [ ] T6.5 Verify: DONE on the API 37 emulator against a fake Subsonic server (per-song art, album art, no-art icon in lists/player/strip/notification, grayscale and original); still to do: real Navidrome library on the phone, sign-out clearing the art cache

## Phase 7 – Artist search and scroll memory (FR-014, FR-015)
- [x] T7.1 `filterArtists` (case/accent-insensitive, all words) + unit tests
- [x] T7.2 Search field + Clear on the artist list; query kept in the view model
- [x] T7.3 Per-screen list state kept in the view model; dropped on Back and sign-out
- [x] T7.4 Verified on the API 37 emulator (fake server, 44 artists): search incl. accents, text kept, scroll position restored after Back; real library on phone still to check

## Phase 8 – Playback buffering (FR-030)
- [x] T8.1 `DefaultLoadControl` in `PlaybackService`: min 2 min / max 5 min, start 2.5 s, rebuffer 5 s, byte target raised so lossless files still reach the time target, keep the last 60 s already played (plan §6)
- [ ] T8.2 Verify: DONE on the API 37 emulator against a fake server with 10-minute tracks (buffered position reached about 300 s ahead; ExoPlayer's default is about 50 s); still to do: a real journey through the patchy area, then tune the numbers in `BufferConfig`

## Phase 9 – Saved library (FR-050..056)  — *spec only so far, not implemented*
- [ ] T9.1 `LibraryCache` (JSON envelope, atomic write, 20 MB trim, per server/user keys) + unit tests
- [ ] T9.2 `LibraryRepository` (saved → fresh / offline / failed, 60 s rule, auth passthrough) + unit tests with a fake client
- [ ] T9.3 `LibraryViewModel` on the repository; `Load.Ready.offline`; no "Loading…" when saved data exists
- [ ] T9.4 Screens: offline line under the title, artist-screen Refresh button, in-place update keeps scroll/search
- [ ] T9.5 Settings "Storage": Clear saved library; sign-out deletes it
- [ ] T9.6 Verify: emulator (airplane mode: cold start with saved list, visited vs never-visited pages, removal after refresh) and a real journey

## Phase 10 – Stream quality (FR-070..074)  — *spec only so far, not implemented*
- [ ] T10.1 **Spike on the real server** (plan §8, probes S1–S6, FLAC and VBR MP3): classify the server as case A, B, C or D, pick the output format, and write the decision record into plan §8
- [ ] T10.2 `athenastream:<id>` queue items + `ResolvingDataSource` (fresh token, quality params); `PlayerController` stops minting token URLs
- [ ] T10.3 Pure logic + tests: `chooseQuality`, `streamParams`, `qualityLabel`
- [ ] T10.4 Network class (`NOT_METERED`, `ACCESS_NETWORK_STATE`); per-track decision held for the whole track
- [ ] T10.5 Fallback to Original when the server refuses a converted request
- [ ] T10.6 `Track.suffix/bitRate/contentType`; quality line on the player screen
- [ ] T10.7 Settings "Playback": mobile quality (Original/320/192/128, default 192)
- [ ] T10.8 Seeking per the spike result (only the matching variant is built): A none · B skip-by-reading with a size limit · C `timeOffset` translation in the resolver · D clamp the seek bar to the buffered part + "seeking limited at this quality"; duration from song metadata in all cases
- [ ] T10.9 Verify: emulator (fake server: converted vs original, error fallback, Wi-Fi ↔ mobile, a fake server for each seeking case A–D), real server on Wi-Fi vs mobile data, a real journey
