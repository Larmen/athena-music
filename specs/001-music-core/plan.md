# Plan 001 – Music Core

Implements `spec.md`. Constrained by `.specify/memory/constitution.md`.

## 1. Stack
| Concern | Choice | Why |
|---|---|---|
| Language/UI | Kotlin, Jetpack Compose (Material 3), single activity | Constitution §7; theme code shared with sibling apps |
| Build | Gradle KTS, version catalog, AGP 9 (built-in Kotlin) | Versions copied from `athena-podcasts/gradle/libs.versions.toml` |
| SDK | minSdk 36, target/compile 37 | Constitution §6 |
| Playback | Media3 ExoPlayer + `MediaSessionService` (+ `media3-datasource-okhttp`) | Notification, lock screen, Bluetooth, audio focus for free; OkHttp data source shares one client |
| HTTP/JSON | OkHttp + kotlinx-serialization-json | Small, already used in sibling apps |
| Settings | DataStore Preferences | As siblings |
| Secrets | Android Keystore AES-GCM wrapper (copied from Podcasts `SecretStore`) | No deprecated security-crypto |
| State | ViewModel + StateFlow, manual `AppContainer` | As siblings |
| Tests | JUnit 4 + MockWebServer | |

## 2. Layout (`se.anders.athenamusic`)
```
MainActivity, AthenaMusicApplication
core/       AppContainer
data/
  server/   SubsonicClient (OkHttp), SubsonicAuth (salt+token), models, Redact
  settings/ SettingsRepository, Account
  secrets/  SecretStore
  launcher/ LauncherAppearanceSource
  licences/ Licences
domain/     EffectiveAppearance, Queue helpers
playback/   PlaybackService, PlayerController
ui/         MainViewModel, LibraryViewModel, PlayerViewModel
  signin/ library/ player/ settings/ licences/ theme/
```

## 3. Key decisions
### 3.1 Server
Navidrome 0.62.0 on the home network, port 4533 (probed 2026-11-05: `/rest/ping` answers `openSubsonic: true`; the server host also offers https via a reverse proxy — not used in v1). API version sent: `v=1.16.1`, `c=athena-music`, `f=json`.

### 3.2 Auth
Subsonic token auth: per request `s` = random 8-byte hex salt, `t` = md5(password + salt). The password never leaves the device in clear text. Stream URLs are built at play time, one salt per media item; they hold a token and are secrets (redacted in logs). MD5 is mandated by the protocol; it is not used for anything else.

### 3.3 Endpoints (stream parameters: see §8)
`ping`, `getArtists`, `getArtist`, `getAlbum`, `stream`, `getCoverArt`. Nothing else in v1.

### 3.4 Playback
`PlaybackService : MediaSessionService` with `ExoPlayer`, `AudioAttributes(USAGE_MEDIA, CONTENT_TYPE_MUSIC)`, audio focus on, `setHandleAudioBecomingNoisy(true)`, OkHttp data source. UI uses a `MediaController`; `PlayerController` maps to `StateFlow<PlayerState>`. The queue is the album's tracks (`MediaItem` with `mediaId` = track id and metadata title/artist/album), URIs resolved by the controller before `setMediaItems`. Stream URLs are minted by the controller at play time, in the UI process, using the stored password.

### 3.5 Local network (Android 17)
Cleartext to a private LAN address needs `ACCESS_LOCAL_NETWORK` (runtime, requested at sign-in) and `network_security_config` permitting cleartext.

### 3.6 Appearance
Copied from Podcasts: `Theme.kt`, fonts, `LauncherAppearanceSource`, `effectiveAppearance`, same Settings names. Bridge needs both apps signed with the same key (debug keystore on this Mac).

### 3.7 Substreamer
Behavioural reference only (no code copied).

## 6. Playback buffering (FR-030)
One change in `PlaybackService`: build the `ExoPlayer` with
`DefaultLoadControl.Builder().setBufferDurationsMs(minBufferMs = 120_000, maxBufferMs = 300_000, bufferForPlaybackMs = 2_500, bufferForPlaybackAfterRebufferMs = 5_000).setBackBuffer(60_000, true).build()`.
The last minute already played is kept (rewinds are the commonest seek and, for converted streams, anything outside the kept part costs a new request: §8). Compressed audio keeps memory small (5 minutes of 256 kbit/s is about 10 MB; a lossless FLAC at about 1 Mbit/s is about 37 MB). `DefaultLoadControl` also limits by bytes (`setTargetBufferBytes`, default about 13 MB for audio), so for lossless files the byte limit would cap the buffer well below 2 minutes: set `setTargetBufferBytes(48 * 1024 * 1024)` explicitly and `setPrioritizeTimeOverSizeThresholds(true)` so the time targets win. No new dependency, no new UI. Tuning is done on a real journey; if stalls persist the deferred ideas (an audio disk cache, reconnect logic, mobile-data quality) are in the Non-goals and the git history of this spec.

