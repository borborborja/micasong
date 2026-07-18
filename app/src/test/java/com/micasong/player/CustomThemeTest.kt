package com.micasong.player

import com.micasong.player.data.theme.ThemeJson
import com.micasong.player.data.theme.parseHexColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Verifies custom-theme JSON parsing (Material Theme Builder shape) and hex handling (spec §25). */
class CustomThemeTest {

    @Test
    fun `parses material theme builder schemes`() {
        val json = """
            {"name":"Ocean","schemes":{
              "light":{"primary":"#0061A4","onPrimary":"#FFFFFF","surface":"#FDFCFF"},
              "dark":{"primary":"#9ECAFF","onPrimary":"#003258"}
            }}
        """.trimIndent()
        val theme = ThemeJson.parse(json)
        assertNotNull(theme)
        assertEquals("Ocean", theme!!.name)
        assertEquals(0xFF0061A4.toInt(), theme.colorFor("primary", darkScheme = false))
        assertEquals(0xFF9ECAFF.toInt(), theme.colorFor("primary", darkScheme = true))
        assertNull(theme.colorFor("tertiary", darkScheme = false))
    }

    @Test
    fun `accepts flat shape and round-trips`() {
        val theme = ThemeJson.parse("""{"light":{"primary":"#112233"}}""")!!
        val roundTrip = ThemeJson.parse(ThemeJson.toJson(theme))!!
        assertEquals(0xFF112233.toInt(), roundTrip.colorFor("primary", darkScheme = false))
    }

    @Test
    fun `hex parsing handles 3-6-8 digit forms`() {
        assertEquals(0xFFFFFFFF.toInt(), parseHexColor("#FFF"))
        assertEquals(0xFF102030.toInt(), parseHexColor("102030"))
        assertEquals(0x80FF0000.toInt(), parseHexColor("#80FF0000"))
        assertNull(parseHexColor("nope"))
    }

    @Test
    fun `invalid theme json returns null`() {
        assertNull(ThemeJson.parse("not json"))
        assertNull(ThemeJson.parse("{}"))
    }
}
