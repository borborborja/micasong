package com.micasong.player.data.render

/**
 * Output renderer types (spec §36). `apiType` matches the broadcast API `SELECT_RENDERER` TYPE
 * enum (0 Local, 1 UPnP, 3 Chromecast, 6 Remote Media Center, §42); Kodi/Plex/Sonos are extra
 * cast destinations without a fixed API code.
 */
enum class RendererType(val apiType: Int?) {
    LOCAL(0),
    UPNP(1),
    CHROMECAST(3),
    REMOTE_MEDIA_CENTER(6),
    KODI(null),
    PLEX(null),
    SONOS(null);

    /** EQ / DSP / crossfade apply only to local playback — never when casting (spec §14). */
    val appliesLocalDsp: Boolean get() = this == LOCAL

    /** Renderers whose volume is controlled remotely (per-room for Sonos) (spec §36). */
    val hasRemoteVolume: Boolean get() = this == SONOS || this == CHROMECAST || this == UPNP
}

data class Renderer(
    val id: String,
    val name: String,
    val type: RendererType,
    val supportsGapless: Boolean = false,
)

/**
 * Immutable registry of the available renderers and which one is active (spec §36). Handles the
 * broadcast `SELECT_RENDERER` action and the "automatic renderer reset" behaviour — if the active
 * renderer drops off the network, playback falls back to the local device (spec §11).
 */
data class RendererRegistry(
    val available: List<Renderer>,
    val activeId: String?,
) {
    val active: Renderer?
        get() = available.firstOrNull { it.id == activeId } ?: local

    private val local: Renderer? get() = available.firstOrNull { it.type == RendererType.LOCAL }

    val dspApplies: Boolean get() = active?.type?.appliesLocalDsp == true

    fun select(id: String): RendererRegistry =
        if (available.any { it.id == id }) copy(activeId = id) else this

    /** Update the discovered renderer list; reset to local if the active one vanished (spec §11). */
    fun updateAvailable(newList: List<Renderer>): RendererRegistry {
        val stillPresent = newList.any { it.id == activeId }
        return copy(
            available = newList,
            activeId = if (stillPresent) activeId else newList.firstOrNull { it.type == RendererType.LOCAL }?.id,
        )
    }

    /** Resolve a broadcast `SELECT_RENDERER` request by API TYPE (+ optional identifier), spec §42. */
    fun selectByApiType(apiType: Int, identifier: String? = null): RendererRegistry {
        val match = available.firstOrNull {
            it.type.apiType == apiType && (identifier == null || it.id == identifier)
        } ?: return this
        return copy(activeId = match.id)
    }

    companion object {
        fun withLocalOnly(localName: String = "Este dispositivo"): RendererRegistry {
            val local = Renderer("local", localName, RendererType.LOCAL, supportsGapless = true)
            return RendererRegistry(listOf(local), local.id)
        }
    }
}