## 7. Saved library (FR-050..056)
No database: the data is a few small trees, read whole, so JSON files are simpler and enough (Room would only pay off with album/song search, a later spec). No new dependency (`kotlinx-serialization-json` is already used).
- **Store.** `LibraryCache` writes one file per request in `filesDir/library-cache/`, named `sha256(serverKey|endpoint|id)` (`serverKey` = scheme/host/port + user, already in `SubsonicClient`). Each file is a small envelope `{version, fetchedAt, payload}` encoded with kotlinx-serialization (`Artist`, `Album`, `Track` are already `@Serializable`; `ArtistDetail` and `AlbumDetail` get the annotation). Atomic write (`.tmp` then rename). A file that fails to parse, or has another `version`, is treated as missing. After each write the folder is trimmed to 20 MB by oldest `fetchedAt`/mtime. Sign-out and "Clear saved library" delete the folder.
- **Repository.** A `LibraryRepository` wraps `SubsonicClient` + `LibraryCache` and exposes each request as a `Flow<LibraryResult<T>>`: first `Saved(data)` if present, then `Fresh(data)` after a successful fetch, or `Offline(data)` if the fetch failed and a saved copy exists, or `Failed(kind)` if there is none. A saved copy under 60 s old skips the fetch. Auth errors are always `Failed(Auth)` (FR-004). Concurrent identical requests are shared.
- **View model.** `LibraryViewModel.Load.Ready` gains `offline: Boolean`; `Load.Loading` only appears with nothing saved. Because list items are keyed by id and `LazyListState` lives in `MainViewModel` (FR-015), an in-place replacement does not move the list.
- **UI.** A one-line `OfflineNote` under the title when `offline`; a "Refresh" button next to Settings on the artist screen; "Clear saved library" under a "Storage" heading in Settings.
- **Startup.** The artist screen reads the saved list on a background dispatcher before the first network answer (NFR-007); the signed-in state still comes from `AccountRepository` as today, so a saved list is only ever shown to the account it belongs to.
- **Tests (pure/JVM).** Cache round trip; corrupt file and old `version` ignored; trim by size; keys differ per server/user and per id; repository sequences with a fake client: saved→fresh, saved→offline, none→failed, fresh-within-60 s skips the fetch, auth error with a saved copy still fails; removal on the server disappears after refresh. Manual: airplane-mode scenarios in `docs/manual-qa.md`.
- **Out of scope.** Prefetching the whole library, explicit downloads, searching albums/songs.

