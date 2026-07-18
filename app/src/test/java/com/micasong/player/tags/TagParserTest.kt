package com.micasong.player.tags

import com.micasong.player.data.tags.TagParser
import com.micasong.player.data.tags.TagParserConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TagParserTest {

    @Test
    fun `splits multi-value fields without breaking names with spaces`() {
        val parsed = TagParser.parse(mapOf("ARTIST" to "The Beatles/John Lennon; Paul McCartney"))
        assertEquals(listOf("The Beatles", "John Lennon", "Paul McCartney"), parsed.artists)
    }

    @Test
    fun `single artist with spaces stays intact`() {
        val parsed = TagParser.parse(mapOf("ARTIST" to "The Rolling Stones"))
        assertEquals(listOf("The Rolling Stones"), parsed.artists)
    }

    @Test
    fun `genres split on comma`() {
        val parsed = TagParser.parse(mapOf("GENRE" to "Rock, Indie; Alternative"))
        assertEquals(listOf("Rock", "Indie", "Alternative"), parsed.genres)
    }

    @Test
    fun `original date preferred over year tag by default`() {
        val parsed = TagParser.parse(mapOf("YEAR" to "2015", "ORIGINALDATE" to "1998-06-01"))
        assertEquals(1998, parsed.year)
    }

    @Test
    fun `prefer year tag option flips precedence`() {
        val parsed = TagParser.parse(
            mapOf("YEAR" to "2015", "ORIGINALDATE" to "1998-06-01"),
            TagParserConfig(preferYearTag = true),
        )
        assertEquals(2015, parsed.year)
    }

    @Test
    fun `rating normalises across scales`() {
        assertEquals(8, TagParser.parseRating("4"))       // 4 of 5 stars → 8 half-stars
        assertEquals(7, TagParser.parseRating("3.5"))     // 3.5 stars
        assertEquals(8, TagParser.parseRating("80"))      // 80% → 4 stars
        assertEquals(10, TagParser.parseRating("255"))    // POPM max
        assertEquals(0, TagParser.parseRating(null))
    }

    @Test
    fun `musicbee love maps to favorite and excluded`() {
        assertTrue(TagParser.parse(mapOf("LOVE" to "L")).isFavorite)
        assertTrue(TagParser.parse(mapOf("LOVE" to "Banned")).excludedFromMixes)
        assertFalse(TagParser.parse(mapOf("LOVE" to "L")).excludedFromMixes)
    }

    @Test
    fun `compilation flag parsed`() {
        assertTrue(TagParser.parse(mapOf("COMPILATION" to "1")).compilation)
        assertFalse(TagParser.parse(mapOf("COMPILATION" to "0")).compilation)
    }

    @Test
    fun `ignore mbids option drops identifiers`() {
        val tags = mapOf("MUSICBRAINZ_ALBUMID" to "abc-123", "MUSICBRAINZ_ARTISTID" to "a1;a2")
        assertEquals("abc-123", TagParser.parse(tags).musicBrainzAlbumId)
        assertEquals(listOf("a1", "a2"), TagParser.parse(tags).musicBrainzArtistIds)
        val ignored = TagParser.parse(tags, TagParserConfig(ignoreMbids = true))
        assertEquals(null, ignored.musicBrainzAlbumId)
        assertTrue(ignored.musicBrainzArtistIds.isEmpty())
    }

    @Test
    fun `track number strips total`() {
        val parsed = TagParser.parse(mapOf("TRACK" to "3/12", "DISC" to "1/2"))
        assertEquals(3, parsed.trackNumber)
        assertEquals(1, parsed.discNumber)
    }

    @Test
    fun `explicit can be ignored`() {
        assertTrue(TagParser.parse(mapOf("ITUNESADVISORY" to "1")).explicit)
        assertFalse(TagParser.parse(mapOf("ITUNESADVISORY" to "1"), TagParserConfig(ignoreExplicit = true)).explicit)
    }
}
