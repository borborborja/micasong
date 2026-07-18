package com.micasong.player.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import com.micasong.player.data.cache.CacheTier
import com.micasong.player.data.cache.DownloadState
import kotlinx.coroutines.flow.Flow

/**
 * Persisted offline-download / cache row for one track (spec §34-35). One row per track: it tracks
 * the download lifecycle, the local file once complete, its cache tier, and whether an automatic
 * rule created it. The immutable [com.micasong.player.data.cache.DownloadQueue] /
 * [com.micasong.player.data.cache.OfflineCache] engines operate on snapshots of these rows.
 */
@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val trackId: Long,
    val providerId: Long,
    /** [DownloadState.ordinal]: 0 QUEUED · 1 DOWNLOADING · 2 COMPLETED · 3 FAILED. */
    val state: Int = DownloadState.QUEUED.ordinal,
    val progress: Float = 0f,
    /** [CacheTier.ordinal]: 0 PLAYBACK · 1 ROLLING · 2 PERMANENT. */
    val tier: Int = CacheTier.ROLLING.ordinal,
    val localPath: String? = null,
    val sizeBytes: Long = 0L,
    val lastAccess: Long = 0L,
    val enqueuedAt: Long = 0L,
    val auto: Boolean = false,
)

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY enqueuedAt ASC")
    fun all(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads")
    suspend fun snapshot(): List<DownloadEntity>

    @Query("SELECT * FROM downloads WHERE trackId = :trackId")
    suspend fun byTrack(trackId: Long): DownloadEntity?

    /** Local file path for a track whose download is COMPLETED (state = 2), else null. */
    @Query("SELECT localPath FROM downloads WHERE trackId = :trackId AND state = 2 LIMIT 1")
    suspend fun completedPath(trackId: Long): String?

    @Query("SELECT trackId FROM downloads WHERE auto = 1")
    suspend fun autoCachedIds(): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DownloadEntity)

    @Query("UPDATE downloads SET state = :state, progress = :progress WHERE trackId = :trackId")
    suspend fun updateState(trackId: Long, state: Int, progress: Float)

    @Query("UPDATE downloads SET progress = :progress WHERE trackId = :trackId")
    suspend fun updateProgress(trackId: Long, progress: Float)

    @Query("UPDATE downloads SET localPath = :path, sizeBytes = :size, state = 2, progress = 1 WHERE trackId = :trackId")
    suspend fun markComplete(trackId: Long, path: String, size: Long)

    @Query("UPDATE downloads SET tier = :tier WHERE trackId = :trackId")
    suspend fun setTier(trackId: Long, tier: Int)

    @Query("DELETE FROM downloads WHERE trackId = :trackId")
    suspend fun delete(trackId: Long)

    @Query("DELETE FROM downloads")
    suspend fun deleteAll()
}
