package se.anders.athenamusic

import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import se.anders.athenamusic.data.server.ServerErrorKind
import se.anders.athenamusic.data.server.ServerException
import se.anders.athenamusic.data.server.SubsonicAuth
import se.anders.athenamusic.data.server.SubsonicClient
import se.anders.athenamusic.data.server.normalizeServerUrl
import se.anders.athenamusic.data.server.redact

class SubsonicTest {
    private lateinit var server: MockWebServer

    @Before fun setUp() { server = MockWebServer().apply { start() } }
    @After fun tearDown() { server.close() }

    private fun client(password: String = "sesame") =
        SubsonicClient(OkHttpClient(), server.url("/").toString().trimEnd('/'), "anders", password) { "c19b2d" }

    private fun reply(body: String) = server.enqueue(MockResponse.Builder().body(body).build())

    @Test fun tokenIsMd5OfPasswordAndSalt() {
        // The worked example from the Subsonic API documentation.
        assertEquals("26719a1196d2a940705a59634eb18eab", SubsonicAuth.token("sesame", "c19b2d"))
    }

    @Test fun saltIsRandomHex() {
        val a = SubsonicAuth.newSalt(); val b = SubsonicAuth.newSalt()
        assertEquals(16, a.length); assertTrue(a.all { it in "0123456789abcdef" }); assertFalse(a == b)
    }

    @Test fun normalizesAddresses() {
        assertEquals("http://music.local:4533", normalizeServerUrl(" music.local:4533/ "))
        assertEquals("https://music.example.org", normalizeServerUrl("https://music.example.org"))
        assertNull(normalizeServerUrl("   "))
        assertNull(normalizeServerUrl("http://"))
    }

    @Test fun requestCarriesTokenNotPassword() = runBlocking {
        reply("""{"subsonic-response":{"status":"ok","version":"1.16.1"}}""")
        client().ping()
        val url = server.takeRequest().url
        assertEquals("/rest/ping", url.encodedPath)
        assertEquals("anders", url.queryParameter("u"))
        assertEquals("26719a1196d2a940705a59634eb18eab", url.queryParameter("t"))
        assertEquals("c19b2d", url.queryParameter("s"))
        assertNull(url.queryParameter("p"))
        assertFalse(url.toString().contains("sesame"))
    }

    @Test fun parsesArtistsAcrossIndexes() = runBlocking {
        reply("""{"subsonic-response":{"status":"ok","artists":{"index":[
            {"name":"A","artist":[{"id":"1","name":"ABBA","albumCount":3}]},
            {"name":"B","artist":[{"id":"2","name":"Beck","albumCount":1,"extra":true},{"id":"3","name":"Bjork"}]}]}}}""")
        val artists = client().artists()
        assertEquals(listOf("ABBA", "Beck", "Bjork"), artists.map { it.name })
        assertEquals(3, artists[0].albumCount)
    }

    @Test fun emptyLibraryIsEmptyList() = runBlocking {
        reply("""{"subsonic-response":{"status":"ok","artists":{"ignoredArticles":"The"}}}""")
        assertTrue(client().artists().isEmpty())
    }

    @Test fun parsesArtistAlbums() = runBlocking {
        reply("""{"subsonic-response":{"status":"ok","artist":{"id":"1","name":"ABBA","album":[{"id":"9","name":"Arrival","year":1976,"songCount":10}]}}}""")
        val detail = client().artist("1")
        assertEquals("ABBA", detail.name)
        assertEquals(1976, detail.albums.single().year)
        assertEquals("1", server.takeRequest().url.queryParameter("id"))
    }

    @Test fun albumTracksAreOrderedByDiscThenTrack() = runBlocking {
        reply("""{"subsonic-response":{"status":"ok","album":{"id":"9","name":"Double","artist":"X","song":[
            {"id":"d2t1","title":"Four","track":1,"discNumber":2,"duration":60},
            {"id":"d1t2","title":"Two","track":2,"duration":60},
            {"id":"d1t1","title":"One","track":1,"discNumber":1,"duration":60}]}}}""")
        assertEquals(listOf("d1t1", "d1t2", "d2t1"), client().album("9").tracks.map { it.id })
    }

    @Test fun authErrorCodeMapsToAuth() {
        reply("""{"subsonic-response":{"status":"failed","error":{"code":40,"message":"Wrong username or password"}}}""")
        assertKind(ServerErrorKind.Auth) { client().ping() }
    }

    @Test fun otherErrorMapsToServer() {
        reply("""{"subsonic-response":{"status":"failed","error":{"code":70,"message":"not found"}}}""")
        assertKind(ServerErrorKind.Server) { client().album("nope") }
    }

    @Test fun nonSubsonicReplyIsRecognised() {
        reply("<html>hello</html>")
        assertKind(ServerErrorKind.NotSubsonic) { client().ping() }
    }

    @Test fun unreachableServerIsNetworkError() {
        val c = client(); server.close()
        assertKind(ServerErrorKind.Network) { c.ping() }
    }

    @Test fun streamUrlHasTokenAndNoTranscoding() {
        val url = client().streamUrl("42")
        assertTrue(url.contains("/rest/stream?"))
        assertTrue(url.contains("id=42"))
        assertTrue(url.contains("t=26719a1196d2a940705a59634eb18eab"))
        assertFalse(url.contains("maxBitRate")); assertFalse(url.contains("format=")); assertFalse(url.contains("sesame"))
    }

    @Test fun redactionDropsEverythingAfterTheHost() {
        val shown = redact(client().streamUrl("42"))
        assertFalse(shown.contains("t=")); assertFalse(shown.contains("anders")); assertFalse(shown.contains("42"))
        assertTrue(shown.endsWith("/…"))
    }

    private fun assertKind(kind: ServerErrorKind, block: suspend () -> Unit) {
        try { runBlocking { block() }; fail("expected $kind") } catch (e: ServerException) { assertEquals(kind, e.kind) }
    }
}
