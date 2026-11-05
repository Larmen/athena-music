package se.anders.athenamusic.playback

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import se.anders.athenamusic.data.server.SubsonicClient
import se.anders.athenamusic.data.server.Track

/** What the UI shows about playback (FR-024). */
data class PlayerState(
    val trackId: String? = null,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    /** Subsonic cover id of the current song (FR-040); null when the server has none. */
    val coverArt: String? = null,
    val playing: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val hasPrevious: Boolean = false,
    val hasNext: Boolean = false,
)

/** UI-side handle on the playback service's media session. */
class PlayerController(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var future: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var pending: (() -> Unit)? = null
    private var ticker: Job? = null

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = publish()
        override fun onIsPlayingChanged(isPlaying: Boolean) = startTicker(isPlaying)
    }

    fun connect() {
        if (future != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val f = MediaController.Builder(context, token).buildAsync()
        future = f
        f.addListener({
            controller = f.get().also { it.addListener(listener) }
            publish()
            pending?.invoke()
            pending = null
        }, ContextCompat.getMainExecutor(context))
    }

    /** FR-020: queue [tracks] (an album) and start at [startIndex]. */
    fun play(client: SubsonicClient, tracks: List<Track>, startIndex: Int) {
        val items = tracks.map { t ->
            MediaItem.Builder()
                .setMediaId(t.id)
                .setUri(client.streamUrl(t.id).toUri()) // holds a token: never logged (constitution §4)
                .setMediaMetadata(
                    MediaMetadata.Builder().setTitle(t.title).setArtist(t.artist).setAlbumTitle(t.album)
                        .setArtworkUri(t.coverArt?.let(::artworkUri)) // resolved by the service (FR-044)
                        .build(),
                )
                .build()
        }
        val start: () -> Unit = {
            controller?.run {
                setMediaItems(items, startIndex.coerceIn(0, items.lastIndex), 0L)
                prepare()
                play()
            }
        }
        connect()
        if (controller != null) start() else pending = start
    }

    fun togglePlayPause() { controller?.run { if (isPlaying) pause() else play() } }
    fun previous() { controller?.seekToPrevious() }
    fun next() { controller?.seekToNext() }
    fun seekTo(positionMs: Long) { controller?.seekTo(positionMs) }

    /** Stops playback and clears the queue (used on sign-out). */
    fun stop() { controller?.run { stop(); clearMediaItems() } }

    private fun publish() {
        val c = controller ?: return
        val item = c.currentMediaItem
        _state.value = if (item == null) PlayerState() else PlayerState(
            trackId = item.mediaId,
            title = item.mediaMetadata.title?.toString().orEmpty(),
            artist = item.mediaMetadata.artist?.toString().orEmpty(),
            album = item.mediaMetadata.albumTitle?.toString().orEmpty(),
            coverArt = item.mediaMetadata.artworkUri?.let(::coverArtIdOf),
            playing = c.isPlaying,
            positionMs = c.currentPosition.coerceAtLeast(0),
            durationMs = c.duration.takeIf { it > 0 } ?: 0,
            hasPrevious = c.hasPreviousMediaItem(),
            hasNext = c.hasNextMediaItem(),
        )
        startTicker(c.isPlaying)
    }

    /** Position moves without events, so while playing it is read twice a second. */
    private fun startTicker(playing: Boolean) {
        ticker?.cancel()
        if (!playing) return
        ticker = scope.launch {
            while (isActive) {
                delay(500)
                val c = controller ?: break
                if (_state.value.trackId != null) _state.value = _state.value.copy(positionMs = c.currentPosition.coerceAtLeast(0))
            }
        }
    }
}

private const val ART_SCHEME = "athenaart"

/** The in-app art address for a cover id: no server, no token (FR-044). */
fun artworkUri(coverArtId: String): android.net.Uri = android.net.Uri.fromParts(ART_SCHEME, coverArtId, null)

/** The cover id inside an [artworkUri], or null for any other address. */
fun coverArtIdOf(uri: android.net.Uri): String? = if (uri.scheme == ART_SCHEME) uri.schemeSpecificPart else null
