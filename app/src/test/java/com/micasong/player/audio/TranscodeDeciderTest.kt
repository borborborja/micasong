package com.micasong.player.audio

import com.micasong.player.data.audio.CastTarget
import com.micasong.player.data.audio.NetworkType
import com.micasong.player.data.audio.TranscodeDecider
import com.micasong.player.data.audio.TranscodeSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TranscodeDeciderTest {

    @Test
    fun `original on wifi passes through`() {
        val d = TranscodeDecider.decide(320, NetworkType.WIFI, TranscodeSettings())
        assertFalse(d.transcode)
        assertEquals(0, d.targetBitrateKbps)
    }

    @Test
    fun `mobile cap transcodes an over-budget source`() {
        val d = TranscodeDecider.decide(320, NetworkType.MOBILE, TranscodeSettings(maxBitrateMobileKbps = 128))
        assertTrue(d.transcode)
        assertEquals(128, d.targetBitrateKbps)
        assertEquals("opus", d.targetFormat)
    }

    @Test
    fun `source within budget passes through`() {
        val d = TranscodeDecider.decide(96, NetworkType.MOBILE, TranscodeSettings(maxBitrateMobileKbps = 128))
        assertFalse(d.transcode)
    }

    @Test
    fun `metered wifi uses the mobile budget`() {
        val d = TranscodeDecider.decide(320, NetworkType.METERED_WIFI, TranscodeSettings(maxBitrateMobileKbps = 160))
        assertTrue(d.transcode)
        assertEquals(160, d.targetBitrateKbps)
    }

    @Test
    fun `chromecast always transcodes to aac`() {
        val d = TranscodeDecider.decide(128, NetworkType.WIFI, TranscodeSettings(), CastTarget.CHROMECAST)
        assertTrue(d.transcode)
        assertEquals("aac", d.targetFormat)
        assertEquals(256, d.targetBitrateKbps)   // no cap → default 256
    }

    @Test
    fun `chromecast respects the budget cap`() {
        val d = TranscodeDecider.decide(320, NetworkType.MOBILE, TranscodeSettings(maxBitrateMobileKbps = 128), CastTarget.CHROMECAST)
        assertEquals(128, d.targetBitrateKbps)
    }

    @Test
    fun `force instant transcodes even at original`() {
        val d = TranscodeDecider.decide(320, NetworkType.WIFI, TranscodeSettings(forceInstant = true))
        assertTrue(d.transcode)
        assertEquals(320, d.targetBitrateKbps)   // forced, no cap → 320 ceiling
    }

    @Test
    fun `unknown source bitrate with a cap transcodes`() {
        val d = TranscodeDecider.decide(null, NetworkType.MOBILE, TranscodeSettings(maxBitrateMobileKbps = 192))
        assertTrue(d.transcode)
        assertEquals(192, d.targetBitrateKbps)
    }

    @Test
    fun `api parameter maps to kbps`() {
        assertEquals(0, TranscodeDecider.bitrateForApiParam(-1))
        assertEquals(64, TranscodeDecider.bitrateForApiParam(1))
        assertEquals(128, TranscodeDecider.bitrateForApiParam(3))
        assertEquals(320, TranscodeDecider.bitrateForApiParam(7))
        assertEquals(0, TranscodeDecider.bitrateForApiParam(99))
    }
}
