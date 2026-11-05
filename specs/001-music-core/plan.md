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
Navidrome 0.62.0 at `http://192.168.1.180:4533` (probed 2026-11-05: `/rest/ping` answers `openSubsonic: true`; Caddy also serves https on 192.168.1.180:443 — not used in v1). API version sent: `v=1.16.1`, `c=athena-music`, `f=json`.

### 3.2 Auth
Subsonic token auth: per request `s` = random 8-byte hex salt, `t` = md5(password + salt). The password never leaves the device in clear text. Stream URLs are built at play time, one salt per media item; they hold a token and are secrets (redacted in logs). MD5 is mandated by the protocol; it is not used for anything else.

### 3.3 Endpoints
`ping`, `getArtists`, `getArtist`, `getAlbum`, `stream`, `getCoverArt`. Nothing else in v1.

### 3.4 Playback
`PlaybackService : MediaSessionService` with `ExoPlayer`, `AudioAttributes(USAGE_MEDIA, CONTENT_TYPE_MUSIC)`, audio focus on, `setHandleAudioBecomingNoisy(true)`, OkHttp data source. UI uses a `MediaController`; `PlayerController` maps to `StateFlow<PlayerState>`. The queue is the album's tracks (`MediaItem` with `mediaId` = track id and metadata title/artist/album), URIs resolved by the controller before `setMediaItems`. Stream URLs are minted by the controller at play time, in the UI process, using the stored password.

### 3.5 Local network (Android 17)
Cleartext to a private LAN address needs `ACCESS_LOCAL_NETWORK` (runtime, requested at sign-in) and `network_security_config` permitting cleartext.

### 3.6 Appearance
Copied from Podcasts: `Theme.kt`, fonts, `LauncherAppearanceSource`, `effectiveAppearance`, same Settings names. Bridge needs both apps signed with the same key (debug keystore on this Mac).

### 3.7 Substreamer
Behavioural reference only (no code copied).

## 4. Risks
- No credentials in the repo: first-run sign-in required; automated verification is limited to unit tests with MockWebServer and the unauthenticated `ping` shape.
- Android 17 local-network permission denial → sign-in shows a specific message.

## 5. Cover art (FR-040..044)
Same approach as Podcasts' `ArtworkStore`, no image library: one file per cover id in `filesDir/art/<sha256(server|id)>`, validated by decoding bounds, decoded with a power-of-two sample size at the drawn size, small `LruCache`, grayscale through the shared colour matrix at draw time. Differences: the key is the Subsonic `coverArt` id, not a URL (URLs carry a fresh salt/token each time), the URL is built by `SubsonicClient.coverArtUrl(id, 800)`, downloads happen on demand when a row is composed (semaphore of 4), and a missing picture draws the launcher icon instead of nothing. Per-song art: songs carry their own `coverArt`; `getAlbum` fills the album's when a song has none. Notification/lock screen: tracks get `artworkUri = athenaart:<id>`; a custom Media3 `BitmapLoader` in the service resolves it from the store (512 px, grayscale per setting) or draws the icon.
