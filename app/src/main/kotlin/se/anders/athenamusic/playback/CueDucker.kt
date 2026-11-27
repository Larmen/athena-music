package se.anders.athenamusic.playback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import androidx.media3.common.Player

private const val ACTION_DUCK = "se.anders.athenanavigator.action.DUCK"
private const val EXTRA_DURATION_MS = "duration_ms"

/** Signature-protected and declared by Athena Navigator, so only an app signed with the same key can send. */
private const val PERMISSION_DUCK = "se.anders.athenanavigator.permission.DUCK"
private const val DUCKED_VOLUME = 0.2f
private const val LONGEST_DUCK_MS = 5_000L

/** How long to stay turned down for a request of [requestedMs], or null to ignore it. */
fun duckTimeMs(requestedMs: Long): Long? = if (requestedMs > 0) minOf(requestedMs, LONGEST_DUCK_MS) else null

/** FR-080: turns the player down while Athena Navigator sounds a cue, without pausing it. Main thread only. */
class CueDucker(private val context: Context, private val player: Player) {
    private val handler = Handler(Looper.getMainLooper())
    private val restore = Runnable { player.volume = 1f }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val time = duckTimeMs(intent.getLongExtra(EXTRA_DURATION_MS, 0)) ?: return
            handler.removeCallbacks(restore)
            player.volume = DUCKED_VOLUME
            handler.postDelayed(restore, time)
        }
    }

    fun register() {
        context.registerReceiver(receiver, IntentFilter(ACTION_DUCK), PERMISSION_DUCK, handler, Context.RECEIVER_EXPORTED)
    }

    fun release() {
        context.unregisterReceiver(receiver)
        handler.removeCallbacks(restore)
    }
}
