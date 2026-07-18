package com.micasong.player.api

/**
 * Typed model of the broadcast automation API (spec §42), decoupled from Android's `Intent` so
 * the parsing is pure and unit-testable. [ApiReceiver] adapts a real Intent into an [ApiExtras]
 * and dispatches the resulting [ApiCommand].
 */
sealed interface ApiCommand {
    data class SelectRenderer(val type: Int, val identifier: String?) : ApiCommand
    data class MediaSync(val providerId: Int?) : ApiCommand
    data class MediaControl(val command: String, val intParam: Int?) : ApiCommand
    data class MediaStart(
        val mediaType: String,
        val name: String?,
        val album: String?,
        val artist: String?,
        val id: String?,
        val file: String?,
        val resume: Boolean,
        val shuffle: Boolean,
        val queue: Int,
    ) : ApiCommand
    data class ChangeSetting(val setting: String, val intParam: Int?) : ApiCommand
    data class PlaylistImport(val providerId: Int?, val playlistType: Int?) : ApiCommand
    data class CustomAction(val action: String, val providerId: Int?, val activeConnection: Int?) : ApiCommand
    data object Unknown : ApiCommand
}

/** Read-only accessor for command extras — backed by a Bundle at runtime, a Map in tests. */
interface ApiExtras {
    fun string(key: String): String?
    fun int(key: String): Int?
    fun bool(key: String, default: Boolean = false): Boolean
}

/** Map-backed extras for unit tests. */
class MapExtras(private val map: Map<String, Any?>) : ApiExtras {
    override fun string(key: String): String? = map[key] as? String
    override fun int(key: String): Int? = (map[key] as? Number)?.toInt()
    override fun bool(key: String, default: Boolean): Boolean = (map[key] as? Boolean) ?: default
}

object ApiCommandParser {

    const val PREFIX = "com.micasong.api"

    fun parse(action: String?, extras: ApiExtras): ApiCommand {
        val name = action?.removePrefix("$PREFIX.") ?: return ApiCommand.Unknown
        return when (name) {
            "SELECT_RENDERER" -> ApiCommand.SelectRenderer(
                type = extras.int("TYPE") ?: 0,
                identifier = extras.string("IDENTIFIER"),
            )
            "MEDIA_SYNC" -> ApiCommand.MediaSync(extras.int("PROVIDER_ID"))
            "MEDIA_COMMAND" -> {
                val command = extras.string("COMMAND") ?: return ApiCommand.Unknown
                ApiCommand.MediaControl(command, extras.int("INT_PARAMETER"))
            }
            "MEDIA_START" -> {
                val mediaType = extras.string("MEDIA_TYPE") ?: return ApiCommand.Unknown
                ApiCommand.MediaStart(
                    mediaType = mediaType,
                    name = extras.string("NAME"),
                    album = extras.string("ALBUM"),
                    artist = extras.string("ARTIST"),
                    id = extras.string("ID"),
                    file = extras.string("FILE"),
                    resume = extras.bool("RESUME"),
                    shuffle = extras.bool("SHUFFLE"),
                    queue = extras.int("QUEUE") ?: 0,
                )
            }
            "CHANGE_SETTINGS" -> {
                val setting = extras.string("SETTING") ?: return ApiCommand.Unknown
                ApiCommand.ChangeSetting(setting, extras.int("INT_PARAMETER"))
            }
            "PLAYLIST_IMPORT" -> ApiCommand.PlaylistImport(extras.int("PROVIDER_ID"), extras.int("PLAYLIST_TYPE"))
            "CUSTOM_ACTION" -> {
                val custom = extras.string("ACTION") ?: return ApiCommand.Unknown
                ApiCommand.CustomAction(
                    action = custom,
                    providerId = extras.int("PROVIDER_ID"),
                    activeConnection = extras.int("ACTIVE_CONNECTION"),
                )
            }
            else -> ApiCommand.Unknown
        }
    }
}
