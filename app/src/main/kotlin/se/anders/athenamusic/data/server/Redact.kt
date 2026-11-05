package se.anders.athenamusic.data.server

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * The only way a server URL may be logged or shown (constitution §4): API and stream URLs carry the auth token and
 * user name in the query string, so everything after the host is dropped.
 */
fun redact(url: HttpUrl): String = "${url.scheme}://${url.host}:${url.port}/…"
fun redact(url: String): String = url.toHttpUrlOrNullSafe()?.let(::redact) ?: "<invalid url>"

private fun String.toHttpUrlOrNullSafe(): HttpUrl? = toHttpUrlOrNull()
