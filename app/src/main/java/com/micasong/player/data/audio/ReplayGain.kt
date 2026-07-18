package com.micasong.player.data.audio

import kotlin.math.pow

/** ReplayGain application mode (spec §15). */
enum class ReplayGainMode { OFF, TRACK, ALBUM, AUTO }

/** Gain values parsed from a track's tags, in dB. Any field may be absent. */
data class ReplayGainInfo(
    val trackGainDb: Double? = null,
    val albumGainDb: Double? = null,
    val trackPeak: Double? = null,
    val albumPeak: Double? = null,
)

/**
 * ReplayGain support (spec §15). Reads `REPLAYGAIN_*` and R128 (`R128_*`) tags and converts the
 * chosen gain into a linear volume multiplier ExoPlayer can apply. In AUTO mode, album gain is
 * used for album playback and track gain for shuffled/playlist playback — matching the spec.
 * A peak-aware clamp prevents clipping.
 */
object ReplayGain {

    /** R128 gain is stored in Q7.8 fixed-point referenced to -23 LUFS; normalise toward -18 LUFS. */
    private const val R128_REFERENCE_OFFSET_DB = 5.0

    fun fromTags(tags: Map<String, String>): ReplayGainInfo {
        val lower = tags.mapKeys { it.key.lowercase() }
        fun db(vararg keys: String): Double? =
            keys.firstNotNullOfOrNull { lower[it]?.replace("dB", "", ignoreCase = true)?.trim()?.toDoubleOrNull() }
        fun r128(key: String): Double? =
            lower[key]?.trim()?.toDoubleOrNull()?.let { it / 256.0 + R128_REFERENCE_OFFSET_DB }

        return ReplayGainInfo(
            trackGainDb = db("replaygain_track_gain") ?: r128("r128_track_gain"),
            albumGainDb = db("replaygain_album_gain") ?: r128("r128_album_gain"),
            trackPeak = db("replaygain_track_peak"),
            albumPeak = db("replaygain_album_peak"),
        )
    }

    /**
     * Compute the linear volume multiplier (0f..1f range around 1.0) for the given mode.
     * Returns 1.0 when the required gain is unavailable (no attenuation/boost).
     */
    fun volumeFor(info: ReplayGainInfo, mode: ReplayGainMode, albumContext: Boolean): Float {
        val gainDb = when (mode) {
            ReplayGainMode.OFF -> return 1f
            ReplayGainMode.TRACK -> info.trackGainDb
            ReplayGainMode.ALBUM -> info.albumGainDb
            ReplayGainMode.AUTO -> if (albumContext) info.albumGainDb ?: info.trackGainDb else info.trackGainDb
        } ?: return 1f

        var volume = 10.0.pow(gainDb / 20.0)
        // Clip protection using the relevant peak, if known.
        val peak = if (mode == ReplayGainMode.ALBUM || (mode == ReplayGainMode.AUTO && albumContext))
            info.albumPeak ?: info.trackPeak else info.trackPeak
        if (peak != null && peak > 0.0 && volume * peak > 1.0) {
            volume = 1.0 / peak
        }
        return volume.coerceIn(0.0, 1.0).toFloat()
    }
}
