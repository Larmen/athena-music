package se.anders.athenamusic

import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import se.anders.athenamusic.data.art.sampleSizeFor
import se.anders.athenamusic.data.server.SubsonicClient

class ArtTest {
    @Test fun sampleSizeKeepsPictureAtLeastAsLargeAsDrawn() {
        assertEquals(1, sampleSizeFor(800, 800, 800))
        assertEquals(1, sampleSizeFor(800, 800, 500))
        assertEquals(2, sampleSizeFor(800, 800, 400))
        assertEquals(8, sampleSizeFor(800, 600, 100))
        assertEquals(1, sampleSizeFor(300, 300, 0))
    }

    private fun server() = MockWebServer().apply { start() }
    private fun client(s: MockWebServer) = SubsonicClient(OkHttpClient(), s.url("/").toString().trimEnd('/'), "u", "p") { "abc" }

    @Test fun coverArtUrlHasIdSizeAndTokenButNoPassword() {
        val s = server()
        val url = client(s).coverArtUrl("al-1", 800)
        assertEquals("/rest/getCoverArt", url.encodedPath)
        assertEquals("al-1", url.queryParameter("id")); assertEquals("800", url.queryParameter("size"))
        assertFalse(url.toString().contains("p=p")); assertTrue(url.queryParameter("t")!!.length == 32)
        s.close()
    }

    @Test fun songWithoutOwnArtGetsTheAlbumsAndOthersKeepTheirs() = runBlocking {
        val s = server()
        s.enqueue(MockResponse.Builder().body("""{"subsonic-response":{"status":"ok","album":{"id":"a","name":"A","artist":"X","coverArt":"al-a","song":[
            {"id":"1","title":"One","track":1,"coverArt":"mf-1"},{"id":"2","title":"Two","track":2}]}}}""").build())
        val album = client(s).album("a")
        assertEquals("al-a", album.coverArt)
        assertEquals(listOf("mf-1", "al-a"), album.tracks.map { it.coverArt })
        s.close()
    }

    @Test fun albumsCarryTheirCoverAndMissingIsNull() = runBlocking {
        val s = server()
        s.enqueue(MockResponse.Builder().body("""{"subsonic-response":{"status":"ok","artist":{"id":"1","name":"X","album":[
            {"id":"a","name":"A","coverArt":"al-a"},{"id":"b","name":"B"}]}}}""").build())
        val albums = client(s).artist("1").albums
        assertEquals("al-a", albums[0].coverArt); assertNull(albums[1].coverArt)
        s.close()
    }
}
