package com.micasong.player.audio

import com.micasong.player.data.audio.FadeCurve
import org.junit.Assert.assertEquals
import org.junit.Test

class FadeCurveTest {

    @Test
    fun `linear is identity for gain in`() {
        assertEquals(0f, FadeCurve.LINEAR.gainIn(0f), 1e-6f)
        assertEquals(0.5f, FadeCurve.LINEAR.gainIn(0.5f), 1e-6f)
        assertEquals(1f, FadeCurve.LINEAR.gainIn(1f), 1e-6f)
    }

    @Test
    fun `gain out is the mirror of gain in`() {
        for (curve in listOf(FadeCurve.LINEAR, FadeCurve.SMOOTH, FadeCurve.BUNGEE)) {
            assertEquals(curve.gainIn(0.7f), curve.gainOut(0.3f), 1e-6f)
            assertEquals(1f, curve.gainOut(0f), 1e-6f)
            assertEquals(0f, curve.gainOut(1f), 1e-6f)
        }
    }

    @Test
    fun `disabled is always unity`() {
        assertEquals(1f, FadeCurve.DISABLED.gainIn(0f), 1e-6f)
        assertEquals(1f, FadeCurve.DISABLED.gainIn(0.5f), 1e-6f)
        assertEquals(1f, FadeCurve.DISABLED.gainOut(0.5f), 1e-6f)
    }

    @Test
    fun `flat is an instant step`() {
        assertEquals(0f, FadeCurve.FLAT.gainIn(0f), 1e-6f)
        assertEquals(1f, FadeCurve.FLAT.gainIn(0.01f), 1e-6f)
    }

    @Test
    fun `smooth curve hits its endpoints`() {
        assertEquals(0f, FadeCurve.SMOOTH.gainIn(0f), 1e-6f)
        assertEquals(1f, FadeCurve.SMOOTH.gainIn(1f), 1e-6f)
        assertEquals(0.5f, FadeCurve.SMOOTH.gainIn(0.5f), 1e-3f)
    }

    @Test
    fun `bungee starts slow`() {
        // quadratic: at 0.5 → 0.25, clearly below linear
        assertEquals(0.25f, FadeCurve.BUNGEE.gainIn(0.5f), 1e-6f)
    }
}
