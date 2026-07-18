package com.micasong.player.data.audio

import kotlinx.serialization.Serializable

/** Output devices the equalizer can auto-switch between (spec §14 "per output device"). */
@Serializable
enum class OutputType { PHONE_SPEAKER, WIRED_HEADPHONES, BLUETOOTH, USB_DAC, CAST }

/** One graphic-EQ band. */
@Serializable
data class EqBand(val freqHz: Int, val gainDb: Float)

/**
 * A named equalizer profile (spec §14): a preamp, a set of GEQ bands, bass-boost and virtualizer
 * strengths, and the output devices it auto-applies to. Serializable so it can be saved/loaded.
 */
@Serializable
data class EqProfile(
    val id: String,
    val name: String,
    val enabled: Boolean = false,
    val preampDb: Float = 0f,
    val bands: List<EqBand> = EqPresets.flat(10),
    val bassBoost: Int = 0,        // 0..1000
    val virtualizer: Int = 0,      // 0..1000
    val outputTypes: Set<OutputType> = emptySet(),
) {
    /** Interpolatable view of the band curve, for resampling onto the device's fixed bands. */
    fun asGraphicCurve(): GraphicEqProfile =
        GraphicEqProfile(bands.map { EqPoint(it.freqHz.toDouble(), it.gainDb.toDouble()) })
}

/** Standard band frequencies and named presets (spec §14 GEQ 5/10/15/31). */
object EqPresets {

    private val FREQ_5 = listOf(60, 230, 910, 3600, 14000)
    private val FREQ_10 = listOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)
    private val FREQ_15 = listOf(25, 40, 63, 100, 160, 250, 400, 630, 1000, 1600, 2500, 4000, 6300, 10000, 16000)
    private val FREQ_31 = listOf(
        20, 25, 31, 40, 50, 63, 80, 100, 125, 160, 200, 250, 315, 400, 500, 630,
        800, 1000, 1250, 1600, 2000, 2500, 3150, 4000, 5000, 6300, 8000, 10000, 12500, 16000, 20000,
    )

    /** Supported band counts (spec §14). */
    val BAND_COUNTS = listOf(5, 10, 15, 31)

    fun frequencies(count: Int): List<Int> = when (count) {
        5 -> FREQ_5
        15 -> FREQ_15
        31 -> FREQ_31
        else -> FREQ_10
    }

    fun flat(count: Int): List<EqBand> = frequencies(count).map { EqBand(it, 0f) }

    val NAMES = listOf("Flat", "Rock", "Pop", "Jazz", "Classical", "Bass Boost", "Treble Boost", "Vocal")

    /** A preset is defined as a frequency→gain curve, then resampled onto the requested band count. */
    fun preset(name: String, count: Int): List<EqBand> {
        val curve = curveFor(name)
        val freqs = frequencies(count)
        return freqs.map { EqBand(it, curve.gainAt(it.toDouble()).toFloat()) }
    }

    private fun curveFor(name: String): GraphicEqProfile {
        val points = when (name) {
            "Rock" -> listOf(20 to 4.0, 80 to 3.0, 250 to -1.0, 1000 to 0.5, 4000 to 2.5, 16000 to 3.5)
            "Pop" -> listOf(20 to -1.0, 250 to 2.0, 1000 to 3.0, 4000 to 1.5, 16000 to -1.0)
            "Jazz" -> listOf(20 to 3.0, 250 to 1.0, 1000 to -1.0, 4000 to 1.0, 16000 to 3.0)
            "Classical" -> listOf(20 to 3.0, 500 to 0.0, 4000 to 0.0, 10000 to 2.0, 16000 to 3.0)
            "Bass Boost" -> listOf(20 to 6.0, 60 to 5.0, 150 to 3.0, 400 to 0.0, 16000 to 0.0)
            "Treble Boost" -> listOf(20 to 0.0, 2000 to 0.0, 6000 to 3.0, 16000 to 6.0)
            "Vocal" -> listOf(20 to -2.0, 250 to 0.0, 1000 to 3.0, 3000 to 3.0, 8000 to 0.0, 16000 to -2.0)
            else -> listOf(20 to 0.0, 16000 to 0.0)   // Flat
        }
        return GraphicEqProfile(points.map { EqPoint(it.first.toDouble(), it.second) })
    }
}

/** Chooses which profile to apply for the current output device (spec §14 auto-switch). */
object EqRouter {
    /**
     * Prefer an enabled profile bound to the current [output]; otherwise fall back to the profile
     * with [defaultId]; otherwise null (no EQ).
     */
    fun profileFor(profiles: List<EqProfile>, output: OutputType, defaultId: String? = null): EqProfile? {
        profiles.firstOrNull { it.enabled && output in it.outputTypes }?.let { return it }
        return profiles.firstOrNull { it.id == defaultId && it.enabled }
    }
}
