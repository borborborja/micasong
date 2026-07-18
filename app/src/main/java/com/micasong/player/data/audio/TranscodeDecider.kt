package com.micasong.player.data.audio

/** Connection kind that gates the bitrate budget (spec §11, §35). */
enum class NetworkType { WIFI, MOBILE, METERED_WIFI, OFFLINE }

/** Where audio is being routed — affects whether/how to transcode (spec §12, §36). */
enum class CastTarget { LOCAL, CHROMECAST, UPNP }

/** Per-connection transcode settings (spec §11: separate mobile/Wi-Fi max bitrate, 0 = Original). */
data class TranscodeSettings(
    val maxBitrateWifiKbps: Int = 0,
    val maxBitrateMobileKbps: Int = 0,
    val forceInstant: Boolean = false,
)

/** The outcome: whether to transcode and to what target. */
data class TranscodeDecision(
    val transcode: Boolean,
    val targetBitrateKbps: Int,   // 0 = original / passthrough
    val targetFormat: String?,    // e.g. "opus", "aac", or null for passthrough
)

/**
 * Decides transcoding for a stream (spec §11-12). It honours the per-connection bitrate cap
 * (mobile vs Wi-Fi, "Original" = 0 meaning no cap), leaves already-within-budget sources
 * untouched, and always transcodes Chromecast to AAC. Pure and unit-testable; the actual
 * FFmpeg/server-side transcode is wired into the provider's stream URL.
 */
object TranscodeDecider {

    fun decide(
        sourceBitrateKbps: Int?,
        network: NetworkType,
        settings: TranscodeSettings,
        castTarget: CastTarget = CastTarget.LOCAL,
    ): TranscodeDecision {
        val maxKbps = when (network) {
            NetworkType.WIFI -> settings.maxBitrateWifiKbps
            NetworkType.MOBILE, NetworkType.METERED_WIFI -> settings.maxBitrateMobileKbps
            NetworkType.OFFLINE -> 0
        }

        // Chromecast always transcodes to AAC (spec §12), capped by the budget if one is set.
        if (castTarget == CastTarget.CHROMECAST) {
            return TranscodeDecision(true, if (maxKbps > 0) maxKbps else 256, "aac")
        }

        // "Original" (no cap) and not forced → passthrough.
        if (maxKbps <= 0 && !settings.forceInstant) return TranscodeDecision(false, 0, null)

        // Source already within budget → passthrough (unless the user forces transcoding).
        if (!settings.forceInstant && maxKbps > 0 && sourceBitrateKbps != null && sourceBitrateKbps <= maxKbps) {
            return TranscodeDecision(false, 0, null)
        }

        val target = if (maxKbps > 0) maxKbps else 320   // forced with no cap → sane ceiling
        return TranscodeDecision(true, target, "opus")
    }

    /** Map the broadcast API's `INT_PARAMETER` transcode value to kbps (spec §42). */
    fun bitrateForApiParam(param: Int): Int = when (param) {
        -1 -> 0      // no transcode / original
        1 -> 64
        2 -> 96
        3 -> 128
        4 -> 160
        5 -> 192
        6 -> 256
        7 -> 320
        else -> 0
    }
}
