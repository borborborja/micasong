package com.micasong.player.data.audio

import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.PI

/**
 * Fade curves for crossfade / fade-in / fade-out transitions (spec §13). Each curve maps a
 * progress fraction (0f at the start of the fade → 1f at the end) to a linear gain multiplier.
 * `gainIn` rises 0→1; `gainOut` is its mirror (1→0) so a fade-out and the paired fade-in end
 * together, as the spec requires.
 */
enum class FadeCurve {
    LINEAR, SMOOTH, BUNGEE, FLAT, DISABLED;

    /** Rising gain (fade-in) at [fraction] in 0f..1f. */
    fun gainIn(fraction: Float): Float {
        val f = fraction.coerceIn(0f, 1f)
        return when (this) {
            DISABLED -> 1f
            LINEAR -> f
            SMOOTH -> (0.5f * (1f - kotlin.math.cos(PI.toFloat() * f)))     // raised cosine (equal-power-ish)
            BUNGEE -> f.toDouble().pow(2.0).toFloat()                       // slow start, fast finish
            FLAT -> if (f <= 0f) 0f else 1f                                 // instant, no fade
        }
    }

    /** Falling gain (fade-out): the complement of [gainIn] so the pair sums to a smooth handover. */
    fun gainOut(fraction: Float): Float {
        if (this == DISABLED) return 1f
        return gainIn(1f - fraction.coerceIn(0f, 1f))
    }
}

/** Convenience wrapper for a "sin" equal-power curve if a caller wants it explicitly. */
fun equalPowerGain(fraction: Float): Float =
    sin(0.5 * PI * fraction.coerceIn(0f, 1f)).toFloat()
