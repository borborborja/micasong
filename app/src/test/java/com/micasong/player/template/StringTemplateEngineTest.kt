package com.micasong.player.template

import com.micasong.player.data.template.StringTemplateEngine
import org.junit.Assert.assertEquals
import org.junit.Test

class StringTemplateEngineTest {

    private val fields = mapOf(
        "title" to "Viva La Vida",
        "artist" to "Coldplay",
        "album" to "",
        "year" to "2008",
    )

    @Test
    fun `substitutes placeholders`() {
        assertEquals("Viva La Vida — Coldplay", StringTemplateEngine.render("%title% — %artist%", fields))
    }

    @Test
    fun `conditional block shows when field present`() {
        assertEquals("Coldplay (2008)", StringTemplateEngine.render("%artist%{ (%year%)}", fields))
    }

    @Test
    fun `conditional block hides when field empty`() {
        // album is empty → the block is dropped entirely
        assertEquals("Coldplay", StringTemplateEngine.render("%artist%{ · %album%}", fields))
    }

    @Test
    fun `line break token becomes newline`() {
        assertEquals("Viva La Vida\nColdplay", StringTemplateEngine.render("%title%%lb%%artist%", fields))
    }

    @Test
    fun `caps formatting upper-cases`() {
        assertEquals("COLDPLAY", StringTemplateEngine.render("^^%artist%^^", fields))
    }

    @Test
    fun `bold markers are stripped for plain text`() {
        assertEquals("Coldplay", StringTemplateEngine.render("**%artist%**", fields))
    }

    @Test
    fun `localized label resolves`() {
        assertEquals("Hi-Res", StringTemplateEngine.render("%string.hires%", fields))
    }

    @Test
    fun `nested blocks resolve innermost first`() {
        val result = StringTemplateEngine.render("%title%{ — %artist%{ (%year%)}}", fields)
        assertEquals("Viva La Vida — Coldplay (2008)", result)
    }
}
