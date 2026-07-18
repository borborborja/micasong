package com.micasong.player.data.audio

import kotlin.math.abs

/** Summary of a track's amplitude envelope (spec §13 smart fades). */
data class WaveformStats(
    val durationMs: Long,
    val leadingSilenceMs: Long,
    val trailingSilenceMs: Long,
    val peak: Float,
) {
    val isFullySilent: Boolean get() = peak <= 0f
}

/** Where to skip to and how to fade this track for a seamless musical transition. */
data class SmartFadePlan(
    val skipToMs: Long,          // skip leading silence
    val fadeInDurationMs: Long,
    val fadeOutStartMs: Long,    // begin fading out here (before the trailing silence)
    val fadeOutDurationMs: Long,
)

/**
 * Waveform analysis for Smart Fades (spec §13). Operates on a downsampled amplitude envelope
 * (normalised 0..1) rather than raw PCM, which is what the waveform-extraction step produces.
 * It locates leading/trailing silence so playback can skip dead air and start the fade-out just
 * before the music ends, overlapping the next track. Pure and unit-testable.
 */
object WaveformAnalyzer {

    fun analyze(envelope: FloatArray, msPerSample: Double, silenceThreshold: Float = 0.02f): WaveformStats {
        if (envelope.isEmpty()) return WaveformStats(0, 0, 0, 0f)
        val durationMs = (envelope.size * msPerSample).toLong()

        var peak = 0f
        var firstLoud = -1
        var lastLoud = -1
        for (i in envelope.indices) {
            val a = abs(envelope[i])
            if (a > peak) peak = a
            if (a > silenceThreshold) {
                if (firstLoud < 0) firstLoud = i
                lastLoud = i
            }
        }
        if (firstLoud < 0) return WaveformStats(durationMs, durationMs, durationMs, peak)

        val leading = (firstLoud * msPerSample).toLong()
        val trailing = ((envelope.size - 1 - lastLoud) * msPerSample).toLong()
        return WaveformStats(durationMs, leading, trailing, peak)
    }

    fun smartFade(stats: WaveformStats, maxFadeMs: Long): SmartFadePlan {
        if (stats.isFullySilent) return SmartFadePlan(0, 0, stats.durationMs, 0)
        val contentEnd = (stats.durationMs - stats.trailingSilenceMs).coerceAtLeast(0)
        val contentLength = (contentEnd - stats.leadingSilenceMs).coerceAtLeast(0)
        val fadeOutDur = maxFadeMs.coerceAtMost(contentLength)
        val fadeOutStart = (contentEnd - fadeOutDur).coerceAtLeast(0)
        val fadeInDur = maxFadeMs.coerceAtMost(contentLength)
        return SmartFadePlan(
            skipToMs = stats.leadingSilenceMs,
            fadeInDurationMs = fadeInDur,
            fadeOutStartMs = fadeOutStart,
            fadeOutDurationMs = fadeOutDur,
        )
    }
}
