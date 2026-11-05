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
