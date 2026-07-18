package com.micasong.player.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** DAO for user & imported playlists (spec §32, §33). */
@Dao
interface PlaylistDao {

    @Query("SELECT * FROM playlists ORDER BY name COLLATE NOCASE ASC")
    fun playlists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    fun playlist(id: Long): Flow<PlaylistEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(playlist: PlaylistEntity): Long

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun delete(id: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addCrossRefs(refs: List<PlaylistTrackCrossRef>)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun clearMembers(playlistId: Long)

    @Query(
        """SELECT t.* FROM tracks t
           INNER JOIN playlist_tracks pt ON pt.trackId = t.id
           WHERE pt.playlistId = :playlistId
           ORDER BY pt.position ASC"""
    )
    fun tracksInPlaylist(playlistId: Long): Flow<List<TrackEntity>>

    @Transaction
    suspend fun setPlaylistTracks(playlistId: Long, trackIds: List<Long>) {
        clearMembers(playlistId)
        addCrossRefs(trackIds.mapIndexed { i, id -> PlaylistTrackCrossRef(playlistId, id, i) })
    }
}
