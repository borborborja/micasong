package com.micasong.player.audio

import com.micasong.player.data.audio.EqPresets
import com.micasong.player.data.audio.EqProfile
import com.micasong.player.data.audio.EqRouter
import com.micasong.player.data.audio.OutputType
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EqualizerModelTest {

    @Test
    fun `band counts have the right number of frequencies`() {
        assertEquals(5, EqPresets.frequencies(5).size)
        assertEquals(10, EqPresets.frequencies(10).size)
        assertEquals(15, EqPresets.frequencies(15).size)
        assertEquals(31, EqPresets.frequencies(31).size)
    }

    @Test
    fun `flat preset is all zero`() {
        assertTrue(EqPresets.flat(10).all { it.gainDb == 0f })
    }

    @Test
    fun `bass boost lifts low frequencies and leaves highs flat`() {
        val bands = EqPresets.preset("Bass Boost", 10)
        val low = bands.first { it.freqHz == 31 }.gainDb
        val high = bands.first { it.freqHz == 16000 }.gainDb
        assertTrue("low should be boosted", low > 3f)
        assertTrue("high should be near flat", high in -0.5f..0.5f)
    }

    @Test
    fun `preset resamples to any band count`() {
        assertEquals(31, EqPresets.preset("Rock", 31).size)
        assertEquals(5, EqPresets.preset("Rock", 5).size)
    }

    @Test
    fun `router prefers profile bound to the current output`() {
        val phone = EqProfile("p", "Phone", enabled = true, outputTypes = setOf(OutputType.PHONE_SPEAKER))
        val bt = EqProfile("b", "Headphones", enabled = true, outputTypes = setOf(OutputType.BLUETOOTH))
        assertEquals("b", EqRouter.profileFor(listOf(phone, bt), OutputType.BLUETOOTH)?.id)
        assertEquals("p", EqRouter.profileFor(listOf(phone, bt), OutputType.PHONE_SPEAKER)?.id)
    }

    @Test
    fun `router falls back to default id`() {
        val def = EqProfile("d", "Default", enabled = true, outputTypes = emptySet())
        assertEquals("d", EqRouter.profileFor(listOf(def), OutputType.USB_DAC, defaultId = "d")?.id)
    }

    @Test
    fun `router returns null when nothing matches`() {
        val disabled = EqProfile("x", "Off", enabled = false, outputTypes = setOf(OutputType.CAST))
        assertNull(EqRouter.profileFor(listOf(disabled), OutputType.CAST))
    }

    @Test
    fun `profile serializes round trip`() {
        val profile = EqProfile(
            id = "main", name = "Main", enabled = true, preampDb = -3f,
            bands = EqPresets.preset("Rock", 10), bassBoost = 200,
            outputTypes = setOf(OutputType.WIRED_HEADPHONES),
        )
        val json = Json.encodeToString(EqProfile.serializer(), profile)
        assertEquals(profile, Json.decodeFromString(EqProfile.serializer(), json))
    }
}
