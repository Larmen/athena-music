package se.anders.athenamusic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import se.anders.athenamusic.playback.BufferConfig

/** FR-030: the numbers of the spec, and the rules ExoPlayer's builder enforces (it throws on a bad combination). */
class BufferConfigTest {
    @Test fun targetsAreTheSpecifiedOnes() {
        assertTrue("min at least 2 min", BufferConfig.MIN_BUFFER_MS >= 120_000)
        assertTrue("max at most 5 min", BufferConfig.MAX_BUFFER_MS <= 300_000)
        assertEquals(2_500, BufferConfig.FOR_PLAYBACK_MS)
        assertEquals(5_000, BufferConfig.AFTER_REBUFFER_MS)
        assertEquals(60_000, BufferConfig.BACK_BUFFER_MS)
    }

    @Test fun combinationIsValidForExoPlayer() {
        assertTrue(BufferConfig.MAX_BUFFER_MS >= BufferConfig.MIN_BUFFER_MS)
        assertTrue(BufferConfig.MIN_BUFFER_MS >= BufferConfig.FOR_PLAYBACK_MS)
        assertTrue(BufferConfig.MIN_BUFFER_MS >= BufferConfig.AFTER_REBUFFER_MS)
        BufferConfig.loadControl() // must not throw
    }

    @Test fun byteLimitHoldsFiveMinutesOfLosslessAudio() {
        val flacBytesPerSecond = 1_000_000 / 8 // about 1 Mbit/s
        assertTrue(BufferConfig.TARGET_BUFFER_BYTES >= flacBytesPerSecond * (BufferConfig.MAX_BUFFER_MS / 1000))
    }
}
