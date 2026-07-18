package com.micasong.player.data.provider

import com.micasong.player.data.db.AlbumEntity
import com.micasong.player.data.db.ArtistEntity
import com.micasong.player.data.db.GenreEntity
import com.micasong.player.data.db.TrackEntity

/** The kinds of backend MiCaSong can aggregate (spec §4). */
enum class ProviderType {
    LOCAL,          // Device folders (SAF) / MediaStore
    SUBSONIC,       // Subsonic / OpenSubsonic / Navidrome / Gonic
    JELLYFIN,
    PLEX,
    EMBY,
    KODI,
    AUDIOBOOKSHELF,
    SMB,
    WEBDAV,
    CLOUD,          // Google Drive / Dropbox / OneDrive / Box / pCloud
}

/**
 * Per-provider capability matrix (spec §6). Fields a provider cannot supply are surfaced as
 * `false`, and the UI degrades accordingly (e.g. hide the "mood" node for Subsonic). Keeping
 * this explicit — rather than assuming every backend is equal — is central to the design.
 */
data class ProviderCapabilities(
    val multiArtist: Boolean = false,
    val multiGenre: Boolean = false,
    val albumMood: Boolean = false,
    val albumStyle: Boolean = false,
    val tagsCollections: Boolean = false,
    val songBpm: Boolean = false,
    val albumType: Boolean = false,
    val composers: Boolean = false,
    val languages: Boolean = false,
    val playlistImport: Boolean = false,
    val playlistPush: Boolean = false,
    val serverTranscoding: Boolean = false,
    val ratings: Boolean = false,
    val similarArtists: Boolean = false,   // radio / Smart Flow sonic modes (spec §16, §17)
)

/** User-facing connection config for a provider instance. Secondary = failover (spec §5.1). */
data class ProviderConfig(
    val id: Long,
    val type: ProviderType,
    val displayName: String,
    val primaryUrl: String? = null,
    val secondaryUrl: String? = null,
    val username: String? = null,
    val secret: String? = null,          // password / API key / token
    val wifiOnly: Boolean = false,
    val enabled: Boolean = true,
    val maxBitrateMobile: Int = 0,       // 0 = Original
    val maxBitrateWifi: Int = 0,
)

/** Everything a full/differential sync produced, ready to be dumped into Room. */
data class ProviderSnapshot(
    val tracks: List<TrackEntity>,
    val albums: List<AlbumEntity>,
    val artists: List<ArtistEntity>,
    val genres: List<GenreEntity>,
)

/**
 * Common interface every backend connector implements (spec §2). Concrete providers
 * translate their backend's model into MiCaSong's unified entities. Streaming URL resolution
 * is separated from sync so playback can honour per-connection bitrate/transcode settings.
 */
interface MediaProvider {
    val config: ProviderConfig
    val capabilities: ProviderCapabilities

    /** Full metadata dump. Differential sync is an optimization layered on top (spec §9). */
    suspend fun sync(onProgress: (Float, String) -> Unit = { _, _ -> }): ProviderSnapshot

    /**
     * Resolve a playable URL for a track. For local providers this is the content URI; for
     * servers it is a `stream` endpoint carrying the negotiated `maxBitRate`/`format`.
     */
    suspend fun streamUri(track: TrackEntity, maxBitrate: Int): String

    /**
     * Raw lyrics for a track (LRC text if synced, plain text otherwise), or null if the backend
     * has none (spec §41). Default: unsupported.
     */
    suspend fun lyrics(track: TrackEntity): String? = null

    /** Report a completed play to the backend (scrobble, spec §9/§47). Default: no-op. */
    suspend fun scrobble(track: TrackEntity) = Unit
}
