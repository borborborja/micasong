package com.micasong.player.data.repository

import android.content.Context
import com.micasong.player.data.db.MusicDao
import com.micasong.player.data.db.PlaylistDao
import com.micasong.player.data.db.ProviderDao
import com.micasong.player.data.db.toConfig
import com.micasong.player.data.db.toDomain
import com.micasong.player.data.db.toEntity
import com.micasong.player.data.model.Album
import com.micasong.player.data.model.Artist
import com.micasong.player.data.model.Genre
import com.micasong.player.data.model.Playlist
import com.micasong.player.data.model.Track
import com.micasong.player.data.provider.JellyfinProvider
import com.micasong.player.data.provider.LocalProvider
import com.micasong.player.data.provider.MediaProvider
import com.micasong.player.data.provider.ProviderConfig
import com.micasong.player.data.provider.ProviderType
import com.micasong.player.data.provider.SubsonicProvider
import com.micasong.player.data.smart.PersonalMixGenerator
import com.micasong.player.data.sync.ServerSyncMerge
import com.micasong.player.data.smart.SmartFlow
import com.micasong.player.data.smart.SmartFlowMode
import com.micasong.player.data.smart.SmartPlaylistDefinition
import com.micasong.player.data.smart.SmartQueueExtender
import com.micasong.player.data.smart.SmartQueueMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Progress state surfaced while a sync runs (spec §9 "Collecting metadata 69%"). */
data class SyncState(val running: Boolean = false, val progress: Float = 0f, val message: String = "")

/**
 * Single source of truth the UI and playback layers talk to. It owns the registered
 * [MediaProvider]s, drives sync into Room, and exposes the unified catalog as domain [Flow]s.
 */
