package com.micasong.player.tags

import com.micasong.player.data.tags.ArtistNfoParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtistNfoParserTest {

    private val sample = """
        <?xml version="1.0" encoding="UTF-8"?>
        <artist>
            <name>Radiohead</name>
            <sortname>Radiohead</sortname>
            <musicBrainzArtistID>a74b1b7f-71a5-4011-9441-d0b5e4122711</musicBrainzArtistID>
            <type>Group</type>
            <gender></gender>
            <biography>An English rock band formed in 1985.</biography>
            <genre>Alternative Rock</genre>
            <genre>Art Rock</genre>
            <style>Experimental</style>
            <mood>Melancholy</mood>
            <tag>british</tag>
            <thumb aspect="thumb">http://img/thumb.jpg</thumb>
            <thumb aspect="fanart">http://img/fanart.jpg</thumb>
        </artist>
    """.trimIndent()

    @Test
    fun `parses core fields`() {
        val nfo = ArtistNfoParser.parse(sample)!!
        assertEquals("Radiohead", nfo.name)
        assertEquals("a74b1b7f-71a5-4011-9441-d0b5e4122711", nfo.musicBrainzId)
        assertEquals("Group", nfo.type)
        assertEquals("An English rock band formed in 1985.", nfo.biography)
    }

    @Test
    fun `collects multi-value fields`() {
        val nfo = ArtistNfoParser.parse(sample)!!
        assertEquals(listOf("Alternative Rock", "Art Rock"), nfo.genres)
        assertEquals(listOf("Experimental"), nfo.styles)
        assertEquals(listOf("Melancholy"), nfo.moods)
        assertEquals(listOf("british"), nfo.tags)
    }

    @Test
    fun `resolves thumbs by aspect`() {
        val nfo = ArtistNfoParser.parse(sample)!!
        assertEquals(2, nfo.thumbs.size)
        assertEquals("http://img/thumb.jpg", nfo.thumbUrl("thumb"))
        assertEquals("http://img/fanart.jpg", nfo.thumbUrl("fanart"))
    }

    @Test
    fun `blank or malformed input yields null`() {
        assertNull(ArtistNfoParser.parse(null))
        assertNull(ArtistNfoParser.parse(""))
        assertNull(ArtistNfoParser.parse("<artist><name>unclosed"))
    }

    @Test
    fun `empty gender field is treated as absent`() {
        val nfo = ArtistNfoParser.parse(sample)!!
        assertNull(nfo.gender)
    }

    @Test
    fun `minimal nfo with only a name parses`() {
        val nfo = ArtistNfoParser.parse("<artist><name>Björk</name></artist>")!!
        assertEquals("Björk", nfo.name)
        assertTrue(nfo.genres.isEmpty())
    }
}
