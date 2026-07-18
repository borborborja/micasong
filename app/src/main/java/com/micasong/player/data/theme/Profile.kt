package com.micasong.player.data.theme

import com.micasong.player.data.smart.SmartQueueMode
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Global visibility/offline filters carried by a profile (spec §28-bis, §30). */
@Serializable
data class GlobalFilters(
    /** Visible provider ids; empty = all providers. */
    val visibleProviderIds: Set<Long> = emptySet(),
    val offlineOnly: Boolean = false,
    /** Media types to show, e.g. "music" / "audiobook" / "podcast"; empty = all. */
    val mediaTypes: Set<String> = emptySet(),
)

/** Smart Queue configuration carried by a profile (spec §16, §28-bis). */
@Serializable
data class SmartQueueConfig(
    val enabled: Boolean = false,
    val mode: SmartQueueMode = SmartQueueMode.RANDOM,
    val batchSize: Int = 20,
)

/**
 * A profile (spec §28-bis) bundles an app style + Smart Queue config + the global filters, so the
 * user can switch "mode" with one tap — e.g. a music mode vs an audiobook mode. Loadable via the
 * API (`load_profile`, §42). Serializable for sharing.
 */
@Serializable
data class Profile(
    val id: String,
    val name: String,
    val appStyle: AppStyle,
    val smartQueue: SmartQueueConfig = SmartQueueConfig(),
    val filters: GlobalFilters = GlobalFilters(),
) {
    val isAudiobookMode: Boolean get() = "audiobook" in filters.mediaTypes
}

object Profiles {

    fun musicMode(): Profile = Profile(
        id = "music",
        name = "Música",
        appStyle = AppStyles.preset("Modern"),
        smartQueue = SmartQueueConfig(enabled = true, mode = SmartQueueMode.ARTIST, batchSize = 20),
        filters = GlobalFilters(mediaTypes = setOf("music")),
    )

    fun audiobookMode(): Profile = Profile(
        id = "audiobook",
        name = "Audiolibros",
        appStyle = AppStyles.preset("Old School Basic"),
        smartQueue = SmartQueueConfig(enabled = false),
        filters = GlobalFilters(mediaTypes = setOf("audiobook")),
    )

    fun defaults(): List<Profile> = listOf(musicMode(), audiobookMode())

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun toJson(profile: Profile): String = json.encodeToString(Profile.serializer(), profile)
    fun fromJson(text: String?): Profile? =
        text?.takeIf { it.isNotBlank() }?.let { runCatching { json.decodeFromString(Profile.serializer(), it) }.getOrNull() }
}