@Singleton
class MediaRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val musicDao: MusicDao,
    private val playlistDao: PlaylistDao,
    private val providerDao: ProviderDao,
) {
    // The local device provider is always present; server providers (Subsonic/Jellyfin/…) are
    // loaded from the database so they survive restarts (spec §4).
    private val localProvider: MediaProvider = LocalProvider(
        context,
        ProviderConfig(id = LOCAL_PROVIDER_ID, type = ProviderType.LOCAL, displayName = "Este dispositivo"),
    )

    /** The configured server providers (local is implicit and not listed here). */
    val providerConfigs: Flow<List<ProviderConfig>> =
        providerDao.all().map { list -> list.map { it.toConfig() } }

    /** Build the full provider set for a sync: local device + every enabled server. */
    private suspend fun buildProviders(): List<MediaProvider> {
        val servers = providerDao.allEnabled().map { entity ->
            val config = entity.toConfig()
            when (config.type) {
                ProviderType.JELLYFIN -> JellyfinProvider(config)
                else -> SubsonicProvider(config)   // Subsonic/OpenSubsonic/Navidrome
            }
        }
        return listOf(localProvider) + servers
    }

    suspend fun addProvider(config: ProviderConfig): Long = providerDao.upsert(config.toEntity())
    suspend fun removeProvider(rowId: Long) = providerDao.delete(rowId)
    suspend fun setActiveConnection(rowId: Long, connection: Int) = providerDao.setActiveConnection(rowId, connection)

    private val _syncState = MutableStateFlow(SyncState())
    val syncState: StateFlow<SyncState> = _syncState

    val trackCount: Flow<Int> = musicDao.trackCount()

    // ---- Catalog reads ----
    val albums: Flow<List<Album>> = musicDao.albums().map { it.map { e -> e.toDomain() } }
    val artists: Flow<List<Artist>> = musicDao.artists().map { it.map { e -> e.toDomain() } }
    val genres: Flow<List<Genre>> = musicDao.genres().map { it.map { e -> e.toDomain() } }
    val allTracks: Flow<List<Track>> = musicDao.allTracks().map { it.map { e -> e.toDomain() } }
    val favoriteAlbums: Flow<List<Album>> = musicDao.favoriteAlbums().map { it.map { e -> e.toDomain() } }
    val favoriteTracks: Flow<List<Track>> = musicDao.favoriteTracks().map { it.map { e -> e.toDomain() } }
    val playlists: Flow<List<Playlist>> = playlistDao.playlists().map { it.map { e -> e.toDomain() } }

    fun recentlyAddedAlbums(limit: Int = 20): Flow<List<Album>> =
        musicDao.recentlyAddedAlbums(limit).map { it.map { e -> e.toDomain() } }

    fun mostPlayedAlbums(limit: Int = 20): Flow<List<Album>> =
        musicDao.mostPlayedAlbums(limit).map { it.map { e -> e.toDomain() } }

    fun recentlyPlayed(limit: Int = 20): Flow<List<Track>> =
        musicDao.recentlyPlayed(limit).map { it.map { e -> e.toDomain() } }

    fun resumable(limit: Int = 20): Flow<List<Track>> =
        musicDao.resumable(limit).map { it.map { e -> e.toDomain() } }

    fun album(id: Long): Flow<Album?> = musicDao.album(id).map { it?.toDomain() }
    fun tracksByAlbum(id: Long): Flow<List<Track>> = musicDao.tracksByAlbum(id).map { it.map { e -> e.toDomain() } }
    fun artist(id: Long): Flow<Artist?> = musicDao.artist(id).map { it?.toDomain() }
    fun albumsByArtist(id: Long): Flow<List<Album>> = musicDao.albumsByArtist(id).map { it.map { e -> e.toDomain() } }
    fun tracksByArtist(id: Long): Flow<List<Track>> = musicDao.tracksByArtist(id).map { it.map { e -> e.toDomain() } }
    fun tracksByGenre(name: String): Flow<List<Track>> = musicDao.tracksByGenre(name).map { it.map { e -> e.toDomain() } }
    fun tracksInPlaylist(id: Long): Flow<List<Track>> = playlistDao.tracksInPlaylist(id).map { it.map { e -> e.toDomain() } }

    // ---- Search ----
    fun searchTracks(q: String): Flow<List<Track>> = musicDao.searchTracks(q).map { it.map { e -> e.toDomain() } }
    fun searchAlbums(q: String): Flow<List<Album>> = musicDao.searchAlbums(q).map { it.map { e -> e.toDomain() } }
    fun searchArtists(q: String): Flow<List<Artist>> = musicDao.searchArtists(q).map { it.map { e -> e.toDomain() } }

    // ---- Mixes (spec §17) ----
    /** Personal mix using the documented bucket algorithm (favorites/most-played/best-rated). */
    suspend fun trackMix(size: Int = 200): List<Track> {
        val all = musicDao.allTracks().first().map { it.toDomain() }
        val recent = musicDao.recentlyPlayed(50).first().map { it.id }.toSet()
        return PersonalMixGenerator.generate(all, size, recent)
    }

    // ---- Playlist management (spec §32) ----
    suspend fun createPlaylist(name: String): Long =
        playlistDao.upsert(com.micasong.player.data.db.PlaylistEntity(name = name, providerId = LOCAL_PROVIDER_ID))

    /** Append tracks to a playlist (skipping ones already present) and refresh its count. */
    suspend fun addTracksToPlaylist(playlistId: Long, trackIds: List<Long>) {
        val current = playlistDao.memberTrackIds(playlistId)
        val merged = current + trackIds.filter { it !in current }
        playlistDao.setPlaylistTracks(playlistId, merged)
        updatePlaylistCount(playlistId, merged.size)
    }

    suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: Long) {
        val remaining = playlistDao.memberTrackIds(playlistId).filterNot { it == trackId }
        playlistDao.setPlaylistTracks(playlistId, remaining)
        updatePlaylistCount(playlistId, remaining.size)
    }

    suspend fun renamePlaylist(playlistId: Long, name: String) {
        playlistDao.getPlaylist(playlistId)?.let { playlistDao.upsert(it.copy(name = name)) }
    }

    suspend fun deletePlaylist(playlistId: Long) = playlistDao.delete(playlistId)

    /** Save an ordered list of tracks (e.g. the current queue) as a new playlist (spec §16). */
    suspend fun saveAsPlaylist(name: String, trackIds: List<Long>): Long {
        val id = createPlaylist(name)
        addTracksToPlaylist(id, trackIds)
        return id
    }

    private suspend fun updatePlaylistCount(playlistId: Long, count: Int) {
        playlistDao.getPlaylist(playlistId)?.let { playlistDao.upsert(it.copy(trackCount = count)) }
    }

    // ---- Smart playlists (spec §31) ----
    /** Evaluate a smart-playlist definition against the current library snapshot. */
    suspend fun evaluateSmartPlaylist(def: SmartPlaylistDefinition): List<Track> {
        val all = musicDao.allTracks().first().map { it.toDomain() }
        return def.apply(all)
    }

    // ---- Smart Queue / Smart Flow (spec §16) ----
    /** Tracks to append at the end of the queue for the given Smart Queue mode. */
    suspend fun smartQueueExtension(recent: List<Track>, mode: SmartQueueMode, count: Int): List<Track> {
        val library = musicDao.allTracks().first().map { it.toDomain() }
        return SmartQueueExtender.extend(recent, library, mode, count)
    }

    /** Tracks to insert after the current one for the given Smart Flow mode. */
    suspend fun smartFlowInsertions(current: Track, mode: SmartFlowMode, maxInsertions: Int = SmartFlow.DEFAULT_MAX_INSERTIONS): List<Track> {
        val library = musicDao.allTracks().first().map { it.toDomain() }
        return SmartFlow.nextInsertions(current, library, mode, maxInsertions)
    }

    // ---- User state (spec §10) ----
    suspend fun toggleTrackFavorite(track: Track) = musicDao.setTrackFavorite(track.id, !track.isFavorite)
    suspend fun toggleAlbumFavorite(album: Album) = musicDao.setAlbumFavorite(album.id, !album.isFavorite)
    suspend fun setTrackRating(id: Long, rating: Int) = musicDao.setTrackRating(id, rating)
    suspend fun registerPlay(id: Long, ts: Long) = musicDao.registerPlay(id, ts)
    suspend fun setResumePosition(id: Long, pos: Long) = musicDao.setResumePosition(id, pos)

    suspend fun trackById(id: Long): Track? = musicDao.trackById(id)?.toDomain()
    suspend fun tracksByIds(ids: List<Long>): List<Track> = musicDao.tracksByIds(ids).map { it.toDomain() }

    // ---- Sync ----
    suspend fun syncAll() {
        _syncState.value = SyncState(running = true, message = "Sincronizando…")
        try {
            buildProviders().forEach { provider ->
                val snapshot = provider.sync { p, msg -> _syncState.value = SyncState(true, p, msg) }
                if (provider.config.type == ProviderType.LOCAL) {
                    // Local: a full re-scan replaces the device library outright.
                    musicDao.replaceLocalLibrary(
                        provider.config.id,
                        snapshot.tracks, snapshot.albums, snapshot.artists, snapshot.genres,
                    )
                } else {
                    applyServerSnapshot(provider.config.id, snapshot)
                }
            }
        } finally {
            _syncState.value = SyncState(running = false, progress = 1f, message = "")
        }
    }

    /**
     * Apply a server snapshot differentially (spec §9), preserving local user state (favorites,
     * ratings, play counts, offline state) via [ServerSyncMerge] — a re-sync must never wipe it.
     */
    private suspend fun applyServerSnapshot(providerId: Long, snapshot: com.micasong.player.data.provider.ProviderSnapshot) {
        val existing = musicDao.tracksByProviderList(providerId)
        val apply = ServerSyncMerge.merge(existing, snapshot.tracks)

        // Preserve album favorites across the upsert (album ids the user starred locally).
        val favAlbumIds = musicDao.favoriteAlbumIds().toSet()
        val albums = snapshot.albums.map { if (it.id in favAlbumIds) it.copy(isFavorite = true) else it }

        musicDao.upsertArtists(snapshot.artists)
        musicDao.upsertAlbums(albums)
        musicDao.upsertGenres(snapshot.genres)
        if (apply.deleteIds.isNotEmpty()) musicDao.deleteTracksByIds(apply.deleteIds)
        if (apply.upsert.isNotEmpty()) musicDao.upsertTracks(apply.upsert)
    }

    companion object {
        const val LOCAL_PROVIDER_ID = 1L
    }
}
