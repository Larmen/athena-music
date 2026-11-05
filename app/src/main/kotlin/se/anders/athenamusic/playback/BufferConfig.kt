package se.anders.athenamusic.playback

import androidx.media3.exoplayer.DefaultLoadControl

/**
 * How far ahead the player buffers (spec FR-030, plan §6). On a patchy connection the buffer is what rides out the
 * gaps, so it is much larger than ExoPlayer's default of about 50 seconds. Numbers are meant to be tuned on a journey.
 */
object BufferConfig {
    /** Keep loading until at least this much is buffered ahead. */
    const val MIN_BUFFER_MS = 120_000

    /** Stop loading once this much is buffered ahead. */
    const val MAX_BUFFER_MS = 300_000

    /** Start playing once this much is buffered. */
    const val FOR_PLAYBACK_MS = 2_500

    /** Resume after a stall once this much is buffered. */
    const val AFTER_REBUFFER_MS = 5_000

    /** Already-played audio kept behind the position, so a short rewind needs no new request. */
    const val BACK_BUFFER_MS = 60_000

    /**
     * ExoPlayer also caps the buffer by size (about 13 MB for audio by default), which would hold a lossless file of
     * about 1 Mbit/s to roughly 100 s. Five minutes of it is about 37 MB, so the byte limit is raised above that and
     * time wins over size.
     */
    const val TARGET_BUFFER_BYTES = 48 * 1024 * 1024

    fun loadControl(): DefaultLoadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(MIN_BUFFER_MS, MAX_BUFFER_MS, FOR_PLAYBACK_MS, AFTER_REBUFFER_MS)
        .setBackBuffer(BACK_BUFFER_MS, /* retainBackBufferFromKeyframe = */ true)
        .setTargetBufferBytes(TARGET_BUFFER_BYTES)
        .setPrioritizeTimeOverSizeThresholds(true)
        .build()
}
