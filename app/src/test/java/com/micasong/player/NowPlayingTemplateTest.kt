package com.micasong.player

import com.micasong.player.data.template.StringTemplateEngine
import org.junit.Assert.assertEquals
import org.junit.Test

/** Verifies the Now Playing subtitle template renders with optional blocks (spec §27). */
class NowPlayingTemplateTest {

    private fun render(album: String?) = StringTemplateEngine.render(
        "%artist%{ · %album%}",
        mapOf("artist" to "Radiohead", "album" to album, "title" to "Creep"),
    )

    @Test
    fun `album block shows when present and hides when empty`() {
        assertEquals("Radiohead · Pablo Honey", render("Pablo Honey"))
        assertEquals("Radiohead", render(null))
        assertEquals("Radiohead", render(""))
    }

    @Test
    fun `caps marker upper-cases`() {
        assertEquals("CREEP", StringTemplateEngine.render("^^%title%^^", mapOf("title" to "Creep")))
    }
}
