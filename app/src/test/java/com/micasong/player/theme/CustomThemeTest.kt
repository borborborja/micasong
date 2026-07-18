package com.micasong.player.theme

import com.micasong.player.data.theme.CustomTheme
import com.micasong.player.data.theme.ThemeJson
import com.micasong.player.data.theme.parseHexColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CustomThemeTest {

    @Test
    fun `parse hex colors of various lengths`() {
        assertEquals(0xFF6B4FA0.toInt(), parseHexColor("#6B4FA0"))
        assertEquals(0xFF6B4FA0.toInt(), parseHexColor("6b4fa0"))
        assertEquals(0x806B4FA0.toInt(), parseHexColor("#806B4FA0"))   // with alpha
        assertEquals(0xFFFFCC00.toInt(), parseHexColor("#FC0"))         // RGB shorthand
        assertNull(parseHexColor("not-a-color"))
        assertNull(parseHexColor("#12"))
    }

    @Test
    fun `parse flat theme shape`() {
        val json = """
            {
              "name": "Violeta",
              "light": { "primary": "#6B4FA0", "onPrimary": "#FFFFFF" },
              "dark":  { "primary": "#8A6FBF" }
            }
        """.trimIndent()
        val theme = ThemeJson.parse(json)!!
        assertEquals("Violeta", theme.name)
        assertEquals(0xFF6B4FA0.toInt(), theme.colorFor("primary", darkScheme = false))
        assertEquals(0xFF8A6FBF.toInt(), theme.colorFor("primary", darkScheme = true))
    }

    @Test
    fun `parse material theme builder shape with schemes wrapper`() {
        val json = """
            {
              "seed": "#6B4FA0",
              "schemes": {
                "light": { "primary": "#005AC1", "surface": "#FDFBFF" },
                "dark":  { "primary": "#AEC6FF" }
              }
            }
        """.trimIndent()
        val theme = ThemeJson.parse(json)!!
        assertEquals(0xFF005AC1.toInt(), theme.colorFor("primary", darkScheme = false))
        assertEquals(0xFFFDFBFF.toInt(), theme.colorFor("surface", darkScheme = false))
        assertEquals(0xFFAEC6FF.toInt(), theme.colorFor("primary", darkScheme = true))
    }

    @Test
    fun `missing role returns null`() {
        val theme = CustomTheme(light = mapOf("primary" to "#000000"))
        assertNull(theme.colorFor("secondary", darkScheme = false))
    }

    @Test
    fun `blank or invalid json returns null`() {
        assertNull(ThemeJson.parse(null))
        assertNull(ThemeJson.parse(""))
        assertNull(ThemeJson.parse("{ \"unrelated\": 1 }"))
    }

    @Test
    fun `json round trip`() {
        val theme = CustomTheme(
            name = "Test",
            light = mapOf("primary" to "#111111", "surface" to "#EEEEEE"),
            dark = mapOf("primary" to "#DDDDDD"),
        )
        val restored = ThemeJson.parse(ThemeJson.toJson(theme))!!
        assertEquals(theme, restored)
    }
}
