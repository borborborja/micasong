package com.micasong.player.data.audio

/** DSD delivery mode to the DAC (spec §11-bis). */
enum class DsdMode { NONE, NATIVE, DOP }

/** A source track's audio format. For DSD, [dsdRate] is the 1-bit rate (e.g. 2_822_400 = DSD64). */
data class AudioFormat(
    val sampleRate: Int,
    val bitDepth: Int,
    val isDsd: Boolean = false,
    val dsdRate: Int = 0,
)

/** What the output device (USB DAC / DAP / phone) can accept (spec §11-bis). */
data class DacCapabilities(
    val maxPcmSampleRate: Int,
    val maxBitDepth: Int,
    val supportsNativeDsd: Boolean = false,
    val supportsDoP: Boolean = false,
    val exclusiveMode: Boolean = false,
)

/** The negotiated output configuration. */
data class OutputConfig(
    val sampleRate: Int,
    val bitDepth: Int,
    val dsdMode: DsdMode,
    val bitPerfect: Boolean,
    val resampled: Boolean,
)

/**
 * Negotiates the output format for bit-perfect / Hi-Res / DSD playback (spec §11-bis). Given the
 * source format and the DAC's capabilities, it decides whether to send audio untouched
 * (bit-perfect), resample it, or deliver DSD natively / over DoP. This is the pure, testable core
 * of the audiophile engine — the actual USB/UAC2 routing is device-specific and out of scope here.
 */
object OutputNegotiator {

    private const val DSD64_RATE = 2_822_400

    fun negotiate(
        source: AudioFormat,
        dac: DacCapabilities,
        allToDsd: Boolean = false,
        upsampleToMax: Boolean = false,
    ): OutputConfig {
        if (source.isDsd) return negotiateDsd(source, dac)

        // "All to DSD" (experimental): convert PCM to DSD when the DAC supports it natively.
        if (allToDsd && dac.supportsNativeDsd) {
            return OutputConfig(DSD64_RATE, 1, DsdMode.NATIVE, bitPerfect = false, resampled = true)
        }

        var targetRate = source.sampleRate
        var resampled = false
        when {
            source.sampleRate > dac.maxPcmSampleRate -> {
                targetRate = dac.maxPcmSampleRate            // downsample to what the DAC accepts
                resampled = true
            }
            upsampleToMax && dac.exclusiveMode && dac.maxPcmSampleRate > source.sampleRate -> {
                targetRate = dac.maxPcmSampleRate            // upsample to the DAC max (exclusive mode)
                resampled = true
            }
        }
        val targetDepth = minOf(source.bitDepth, dac.maxBitDepth)
        val bitPerfect = !resampled && targetDepth == source.bitDepth
        return OutputConfig(targetRate, targetDepth, DsdMode.NONE, bitPerfect, resampled)
    }

    private fun negotiateDsd(source: AudioFormat, dac: DacCapabilities): OutputConfig = when {
        dac.supportsNativeDsd ->
            OutputConfig(source.dsdRate, 1, DsdMode.NATIVE, bitPerfect = true, resampled = false)
        dac.supportsDoP ->
            // DoP wraps 16 DSD bits into a 24-bit PCM frame → PCM rate = dsdRate / 16.
            OutputConfig(source.dsdRate / 16, 24, DsdMode.DOP, bitPerfect = true, resampled = false)
        else -> {
            // No DSD path: decode DSD to PCM, capped at the DAC's max rate.
            val sr = minOf(176_400, dac.maxPcmSampleRate)
            OutputConfig(sr, minOf(24, dac.maxBitDepth), DsdMode.NONE, bitPerfect = false, resampled = true)
        }
    }
}
