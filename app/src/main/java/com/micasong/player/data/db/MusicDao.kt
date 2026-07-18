package com.micasong.player.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * Data-access object for the unified catalog. Read queries return [Flow]s so the Compose UI
 * reacts to sync updates automatically (offline-first, spec §2).
 */
@Dao
interface MusicDao {

    // ---- Upserts (used by the sync engine) ----
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTracks(tracks: List<TrackEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAlbums(albums: List<AlbumEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertArtists(artists: List<ArtistEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGenres(genres: List<GenreEntity>)

    // ---- Albums ----
    @Query("SELECT * FROM albums ORDER BY nameSort COLLATE NOCASE ASC")
    fun albums(): Flow<List<AlbumEntity>>

    @Query("SELECT * FROM albums WHERE isFavorite = 1 ORDER BY nameSort COLLATE NOCASE ASC")
    fun favoriteAlbums(): Flow<List<AlbumEntity>>

    @Query("SELECT * FROM albums ORDER BY dateAdded DESC LIMIT :limit")
    fun recentlyAddedAlbums(limit: Int): Flow<List<AlbumEntity>>

    @Query("SELECT * FROM albums WHERE playCount > 0 ORDER BY playCount DESC LIMIT :limit")
    fun mostPlayedAlbums(limit: Int): Flow<List<AlbumEntity>>

    @Query("SELECT * FROM albums WHERE id = :id")
    fun album(id: Long): Flow<AlbumEntity?>

    @Query("SELECT * FROM albums WHERE artistId = :artistId ORDER BY year DESC, nameSort COLLATE NOCASE ASC")
    fun albumsByArtist(artistId: Long): Flow<List<AlbumEntity>>

    // ---- Artists ----
    @Query("SELECT * FROM artists ORDER BY nameSort COLLATE NOCASE ASC")
    fun artists(): Flow<List<ArtistEntity>>

    @Query("SELECT * FROM artists WHERE id = :id")
    fun artist(id: Long): Flow<ArtistEntity?>

    // ---- Genres ----
    @Query("SELECT * FROM genres ORDER BY name COLLATE NOCASE ASC")
    fun genres(): Flow<List<GenreEntity>>

    // ---- Tracks ----
    @Query("SELECT * FROM tracks ORDER BY title COLLATE NOCASE ASC")
    fun allTracks(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE albumId = :albumId ORDER BY discNumber ASC, trackNumber ASC, title COLLATE NOCASE ASC")
    fun tracksByAlbum(albumId: Long): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE artistId = :artistId ORDER BY albumName, discNumber, trackNumber")
    fun tracksByArtist(artistId: Long): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE genre = :genre ORDER BY title COLLATE NOCASE ASC")
    fun tracksByGenre(genre: String): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE isFavorite = 1 ORDER BY title COLLATE NOCASE ASC")
    fun favoriteTracks(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE lastPlayed > 0 ORDER BY lastPlayed DESC LIMIT :limit")
    fun recentlyPlayed(limit: Int): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE playCount > 0 ORDER BY playCount DESC LIMIT :limit")
    fun mostPlayed(limit: Int): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE resumePositionMs > 0 ORDER BY lastPlayed DESC LIMIT :limit")
    fun resumable(limit: Int): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE id = :id")
    suspend fun trackById(id: Long): TrackEntity?

    @Query("SELECT * FROM tracks WHERE id = :id")
    fun trackByIdFlow(id: Long): Flow<TrackEntity?>

    @Query("SELECT * FROM tracks WHERE id IN (:ids)")
    suspend fun tracksByIds(ids: List<Long>): List<TrackEntity>

    // Differential server sync (spec §9): compare the stored snapshot with the fetched one.
    @Query("SELECT * FROM tracks WHERE providerId = :providerId")
    suspend fun tracksByProviderList(providerId: Long): List<TrackEntity>

    @Query("DELETE FROM tracks WHERE id IN (:ids)")
    suspend fun deleteTracksByIds(ids: List<Long>)

    @Query("SELECT id FROM albums WHERE isFavorite = 1")
    suspend fun favoriteAlbumIds(): List<Long>

    // Weighted-random selection used by the mix shortcuts (spec §17).
    @Query("SELECT * FROM tracks WHERE excludedFromMixes = 0 ORDER BY RANDOM() LIMIT :limit")
    suspend fun randomTracks(limit: Int): List<TrackEntity>

    // ---- Search (spec §29) ----
    @Query(
        """SELECT * FROM tracks
           WHERE title LIKE '%' || :q || '%'
              OR artistName LIKE '%' || :q || '%'
              OR albumName LIKE '%' || :q || '%'
           ORDER BY title COLLATE NOCASE ASC LIMIT 100"""
    )
    fun searchTracks(q: String): Flow<List<TrackEntity>>

    @Query("SELECT * FROM albums WHERE name LIKE '%' || :q || '%' ORDER BY nameSort COLLATE NOCASE ASC LIMIT 50")
    fun searchAlbums(q: String): Flow<List<AlbumEntity>>

    @Query("SELECT * FROM artists WHERE name LIKE '%' || :q || '%' ORDER BY nameSort COLLATE NOCASE ASC LIMIT 50")
    fun searchArtists(q: String): Flow<List<ArtistEntity>>

    // ---- User state mutations (spec §10) ----
    @Query("UPDATE tracks SET isFavorite = :fav WHERE id = :id")
    suspend fun setTrackFavorite(id: Long, fav: Boolean)

    @Query("UPDATE albums SET isFavorite = :fav WHERE id = :id")
    suspend fun setAlbumFavorite(id: Long, fav: Boolean)

    @Query("UPDATE tracks SET userRating = :rating WHERE id = :id")
    suspend fun setTrackRating(id: Long, rating: Int)

    @Query("UPDATE tracks SET playCount = playCount + 1, lastPlayed = :ts WHERE id = :id")
    suspend fun registerPlay(id: Long, ts: Long)

    @Query("UPDATE tracks SET skipCount = skipCount + 1 WHERE id = :id")
    suspend fun registerSkip(id: Long)

    @Query("UPDATE tracks SET resumePositionMs = :pos WHERE id = :id")
    suspend fun setResumePosition(id: Long, pos: Long)

    // ---- Maintenance ----
    @Query("DELETE FROM tracks WHERE providerId = :providerId")
    suspend fun clearProviderTracks(providerId: Long)

    @Query("DELETE FROM albums")
    suspend fun clearAlbums()

    @Query("DELETE FROM artists")
    suspend fun clearArtists()

    @Query("DELETE FROM genres")
    suspend fun clearGenres()

    @Query("SELECT COUNT(*) FROM tracks")
    fun trackCount(): Flow<Int>

    @Transaction
    suspend fun replaceLocalLibrary(
        providerId: Long,
        tracks: List<TrackEntity>,
        albums: List<AlbumEntity>,
        artists: List<ArtistEntity>,
        genres: List<GenreEntity>,
    ) {
        clearProviderTracks(providerId)
        clearAlbums()
        clearArtists()
        clearGenres()
        upsertArtists(artists)
        upsertAlbums(albums)
        upsertGenres(genres)
        upsertTracks(tracks)
    }
}
