package se.anders.athenamusic.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** FR-080. */
class CueDuckerTest {
    @Test
    fun staysDownForTheRequestedTimeUpToALimit() {
        assertEquals(900L, duckTimeMs(900))
        assertEquals(5_000L, duckTimeMs(60_000))
    }

    @Test
    fun ignoresRequestsWithoutATime() {
        assertNull(duckTimeMs(0))
        assertNull(duckTimeMs(-1))
    }
}
