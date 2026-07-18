package com.micasong.player.audio

import com.micasong.player.data.audio.AudioFormat
import com.micasong.player.data.audio.DacCapabilities
import com.micasong.player.data.audio.DsdMode
import com.micasong.player.data.audio.OutputNegotiator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OutputNegotiatorTest {

    private val hiResDac = DacCapabilities(maxPcmSampleRate = 384_000, maxBitDepth = 32, supportsNativeDsd = true, supportsDoP = true, exclusiveMode = true)

    @Test
    fun `cd audio to a hi-res dac is bit-perfect`() {
        val out = OutputNegotiator.negotiate(AudioFormat(44_100, 16), hiResDac)
        assertEquals(44_100, out.sampleRate)
        assertEquals(16, out.bitDepth)
        assertTrue(out.bitPerfect)
        assertFalse(out.resampled)
        assertEquals(DsdMode.NONE, out.dsdMode)
    }

    @Test
    fun `hi-res beyond dac max is downsampled`() {
        val dac = DacCapabilities(maxPcmSampleRate = 96_000, maxBitDepth = 24)
        val out = OutputNegotiator.negotiate(AudioFormat(192_000, 24), dac)
        assertEquals(96_000, out.sampleRate)
        assertTrue(out.resampled)
        assertFalse(out.bitPerfect)
    }

    @Test
    fun `24-bit source into a 16-bit dac loses bit-perfection`() {
        val dac = DacCapabilities(maxPcmSampleRate = 48_000, maxBitDepth = 16)
        val out = OutputNegotiator.negotiate(AudioFormat(48_000, 24), dac)
        assertEquals(16, out.bitDepth)
        assertFalse(out.bitPerfect)
    }

    @Test
    fun `dsd plays natively on a native-dsd dac`() {
        val out = OutputNegotiator.negotiate(AudioFormat(0, 1, isDsd = true, dsdRate = 2_822_400), hiResDac)
        assertEquals(DsdMode.NATIVE, out.dsdMode)
        assertTrue(out.bitPerfect)
        assertEquals(2_822_400, out.sampleRate)
    }

    @Test
    fun `dsd falls back to DoP when native is unavailable`() {
        val dac = DacCapabilities(maxPcmSampleRate = 192_000, maxBitDepth = 24, supportsNativeDsd = false, supportsDoP = true)
        val out = OutputNegotiator.negotiate(AudioFormat(0, 1, isDsd = true, dsdRate = 2_822_400), dac)
        assertEquals(DsdMode.DOP, out.dsdMode)
        assertEquals(176_400, out.sampleRate)   // dsdRate / 16
        assertEquals(24, out.bitDepth)
    }

    @Test
    fun `dsd decodes to pcm when the dac has no dsd path`() {
        val dac = DacCapabilities(maxPcmSampleRate = 96_000, maxBitDepth = 24)
        val out = OutputNegotiator.negotiate(AudioFormat(0, 1, isDsd = true, dsdRate = 2_822_400), dac)
        assertEquals(DsdMode.NONE, out.dsdMode)
        assertEquals(96_000, out.sampleRate)    // capped at dac max
        assertTrue(out.resampled)
    }

    @Test
    fun `upsample to max only in exclusive mode`() {
        val out = OutputNegotiator.negotiate(AudioFormat(44_100, 24), hiResDac, upsampleToMax = true)
        assertEquals(384_000, out.sampleRate)
        assertTrue(out.resampled)
        assertFalse(out.bitPerfect)
    }

    @Test
    fun `all to dsd converts pcm on a native-dsd dac`() {
        val out = OutputNegotiator.negotiate(AudioFormat(44_100, 16), hiResDac, allToDsd = true)
        assertEquals(DsdMode.NATIVE, out.dsdMode)
        assertFalse(out.bitPerfect)
    }
}
