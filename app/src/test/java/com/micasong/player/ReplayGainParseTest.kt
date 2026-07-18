package com.micasong.player

import com.micasong.player.data.audio.ReplayGain
import com.micasong.player.data.audio.ReplayGainInfo
import com.micasong.player.data.audio.ReplayGainMode
import com.micasong.player.data.provider.SubsonicMappers
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Verifies OpenSubsonic replayGain parsing and gain→volume application (spec §15). */
class ReplayGainParseTest {

    @Test
    fun `subsonic replayGain maps into the track`() {
        val song = JSONObject(
            """{"id":"s1","title":"T","duration":100,"replayGain":{"trackGain":-6.0,"albumGain":-4.5,"trackPeak":0.98}}"""
        )
        val track = SubsonicMappers.parseSong(song, providerId = 1, streamUrl = "http://x?id=s1", coverUrl = null)
        assertEquals(-6.0, track.trackGainDb!!, 1e-6)
        assertEquals(-4.5, track.albumGainDb!!, 1e-6)
        assertEquals(0.98, track.trackPeak!!, 1e-6)
        assertNull(track.albumPeak)
    }

    @Test
    fun `absent replayGain leaves nulls`() {
        val song = JSONObject("""{"id":"s2","title":"T","duration":100}""")
        val track = SubsonicMappers.parseSong(song, 1, "http://x?id=s2", null)
        assertNull(track.trackGainDb)
    }

    @Test
    fun `track mode attenuates by the track gain`() {
        val info = ReplayGainInfo(trackGainDb = -6.0, albumGainDb = 0.0)
        // 10^(-6/20) ≈ 0.501
        assertEquals(0.501f, ReplayGain.volumeFor(info, ReplayGainMode.TRACK, albumContext = false), 0.01f)
        assertEquals(1f, ReplayGain.volumeFor(info, ReplayGainMode.OFF, albumContext = false), 0f)
    }

    @Test
    fun `peak clamp prevents boosting into clipping`() {
        val info = ReplayGainInfo(trackGainDb = 6.0, trackPeak = 0.9) // +6dB would clip
        val v = ReplayGain.volumeFor(info, ReplayGainMode.TRACK, albumContext = false)
        assertTrue("volume must not exceed 1/peak", v <= (1.0f / 0.9f) + 0.001f)
    }
}
