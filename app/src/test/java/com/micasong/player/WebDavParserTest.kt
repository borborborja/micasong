package com.micasong.player

import com.micasong.player.data.provider.WebDavParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Verifies WebDAV PROPFIND multistatus parsing and audio detection (spec §46). */
class WebDavParserTest {

    private val xml = """
        <?xml version="1.0"?>
        <d:multistatus xmlns:d="DAV:">
          <d:response>
            <d:href>/music/</d:href>
            <d:propstat><d:prop><d:resourcetype><d:collection/></d:resourcetype></d:prop></d:propstat>
          </d:response>
          <d:response>
            <d:href>/music/song.flac</d:href>
            <d:propstat><d:prop>
              <d:getcontenttype>audio/flac</d:getcontenttype>
              <d:getcontentlength>8123456</d:getcontentlength>
              <d:resourcetype/>
            </d:prop></d:propstat>
          </d:response>
          <d:response>
            <d:href>/music/cover.jpg</d:href>
            <d:propstat><d:prop><d:getcontenttype>image/jpeg</d:getcontenttype><d:resourcetype/></d:prop></d:propstat>
          </d:response>
        </d:multistatus>
    """.trimIndent()

    @Test
    fun `parses entries and detects audio`() {
        val entries = WebDavParser.parse(xml)
        assertEquals(3, entries.size)

        val folder = entries.first { it.href == "/music/" }
        assertTrue(folder.isCollection)
        assertFalse(WebDavParser.isAudio(folder))

        val song = entries.first { it.href.endsWith("song.flac") }
        assertFalse(song.isCollection)
        assertEquals(8123456L, song.contentLength)
        assertTrue(WebDavParser.isAudio(song))

        val cover = entries.first { it.href.endsWith("cover.jpg") }
        assertFalse(WebDavParser.isAudio(cover))
    }

    @Test
    fun `detects audio by extension when content type is missing`() {
        val entries = WebDavParser.parse(
            """<d:multistatus xmlns:d="DAV:"><d:response><d:href>/a/track.mp3</d:href>
               <d:propstat><d:prop><d:resourcetype/></d:prop></d:propstat></d:response></d:multistatus>"""
        )
        assertTrue(WebDavParser.isAudio(entries.single()))
    }
}
