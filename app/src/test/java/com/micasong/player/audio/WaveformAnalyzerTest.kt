package com.micasong.player.audio

import com.micasong.player.data.audio.WaveformAnalyzer
import com.micasong.player.data.audio.WaveformStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WaveformAnalyzerTest {

    // 100 samples × 10 ms = 1000 ms. Silence [0..4] and [90..99], content [5..89] at 0.5.
    private val envelope = FloatArray(100) { if (it in 5..89) 0.5f else 0f }
    private val msPerSample = 10.0

    @Test
    fun `detects leading and trailing silence`() {
        val stats = WaveformAnalyzer.analyze(envelope, msPerSample)
        assertEquals(1000L, stats.durationMs)
        assertEquals(50L, stats.leadingSilenceMs)     // first loud at index 5
        assertEquals(100L, stats.trailingSilenceMs)   // last loud at index 89 → 10 samples of tail
        assertEquals(0.5f, stats.peak, 1e-6f)
    }

    @Test
    fun `fully silent waveform reported as silent`() {
        val stats = WaveformAnalyzer.analyze(FloatArray(50), msPerSample)
        assertTrue(stats.isFullySilent)
        assertEquals(500L, stats.leadingSilenceMs)
        assertEquals(500L, stats.trailingSilenceMs)
    }

    @Test
    fun `empty envelope is safe`() {
        val stats = WaveformAnalyzer.analyze(FloatArray(0), msPerSample)
        assertEquals(0L, stats.durationMs)
    }

    @Test
    fun `smart fade skips leading silence and starts fade-out before trailing silence`() {
        val stats = WaveformAnalyzer.analyze(envelope, msPerSample)
        val plan = WaveformAnalyzer.smartFade(stats, maxFadeMs = 200)
        assertEquals(50L, plan.skipToMs)             // skip the 50 ms of dead air
        // content ends at 1000 - 100 = 900; fade-out 200 ms → starts at 700
        assertEquals(700L, plan.fadeOutStartMs)
        assertEquals(200L, plan.fadeOutDurationMs)
        assertEquals(200L, plan.fadeInDurationMs)
    }

    @Test
    fun `fade duration is capped by content length`() {
        // Short content: only 100 ms of music.
        val stats = WaveformStats(durationMs = 1000, leadingSilenceMs = 450, trailingSilenceMs = 450, peak = 0.5f)
        val plan = WaveformAnalyzer.smartFade(stats, maxFadeMs = 500)
        assertEquals(100L, plan.fadeOutDurationMs)   // clamped to the 100 ms of content
        assertEquals(450L, plan.fadeOutStartMs)      // contentEnd(550) - 100
    }

    @Test
    fun `custom threshold ignores low-level noise`() {
        val noisy = FloatArray(20) { if (it in 5..14) 0.5f else 0.01f }   // 0.01 background hiss
        val stats = WaveformAnalyzer.analyze(noisy, msPerSample, silenceThreshold = 0.05f)
        assertEquals(50L, stats.leadingSilenceMs)    // hiss below threshold treated as silence
    }
}
