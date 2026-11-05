package se.anders.athenamusic.data.server

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/** Why a server call failed. Carries no URL or credential (constitution §4). */
enum class ServerErrorKind { InvalidUrl, Network, Auth, NotSubsonic, Server }

class ServerException(val kind: ServerErrorKind) : Exception(kind.name)

@Serializable data class Artist(val id: String, val name: String, val albumCount: Int = 0)
@Serializable data class Album(val id: String, val name: String, val artist: String = "", val year: Int = 0, val songCount: Int = 0, val coverArt: String? = null)
@Serializable data class Track(
    val id: String,
    val title: String,
    val artist: String = "",
    val album: String = "",
    val track: Int = 0,
    val discNumber: Int = 1,
    val duration: Int = 0,
    val coverArt: String? = null,
)

data class ArtistDetail(val name: String, val albums: List<Album>)
data class AlbumDetail(val name: String, val artist: String, val coverArt: String?, val tracks: List<Track>)

/**
 * Minimal Subsonic / OpenSubsonic client (spec FR-001, FR-010..012, FR-021). Navidrome is the target server.
 * Every request carries a fresh salt and token (plan §3.2); the password is held only in memory.
 */
class SubsonicClient(
    private val http: OkHttpClient,
    baseUrl: String,
    private val username: String,
    private val password: String,
    private val newSalt: () -> String = SubsonicAuth::newSalt,
) {
    /** Identifies the server and user for cache keys; contains no secret. */
    val serverKey: String get() = "${base.scheme}://${base.host}:${base.port}|$username"

    private val base: HttpUrl = baseUrl.toHttpUrlOrNull() ?: throw ServerException(ServerErrorKind.InvalidUrl)

    private fun url(endpoint: String, vararg params: Pair<String, String>): HttpUrl {
        val salt = newSalt()
        return base.newBuilder()
            .addPathSegments("rest/$endpoint")
            .addQueryParameter("u", username)
            .addQueryParameter("t", SubsonicAuth.token(password, salt))
            .addQueryParameter("s", salt)
            .addQueryParameter("v", API_VERSION)
            .addQueryParameter("c", CLIENT_NAME)
            .addQueryParameter("f", "json")
            .apply { params.forEach { (k, v) -> addQueryParameter(k, v) } }
            .build()
    }

    /** URL ExoPlayer streams from (FR-021): original format, no transcoding parameters. It holds a token: a secret. */
    fun streamUrl(trackId: String): String = url("stream", "id" to trackId).toString()

    /** URL of a cover picture (FR-043). It holds a token: a secret, never logged. */
    fun coverArtUrl(coverArtId: String, sizePx: Int): HttpUrl = url("getCoverArt", "id" to coverArtId, "size" to sizePx.toString())

    suspend fun ping() { call("ping") }

    suspend fun artists(): List<Artist> {
        val indexes = call("getArtists")["artists"]?.jsonObject?.get("index")
        return indexes.objects().flatMap { it["artist"].objects() }.map { json.decodeFromJsonElement(Artist.serializer(), it) }
    }

    suspend fun artist(id: String): ArtistDetail {
        val artist = call("getArtist", "id" to id)["artist"]?.jsonObject ?: throw ServerException(ServerErrorKind.Server)
        return ArtistDetail(
            name = artist["name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            albums = artist["album"].objects().map { json.decodeFromJsonElement(Album.serializer(), it) },
        )
    }

    suspend fun album(id: String): AlbumDetail {
        val album = call("getAlbum", "id" to id)["album"]?.jsonObject ?: throw ServerException(ServerErrorKind.Server)
        val albumArt = album["coverArt"]?.jsonPrimitive?.contentOrNull
        return AlbumDetail(
            name = album["name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            artist = album["artist"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            coverArt = albumArt,
            // FR-040: a song without a picture of its own shows the album's.
            tracks = album["song"].objects().map { json.decodeFromJsonElement(Track.serializer(), it) }
                .map { it.copy(coverArt = it.coverArt ?: albumArt) }
                .sortedWith(compareBy({ it.discNumber }, { it.track })),
        )
    }

    /** Runs one API call and returns the `subsonic-response` object. Throws [ServerException] only. */
    private suspend fun call(endpoint: String, vararg params: Pair<String, String>): JsonObject = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url(endpoint, *params)).build()
        val body = try {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw ServerException(if (response.code == 401 || response.code == 403) ServerErrorKind.Auth else ServerErrorKind.Server)
                }
                response.body.string()
            }
        } catch (e: ServerException) {
            throw e
        } catch (_: IOException) {
            throw ServerException(ServerErrorKind.Network) // message dropped on purpose: it can contain the URL
        }
        parseResponse(body)
    }

    companion object {
        const val API_VERSION = "1.16.1"
        const val CLIENT_NAME = "athena-music"
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }

        /** Parses a Subsonic JSON reply: `status` must be ok, otherwise error code 40–44 means bad credentials. */
        fun parseResponse(body: String): JsonObject {
            val root = try {
                json.parseToJsonElement(body).jsonObject["subsonic-response"]?.jsonObject
            } catch (_: SerializationException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            } ?: throw ServerException(ServerErrorKind.NotSubsonic)
            if (root["status"]?.jsonPrimitive?.contentOrNull != "ok") {
                val code = root["error"]?.jsonObject?.get("code")?.jsonPrimitive?.intOrNull
                throw ServerException(if (code in 40..44) ServerErrorKind.Auth else ServerErrorKind.Server)
            }
            return root
        }

        /** Subsonic returns a single child as an object in some servers and an array in others; accept both. */
        private fun JsonElement?.objects(): List<JsonObject> = when (this) {
            null -> emptyList()
            is kotlinx.serialization.json.JsonArray -> map { it.jsonObject }
            is JsonObject -> listOf(this)
            else -> emptyList()
        }
    }
}
