package com.micasong.player.data.wear

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Wear OS companion configuration (spec §39). Streaming goes through the phone proxy (with
 * transcode to save battery); tracks can be downloaded to the watch for phone-free playback,
 * and "secret mode" plays on the watch speaker. Serializable so it syncs to the watch.
 */
@Serializable
data class WearConfig(
    val downloadQualityKbps: Int = 128,
    val streamingQualityKbps: Int = 96,
    val wifiOnlyDownloads: Boolean = true,
    val secretMode: Boolean = false,        // play on the watch speaker
    val autoReturnHome: Boolean = true,
    val tileEnabled: Boolean = true,
    val rotaryVolume: Boolean = true,
    val speakerPlayback: Boolean = false,   // developer option
    val disableOffload: Boolean = false,    // developer option
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        fun toJson(config: WearConfig): String = json.encodeToString(serializer(), config)
        fun fromJson(text: String?): WearConfig =
            text?.takeIf { it.isNotBlank() }?.let { runCatching { json.decodeFromString(serializer(), it) }.getOrNull() }
                ?: WearConfig()
    }
}
