package com.micasong.player.data.audio

import kotlin.math.log10

/** A single frequency→gain point of a graphic-EQ curve. */
data class EqPoint(val freqHz: Double, val gainDb: Double)

/**
 * An AutoEQ GraphicEQ curve. Provides log-frequency interpolation so the fine-grained AutoEQ
 * data (often ~127 points) can be resampled onto MiCaSong's fixed GEQ bands (5/10/15/31, §14).
 */
data class GraphicEqProfile(val points: List<EqPoint>) {

    /** Gain (dB) at an arbitrary frequency, linearly interpolated in the log-frequency domain. */
    fun gainAt(freqHz: Double): Double {
        if (points.isEmpty()) return 0.0
        if (freqHz <= points.first().freqHz) return points.first().gainDb
        if (freqHz >= points.last().freqHz) return points.last().gainDb
        val i = points.indexOfLast { it.freqHz <= freqHz }
        val lo = points[i]
        val hi = points[i + 1]
        val t = (log10(freqHz) - log10(lo.freqHz)) / (log10(hi.freqHz) - log10(lo.freqHz))
        return lo.gainDb + t * (hi.gainDb - lo.gainDb)
    }

    /** Resample this curve onto a set of band center frequencies. */
    fun resampleTo(bandFrequencies: List<Int>): List<Double> = bandFrequencies.map { gainAt(it.toDouble()) }
}

/** One EqualizerAPO filter (peaking / shelf) parsed from a config. */
data class ApoFilter(val type: String, val freqHz: Double, val gainDb: Double, val q: Double)

/** An EqualizerAPO profile: a preamp plus a list of filters (spec §14 APO import). */
data class ApoProfile(val preampDb: Double, val filters: List<ApoFilter>)

/**
 * Parser for AutoEQ headphone-correction profiles (spec §14). Supports the two formats the
 * reference app imports: the single-line `GraphicEQ.txt` and EqualizerAPO `config.txt`. Pure
 * and unit-testable; feeding the values into the platform equalizer happens on device.
 */
object AutoEqParser {

    private val APO_FILTER = Regex(
        """Filter\s+\d+:\s+ON\s+(\w+)\s+Fc\s+([\d.]+)\s*Hz\s+Gain\s+(-?[\d.]+)\s*dB\s+Q\s+([\d.]+)""",
        RegexOption.IGNORE_CASE,
    )
    private val APO_PREAMP = Regex("""Preamp:\s*(-?[\d.]+)\s*dB""", RegexOption.IGNORE_CASE)

    fun parseGraphicEq(text: String?): GraphicEqProfile? {
        if (text.isNullOrBlank()) return null
        val body = text.substringAfter(':', text).trim()
        val points = body.split(';').mapNotNull { token ->
            val nums = token.trim().split(Regex("\\s+"))
            if (nums.size < 2) return@mapNotNull null
            val f = nums[0].toDoubleOrNull() ?: return@mapNotNull null
            val g = nums[1].toDoubleOrNull() ?: return@mapNotNull null
            EqPoint(f, g)
        }.sortedBy { it.freqHz }
        return if (points.isEmpty()) null else GraphicEqProfile(points)
    }

    fun parseApo(text: String?): ApoProfile {
        if (text.isNullOrBlank()) return ApoProfile(0.0, emptyList())
        var preamp = 0.0
        val filters = ArrayList<ApoFilter>()
        for (line in text.lineSequence()) {
            APO_PREAMP.find(line)?.let { preamp = it.groupValues[1].toDoubleOrNull() ?: 0.0 }
            APO_FILTER.find(line)?.let { m ->
                filters += ApoFilter(
                    type = m.groupValues[1].uppercase(),
                    freqHz = m.groupValues[2].toDouble(),
                    gainDb = m.groupValues[3].toDouble(),
                    q = m.groupValues[4].toDouble(),
                )
            }
        }
        return ApoProfile(preamp, filters)
    }
}
