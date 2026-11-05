package se.anders.athenamusic.data.art

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import se.anders.athenamusic.R
import se.anders.athenamusic.data.server.SubsonicClient
import se.anders.athenamusic.domain.ArtStyle
import se.anders.athenamusic.domain.GRAYSCALE_MATRIX

/** Largest side, in pixels, of the pictures put on the notification and lock screen. */
private const val MEDIA_ART_PX = 512

/** Size asked from the server; big enough for the player screen on a phone. */
private const val FETCH_PX = 800

/**
 * Cover art (spec FR-040..044), modelled on Athena Podcasts' store. Each picture is fetched once into private storage,
 * keyed by its Subsonic cover id (URLs carry a fresh token, so they make poor keys), and decoded at the size it is drawn.
 * A picture that is missing or cannot be fetched is simply `null`: callers draw the Athena Music icon instead (FR-041).
 * Fetch URLs hold a token and are never logged.
 */
class ArtworkStore(
    context: Context,
    private val http: OkHttpClient,
    /** The signed-in client, or null while signed out. */
    private val client: () -> SubsonicClient?,
) {
    private val appContext = context.applicationContext
    private val dir = File(appContext.filesDir, "art").apply { mkdirs() }
    private val gate = Semaphore(MAX_PARALLEL)
    private val failed = ConcurrentHashMap.newKeySet<String>()
    private val cache = object : LruCache<String, ImageBitmap>(CACHE_BYTES) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }

    private fun fileFor(id: String): File {
        val server = client()?.let { it.serverKey } ?: ""
        val hash = MessageDigest.getInstance("SHA-256").digest("$server|$id".toByteArray()).joinToString("") { "%02x".format(it) }
        return File(dir, hash.take(40))
    }

    /** Makes sure the picture is on the phone. True when it is (already or now), false when there is none to show. */
    suspend fun ensure(id: String?): Boolean {
        if (id.isNullOrBlank()) return false
        val file = fileFor(id)
        if (file.isFile) return true
        if (id in failed) return false
        val c = client() ?: return false
        return gate.withPermit {
            if (file.isFile) return@withPermit true
            withContext(Dispatchers.IO) {
                try {
                    http.newCall(Request.Builder().url(c.coverArtUrl(id, FETCH_PX)).build()).execute().use { response ->
                        val body = response.body
                        // Subsonic answers errors with JSON and status 200, so the content type is what counts.
                        if (!response.isSuccessful || body.contentType()?.type != "image" || body.contentLength() > MAX_BYTES) {
                            failed += id; return@use false
                        }
                        val bytes = body.byteStream().use { it.readNBytes(MAX_BYTES.toInt() + 1) }
                        if (bytes.size > MAX_BYTES) { failed += id; return@use false }
                        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                        if (bounds.outWidth <= 0) { failed += id; return@use false }
                        val temp = File(dir, "${file.name}.tmp")
                        temp.writeBytes(bytes)
                        if (temp.renameTo(file)) true else { temp.delete(); false }
                    }
                } catch (_: Exception) {
                    false // offline: try again next time it is shown
                }
            }
        }
    }

    /** The stored picture decoded to at most [maxPx] on its longest side; null if it is not stored. */
    suspend fun bitmap(id: String?, maxPx: Int): ImageBitmap? = withContext(Dispatchers.IO) {
        if (id.isNullOrBlank()) return@withContext null
        val key = "${fileFor(id).name}@$maxPx"
        cache.get(key)?.let { return@withContext it }
        val file = fileFor(id).takeIf { it.isFile } ?: return@withContext null
        decode(file, maxPx)?.asImageBitmap()?.also { cache.put(key, it) }
    }

    /**
     * The picture for the notification and lock screen (FR-044): fetched if needed, at most 512 px, in the chosen
     * [style]; or the Athena Music icon when there is none (FR-041).
     */
    suspend fun mediaBitmap(id: String?, style: ArtStyle): Bitmap {
        if (ensure(id)) {
            val file = fileFor(id!!)
            decode(file, MEDIA_ART_PX)?.let { return if (style == ArtStyle.Grayscale) grayscale(it) else it }
        }
        return iconBitmap()
    }

    /** The launcher-icon mark on black, as a bitmap, for places that need one. */
    private fun iconBitmap(): Bitmap {
        val bitmap = createBitmap(MEDIA_ART_PX, MEDIA_ART_PX)
        val canvas = Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.BLACK)
        ContextCompat.getDrawable(appContext, R.drawable.ic_launcher_foreground)?.apply {
            setBounds(0, 0, MEDIA_ART_PX, MEDIA_ART_PX)
            draw(canvas)
        }
        return bitmap
    }

    private fun decode(file: File, maxPx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0) return null
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, maxPx) }
        return BitmapFactory.decodeFile(file.path, options)
    }

    private fun grayscale(source: Bitmap): Bitmap {
        val result = createBitmap(source.width, source.height)
        val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(ColorMatrix(GRAYSCALE_MATRIX)) }
        Canvas(result).drawBitmap(source, 0f, 0f, paint)
        return result
    }

    /** Forget everything (sign-out): another account must not see this one's pictures (FR-043). */
    fun clear() {
        dir.listFiles()?.forEach { it.delete() }
        cache.evictAll()
        failed.clear()
    }

    private companion object {
        const val MAX_BYTES = 5L * 1024 * 1024
        const val CACHE_BYTES = 8 * 1024 * 1024
        const val MAX_PARALLEL = 4
    }
}

/**
 * Power-of-two decode factor so the decoded picture is not much bigger than needed:
 * the largest factor that keeps the longest side at or above [maxPx].
 */
fun sampleSizeFor(width: Int, height: Int, maxPx: Int): Int {
    if (maxPx <= 0) return 1
    val longest = maxOf(width, height)
    var sample = 1
    while (longest / (sample * 2) >= maxPx) sample *= 2
    return sample
}
