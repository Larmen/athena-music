package se.anders.athenamusic.data.server

import java.security.MessageDigest
import java.security.SecureRandom
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Subsonic token authentication (plan §3.2): the server is sent `t = md5(password + salt)` and the random `salt`,
 * never the password. MD5 is mandated by the protocol and used for nothing else.
 */
object SubsonicAuth {
    fun token(password: String, salt: String): String =
        MessageDigest.getInstance("MD5").digest((password + salt).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    fun newSalt(random: SecureRandom = SecureRandom()): String =
        ByteArray(8).also(random::nextBytes).joinToString("") { "%02x".format(it) }
}

/** Turns what the user typed into a base address: adds `http://` when missing, trims, drops a trailing slash. Null if unusable. */
fun normalizeServerUrl(input: String): String? {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return null
    val withScheme = if ("://" in trimmed) trimmed else "http://$trimmed"
    val url = withScheme.toHttpUrlOrNull() ?: return null
    return url.toString().trimEnd('/')
}


