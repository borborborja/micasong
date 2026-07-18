package com.micasong.player.data.repository

import android.content.Context
import com.micasong.player.data.cache.AutoCacheEngine
import com.micasong.player.data.cache.AutoCacheRules
import com.micasong.player.data.cache.CacheTier
import com.micasong.player.data.cache.DownloadState
import com.micasong.player.data.cache.RollingCache
import com.micasong.player.data.db.DownloadDao
import com.micasong.player.data.db.DownloadEntity
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
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
    private val downloadDao: DownloadDao,
    private val downloadTrigger: com.micasong.player.data.cache.DownloadTrigger,
    private val radioDao: com.micasong.player.data.db.RadioDao,
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
                ProviderType.EMBY -> com.micasong.player.data.provider.EmbyProvider(config)
                ProviderType.PLEX -> com.micasong.player.data.provider.PlexProvider(config)
                ProviderType.KODI -> com.micasong.player.data.provider.KodiProvider(config)
                ProviderType.WEBDAV -> com.micasong.player.data.provider.WebDavProvider(config)
                ProviderType.AUDIOBOOKSHELF -> com.micasong.player.data.provider.AudioBookShelfProvider(config)
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
    /**
     * Tracks of a playlist. A normal playlist returns its stored members; a smart playlist (spec
     * §31) re-evaluates its rule tree against the live library every time the catalog changes.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun tracksInPlaylist(id: Long): Flow<List<Track>> =
        playlistDao.playlist(id).flatMapLatest { pl ->
            if (pl?.isSmart == true) {
                val def = SmartPlaylistDefinition.fromJson(pl.smartRulesJson) ?: SmartPlaylistDefinition()
                musicDao.allTracks().map { list -> def.apply(list.map { e -> e.toDomain() }) }
            } else {
                playlistDao.tracksInPlaylist(id).map { it.map { e -> e.toDomain() } }
            }
        }

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

    /** Create a dynamic smart playlist that stores its rule tree as JSON on the playlist row. */
    suspend fun createSmartPlaylist(name: String, def: SmartPlaylistDefinition): Long =
        playlistDao.upsert(
            com.micasong.player.data.db.PlaylistEntity(
                name = name,
                providerId = LOCAL_PROVIDER_ID,
                isSmart = true,
                smartRulesJson = SmartPlaylistDefinition.toJson(def),
                sortOrder = def.sortField.name,
                limit = def.limit,
            )
        )

    /** Replace an existing smart playlist's name and rule tree. */
    suspend fun updateSmartPlaylist(playlistId: Long, name: String, def: SmartPlaylistDefinition) {
        playlistDao.getPlaylist(playlistId)?.let {
            playlistDao.upsert(
                it.copy(
                    name = name,
                    isSmart = true,
                    smartRulesJson = SmartPlaylistDefinition.toJson(def),
                    sortOrder = def.sortField.name,
                    limit = def.limit,
                )
            )
        }
    }

    /** The stored rule tree of a smart playlist, or null if it isn't one. */
    suspend fun smartPlaylistDefinition(playlistId: Long): SmartPlaylistDefinition? =
        playlistDao.getPlaylist(playlistId)?.takeIf { it.isSmart }
            ?.let { SmartPlaylistDefinition.fromJson(it.smartRulesJson) }

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

    // ---- Offline downloads & cache (spec §34-35) ----
    /** All download/cache rows, for the offline-files UI. */
    val downloads: Flow<List<DownloadEntity>> = downloadDao.all()

    /** Queue tracks for offline download (skipping local-only tracks and ones already queued). */
    suspend fun enqueueDownloads(trackIds: List<Long>, tier: CacheTier = CacheTier.ROLLING, auto: Boolean = false) {
        var stamp = System.currentTimeMillis()
        var queued = 0
        for (id in trackIds) {
            if (downloadDao.byTrack(id) != null) continue
            val providerId = musicDao.trackById(id)?.providerId ?: continue
            if (providerId == LOCAL_PROVIDER_ID) continue // local files are already offline
            downloadDao.upsert(
                DownloadEntity(
                    trackId = id,
                    providerId = providerId,
                    state = DownloadState.QUEUED.ordinal,
                    tier = tier.ordinal,
                    enqueuedAt = stamp++,
                    auto = auto,
                )
            )
            queued++
        }
        if (queued > 0) downloadTrigger.trigger()
    }

    suspend fun removeDownload(trackId: Long) {
        downloadDao.byTrack(trackId)?.localPath?.let { runCatching { java.io.File(it).delete() } }
        downloadDao.delete(trackId)
    }

    suspend fun retryDownload(trackId: Long) =
        downloadDao.updateState(trackId, DownloadState.QUEUED.ordinal, 0f)

    suspend fun setDownloadTier(trackId: Long, tier: CacheTier) = downloadDao.setTier(trackId, tier.ordinal)

    /** The local file path if a track has been fully downloaded, else null (used at playback). */
    suspend fun downloadedPath(trackId: Long): String? = downloadDao.completedPath(trackId)

    /** Reconcile automatic-cache rules against the library (spec §34); returns tracks newly queued. */
    suspend fun runAutoCacheReconcile(rules: AutoCacheRules): Int {
        if (rules.isEmpty) return 0
        val library = musicDao.allTracks().first().map { it.toDomain() }
        val autoCached = downloadDao.autoCachedIds().toSet()
        val plan = AutoCacheEngine.reconcile(library, rules, autoCached)
        plan.toRemove.forEach { removeDownload(it) }
        enqueueDownloads(plan.toAdd.toList(), tier = CacheTier.ROLLING, auto = true)
        return plan.toAdd.size
    }

    /** Evict least-recently-used rolling downloads exceeding [maxBytes] (spec §34). */
    suspend fun evictRollingCache(maxBytes: Long) {
        val items = downloadDao.snapshot().map {
            com.micasong.player.data.cache.CachedTrack(it.trackId, CacheTier.entries[it.tier], it.sizeBytes, it.lastAccess, it.auto)
        }
        RollingCache.evictionPlan(items, maxBytes).forEach { removeDownload(it) }
    }

    // ---- User state (spec §10) ----
    suspend fun toggleTrackFavorite(track: Track) = musicDao.setTrackFavorite(track.id, !track.isFavorite)
    suspend fun toggleAlbumFavorite(album: Album) = musicDao.setAlbumFavorite(album.id, !album.isFavorite)
    suspend fun setTrackRating(id: Long, rating: Int) = musicDao.setTrackRating(id, rating)
    suspend fun registerPlay(id: Long, ts: Long) = musicDao.registerPlay(id, ts)

    /** Report a completed play to the track's server backend (scrobble, spec §9). */
    suspend fun scrobble(id: Long) {
        val track = musicDao.trackById(id) ?: return
        if (track.providerId == LOCAL_PROVIDER_ID) return
        val provider = buildProviders().firstOrNull { it.config.id == track.providerId } ?: return
        runCatching { provider.scrobble(track) }
    }
    suspend fun setResumePosition(id: Long, pos: Long) = musicDao.setResumePosition(id, pos)

    suspend fun trackById(id: Long): Track? = musicDao.trackById(id)?.toDomain()
    fun trackFlow(id: Long): Flow<Track?> = musicDao.trackByIdFlow(id).map { it?.toDomain() }

    // ---- Internet radio (spec §10) ----
    val radioStations: Flow<List<com.micasong.player.data.radio.RadioStation>> =
        radioDao.all().map { list -> list.map { it.toDomain() } }

    suspend fun addRadioStation(name: String, streamUrl: String, homepage: String? = null): Long =
        radioDao.upsert(
            com.micasong.player.data.db.RadioStationEntity(name = name.trim(), streamUrl = streamUrl.trim(), homepage = homepage)
        )

    suspend fun deleteRadioStation(id: Long) = radioDao.delete(id)

    suspend fun radioStationsSnapshot(): List<com.micasong.player.data.radio.RadioStation> =
        radioDao.all().first().map { it.toDomain() }

    // ---- Lyrics (spec §41) ----
    private val lyricsDir = java.io.File(context.filesDir, "lyrics").apply { mkdirs() }

    /** Lyrics for a track: cached on disk, else fetched from its provider and parsed (spec §41). */
    suspend fun lyricsFor(trackId: Long): com.micasong.player.data.lyrics.Lyrics? {
        val cache = java.io.File(lyricsDir, "$trackId.lrc")
        if (cache.exists()) return com.micasong.player.data.lyrics.LrcParser.parse(cache.readText())
        val track = musicDao.trackById(trackId) ?: return null
        if (track.providerId == LOCAL_PROVIDER_ID) return null // local sidecar lyrics: future step
        val provider = buildProviders().firstOrNull { it.config.id == track.providerId } ?: return null
        val raw = runCatching { provider.lyrics(track) }.getOrNull() ?: return null
        runCatching { cache.writeText(raw) }
        return com.micasong.player.data.lyrics.LrcParser.parse(raw)
    }
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
