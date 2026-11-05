package se.anders.athenamusic.domain

import java.text.Normalizer
import se.anders.athenamusic.data.server.Artist

private val MARKS = Regex("\\p{Mn}+")

/** Lower-case, accents removed: "Björk" and "bjork" compare equal (FR-014). */
fun searchKey(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFD).replace(MARKS, "").lowercase()

/** FR-014: keeps the artists whose name contains every word of [query]; a blank query keeps them all. */
fun filterArtists(artists: List<Artist>, query: String): List<Artist> {
    val words = searchKey(query).split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (words.isEmpty()) return artists
    return artists.filter { artist ->
        val name = searchKey(artist.name)
        words.all { it in name }
    }
}
