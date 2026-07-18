package com.micasong.player.audio

import com.micasong.player.data.audio.ReplayGain
import com.micasong.player.data.audio.ReplayGainInfo
import com.micasong.player.data.audio.ReplayGainMode
import org.junit.Assert.assertEquals
import org.junit.Test

class ReplayGainTest {

    @Test
    fun `parses REPLAYGAIN track and album gain`() {
        val info = ReplayGain.fromTags(
            mapOf(
                "REPLAYGAIN_TRACK_GAIN" to "-6.00 dB",
                "REPLAYGAIN_ALBUM_GAIN" to "-3.50 dB",
                "REPLAYGAIN_TRACK_PEAK" to "0.98",
            )
        )
        assertEquals(-6.0, info.trackGainDb!!, 1e-6)
        assertEquals(-3.5, info.albumGainDb!!, 1e-6)
        assertEquals(0.98, info.trackPeak!!, 1e-6)
    }

    @Test
    fun `off mode is unity gain`() {
        val v = ReplayGain.volumeFor(ReplayGainInfo(trackGainDb = -6.0), ReplayGainMode.OFF, albumContext = false)
        assertEquals(1f, v, 1e-6f)
    }

    @Test
    fun `negative gain attenuates`() {
        val v = ReplayGain.volumeFor(ReplayGainInfo(trackGainDb = -6.0), ReplayGainMode.TRACK, albumContext = false)
        assertEquals(0.5012f, v, 1e-3f)
    }

    @Test
    fun `missing gain returns unity`() {
        val v = ReplayGain.volumeFor(ReplayGainInfo(), ReplayGainMode.TRACK, albumContext = false)
        assertEquals(1f, v, 1e-6f)
    }

    @Test
    fun `auto mode uses album gain in album context`() {
        val info = ReplayGainInfo(trackGainDb = -6.0, albumGainDb = -3.0)
        val albumV = ReplayGain.volumeFor(info, ReplayGainMode.AUTO, albumContext = true)
        val trackV = ReplayGain.volumeFor(info, ReplayGainMode.AUTO, albumContext = false)
        assertEquals(0.7079f, albumV, 1e-3f)   // 10^(-3/20)
        assertEquals(0.5012f, trackV, 1e-3f)   // 10^(-6/20)
    }

    @Test
    fun `peak clamp prevents clipping`() {
        val info = ReplayGainInfo(albumGainDb = 0.0, albumPeak = 1.25)
        val v = ReplayGain.volumeFor(info, ReplayGainMode.ALBUM, albumContext = true)
        assertEquals(0.8f, v, 1e-3f)   // 1 / 1.25
    }

    @Test
    fun `r128 tag is converted from q7 fixed point`() {
        // R128_TRACK_GAIN in Q7.8: -1280 → -5 dB, then +5 dB reference offset → 0 dB → unity
        val info = ReplayGain.fromTags(mapOf("R128_TRACK_GAIN" to "-1280"))
        assertEquals(0.0, info.trackGainDb!!, 1e-6)
    }
}
