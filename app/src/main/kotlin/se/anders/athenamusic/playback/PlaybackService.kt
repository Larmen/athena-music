package se.anders.athenamusic.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.BitmapLoader
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import se.anders.athenamusic.AthenaMusicApplication
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import okhttp3.OkHttpClient
import se.anders.athenamusic.MainActivity

/**
 * Streams music from the user's server (FR-020..023). A foreground media service, so audio continues with the
 * screen off and can be controlled from the notification, lock screen, headset and Bluetooth (FR-022).
 */
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(OkHttpDataSource.Factory(OkHttpClient())))
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        // Tapping the notification or lock-screen widget opens the player screen (FR-022).
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java)
                .setAction(MainActivity.ACTION_OPEN_PLAYER)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        session = MediaSession.Builder(this, player).setSessionActivity(open).setBitmapLoader(CoverBitmapLoader()).build()
    }

    /**
     * Gives the notification and lock screen the song's cover (FR-044): `athenaart:<id>` is resolved through the same
     * store as the screens, in the chosen art style, or the Athena Music icon when there is none.
     */
    private inner class CoverBitmapLoader : BitmapLoader {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val container get() = (application as AthenaMusicApplication).container

        override fun supportsMimeType(mimeType: String) = mimeType.startsWith("image/")

        override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> {
            val future = SettableFuture.create<Bitmap>()
            val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size)
            if (bitmap != null) future.set(bitmap) else future.setException(IllegalArgumentException("not an image"))
            return future
        }

        override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
            val future = SettableFuture.create<Bitmap>()
            scope.launch {
                try {
                    val style = container.settings.settings.first().artStyle
                    future.set(container.artwork.mediaBitmap(coverArtIdOf(uri), style))
                } catch (e: Exception) {
                    future.setException(e)
                }
            }
            return future
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    /** Swiping the app away while paused ends the service; while playing it keeps going. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }
}
