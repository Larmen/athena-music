package se.anders.athenamusic

import org.junit.Assert.assertEquals
import org.junit.Test
import se.anders.athenamusic.data.server.Artist
import se.anders.athenamusic.domain.filterArtists

class ArtistSearchTest {
    private val artists = listOf("The Arcade Fire", "Björk", "Air", "Animal Collective", "Sigur Rós").mapIndexed { i, n -> Artist(i.toString(), n) }
    private fun names(q: String) = filterArtists(artists, q).map { it.name }

    @Test fun blankQueryKeepsEverything() { assertEquals(5, names("").size); assertEquals(5, names("   ").size) }
    @Test fun caseInsensitive() = assertEquals(listOf("Air"), names("AIR"))
    @Test fun accentInsensitiveBothWays() { assertEquals(listOf("Björk"), names("bjork")); assertEquals(listOf("Sigur Rós"), names("ros")); assertEquals(listOf("Björk"), names("BJÖRK")) }
    @Test fun matchesAnywhereInName() = assertEquals(listOf("The Arcade Fire"), names("cade"))
    @Test fun everyWordMustMatchInAnyOrder() { assertEquals(listOf("The Arcade Fire"), names("fire arc")); assertEquals(emptyList<String>(), names("arc collective")) }
    @Test fun noMatchGivesEmptyList() = assertEquals(emptyList<String>(), names("zzz"))
    @Test fun keepsServerOrder() = assertEquals(listOf("The Arcade Fire", "Air", "Animal Collective"), names("a").filter { it != "Björk" && it != "Sigur Rós" })
}