## 8. Stream quality (FR-070..074)
- **Queue holds no URLs with tokens.** `PlayerController.play` queues `MediaItem`s with a plain `athenastream:<trackId>` URI. The service wraps its `OkHttpDataSource` in a `ResolvingDataSource` whose `Resolver` turns that into the real `stream` URL **when ExoPlayer opens the stream**: fresh salt/token (so no token is kept in the queue, a small win for constitution §4) plus the quality parameters of FR-071/072. This also keeps `PlayerController` independent of the network.
- **Per-track decision.** The resolver keeps a small map `trackId → decided quality` for the current playback and reuses it when the same track is opened again (seek beyond the buffer), so quality never changes mid-track (FR-071). The map is cleared when the queue changes or the player stops.
- **Network class.** `ConnectivityManager.getNetworkCapabilities(activeNetwork)` → `NET_CAPABILITY_NOT_METERED` decides; unknown/no network → treat as metered (the setting applies). Needs the normal permission `ACCESS_NETWORK_STATE`. The setting is read from `SettingsRepository` (a `StateFlow` snapshot in the service).
- **Fallback (FR-072).** If a converted request answers with an HTTP error, or an `application/json` body (a Subsonic error), the same track is re-resolved once as Original (flag in the map) without changing the queue; this needs a small `DataSource` wrapper that inspects the response code/content type.
- **Quality line (FR-074).** `Track` gains `suffix`, `bitRate` (kbps) and `contentType` from `getAlbum`; `PlayerState` carries `qualityLine` from a pure function `qualityLabel(track, networkClass, setting)`. Played quality is assumed `min(source bitrate, setting)` when the source bitrate is known, else the setting: a label, not a measurement. The network class shown is the one used at track start (kept in the resolver's map, published via session extras).
- **Interaction with the buffer (§6).** Lower bitrates only make the 2–5 minute buffer cheaper; the byte target in §6 stays.
- **Spike T10.1 (first).** Against the real server, run the probes below for a FLAC and a VBR MP3, through the debug build on the phone (it has the login) or by you with `curl` and a stream URL; debug logging records only status, `Content-Type`, `Content-Length`/`Transfer-Encoding`, `Accept-Ranges`/`Content-Range`, time to first byte and byte counts, never the URL or token.
  - **S1** Convert with `maxBitRate=192&estimateContentLength=true`, once without and once with `format=mp3`: which format does the server pick by default, and does `format=mp3` work?
  - **S2** Range probe: send `Range: bytes=1000000-`. 206 with `Content-Range` → case A; 200 → B/C/D.
  - **S3** Length estimate: compare the announced `Content-Length` with the bytes actually received on a full download. Within ±3 % → usable; missing or off by more → unusable (case D unless S4 passes).
  - **S4** `timeOffset=60`: does the stream start at about 1:00, with a valid header, and how long to first byte?
  - **S5** Start latency (ffmpeg spin-up) for the FLAC and the VBR MP3 on Wi-Fi.
  - **S6** In ExoPlayer: seek inside the buffer, back within the kept minute, and far ahead (about 80 % of a 5-minute track), recording time to resume and position error.

- **Seeking cases (NFR-008) and strategies, decided by S1–S6.**

| Case | Server behaviour | Strategy | Fallback if that does not hold up |
|---|---|---|---|
| **A** | S2 gives 206 | Native ExoPlayer seeking; nothing to build. | None needed. |
| **B** | S2 gives 200, S3 length usable | ExoPlayer reads and discards up to the target. Accept for jumps inside about 3 MB (about 2 minutes at 192 kbps); for longer jumps on a metered network show the normal buffering state, and if S6 shows it feels bad go to C. | If long jumps take too long and C is unavailable: treat as D for jumps beyond the limit only. |
| **C** | S2 gives 200 and S4 works | When a converted stream is opened at a position beyond the limit, a small translation in the resolver requests `timeOffset = position / (bitrate / 8)` and serves it as if it began at that position. Only used where the format is frame-synchronised (MP3, ADTS/AAC); with the format fixed to `mp3` this holds. | If the result drifts by more than 3 s or fails to decode: fall back to B behaviour for that track. |
| **D** | S2 gives 200, S3 unusable, S4 fails | Seeking limited to the buffered and kept part (NFR-008): the seek bar clamps to the buffered extent, the quality line adds "seeking limited at this quality". Original (Wi-Fi, or the setting) is unlimited. | A user who needs to seek far on mobile data sets the quality to Original. No automatic switch to the original: it would burn mobile data without being asked. |

  **Format choice (FR-072).** If the server's default converted format is not constant-bitrate MP3 (for example Opus in Ogg, which cannot be seeked by estimated length), add `format=mp3` to FR-072; if the `mp3` profile does not exist on the server, omit `format` and fall back as FR-072 says (Original). At 192 kbps MP3 sounds about like 128 kbps Opus: acceptable for mobile, noted in the Playback setting's help text.
  **Duration.** The song's duration from `getAlbum` is set in the `MediaMetadata`, because a converted stream may not announce a length (otherwise the seek bar would show no length).
  **Position accuracy.** Converted CBR streams are seeked by `bytes / (bitrate/8)`; the estimate is checked in S6 and the tolerance (3 s) adjusted in NFR-008 if needed.
  **Decision record.** After the spike, the chosen case, format and thresholds are written here, and the unused strategies' tasks (T10.9 variants) are struck out.
- **Risks.** Converted streams can be slower to start (ffmpeg) and may not support Range; Navidrome needs ffmpeg and a transcoding profile enabled for the user/player; VBR `bitRate` is an average, so the label can be slightly off.
- **Tests.** Pure: `chooseQuality(notMetered, setting)`, `streamParams(kbps)` → query, `qualityLabel`; resolver with a fake connectivity (decision held per track, re-decided for the next track, fallback to Original once). Manual: Wi-Fi ↔ mobile switching mid-queue, FLAC vs low-bitrate MP3, seeking.

## 4. Risks
- A saved library can show things the server no longer has until the next successful refresh (FR-051); acceptable and visible through the offline line only when offline.
- The longer buffer (FR-030) and lower mobile quality (FR-070) may still not be enough on very weak coverage; judge on a real journey before specifying anything bigger (disk cache, reconnect: deferred).
- No credentials in the repo: first-run sign-in required; automated verification is limited to unit tests with MockWebServer and the unauthenticated `ping` shape.
- Android 17 local-network permission denial → sign-in shows a specific message.

## 5. Cover art (FR-040..044)
Same approach as Podcasts' `ArtworkStore`, no image library: one file per cover id in `filesDir/art/<sha256(server|id)>`, validated by decoding bounds, decoded with a power-of-two sample size at the drawn size, small `LruCache`, grayscale through the shared colour matrix at draw time. Differences: the key is the Subsonic `coverArt` id, not a URL (URLs carry a fresh salt/token each time), the URL is built by `SubsonicClient.coverArtUrl(id, 800)`, downloads happen on demand when a row is composed (semaphore of 4), and a missing picture draws the launcher icon instead of nothing. Per-song art: songs carry their own `coverArt`; `getAlbum` fills the album's when a song has none. Notification/lock screen: tracks get `artworkUri = athenaart:<id>`; a custom Media3 `BitmapLoader` in the service resolves it from the store (512 px, grayscale per setting) or draws the icon.
