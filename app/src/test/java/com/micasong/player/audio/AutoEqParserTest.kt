package com.micasong.player.audio

import com.micasong.player.data.audio.AutoEqParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoEqParserTest {

    @Test
    fun `parses graphic eq points`() {
        val profile = AutoEqParser.parseGraphicEq("GraphicEQ: 20 -0.5; 200 2.0; 2000 -3.0; 20000 1.0")!!
        assertEquals(4, profile.points.size)
        assertEquals(20.0, profile.points.first().freqHz, 1e-9)
        assertEquals(-3.0, profile.points[2].gainDb, 1e-9)
    }

    @Test
    fun `graphic eq clamps outside range`() {
        val profile = AutoEqParser.parseGraphicEq("GraphicEQ: 100 -2.0; 1000 4.0")!!
        assertEquals(-2.0, profile.gainAt(50.0), 1e-9)     // below first
        assertEquals(4.0, profile.gainAt(5000.0), 1e-9)    // above last
    }

    @Test
    fun `graphic eq interpolates in log-frequency domain`() {
        // 100 Hz → 0 dB, 10000 Hz → 10 dB. 1000 Hz is the log midpoint → 5 dB.
        val profile = AutoEqParser.parseGraphicEq("GraphicEQ: 100 0.0; 10000 10.0")!!
        assertEquals(5.0, profile.gainAt(1000.0), 1e-6)
    }

    @Test
    fun `resample maps curve onto bands`() {
        val profile = AutoEqParser.parseGraphicEq("GraphicEQ: 100 0.0; 10000 10.0")!!
        val bands = profile.resampleTo(listOf(100, 1000, 10000))
        assertEquals(listOf(0.0, 5.0, 10.0), bands.map { Math.round(it * 1e6) / 1e6 })
    }

    @Test
    fun `blank graphic eq is null`() {
        assertNull(AutoEqParser.parseGraphicEq(null))
        assertNull(AutoEqParser.parseGraphicEq(""))
    }

    @Test
    fun `parses apo preamp and peaking filters`() {
        val apo = """
            Preamp: -6.5 dB
            Filter 1: ON PK Fc 105 Hz Gain -3.5 dB Q 0.70
            Filter 2: ON PK Fc 1000 Hz Gain 2.0 dB Q 1.41
        """.trimIndent()
        val profile = AutoEqParser.parseApo(apo)
        assertEquals(-6.5, profile.preampDb, 1e-9)
        assertEquals(2, profile.filters.size)
        assertEquals("PK", profile.filters[0].type)
        assertEquals(105.0, profile.filters[0].freqHz, 1e-9)
        assertEquals(-3.5, profile.filters[0].gainDb, 1e-9)
        assertEquals(1.41, profile.filters[1].q, 1e-9)
    }

    @Test
    fun `apo with no filters yields empty profile`() {
        val profile = AutoEqParser.parseApo("# just a comment\n")
        assertEquals(0.0, profile.preampDb, 1e-9)
        assertTrue(profile.filters.isEmpty())
    }
}
