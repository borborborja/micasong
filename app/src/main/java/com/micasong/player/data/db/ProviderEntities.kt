package com.micasong.player.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import com.micasong.player.data.provider.ProviderConfig
import com.micasong.player.data.provider.ProviderType
import kotlinx.coroutines.flow.Flow

/**
 * Persisted media-provider connections (spec §4, §5). Lets the user add Subsonic/Jellyfin/… servers
 * that survive restarts. The local device provider is a fixed singleton and is NOT stored here.
 */
@Entity(tableName = "provider_configs")
data class ProviderConfigEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val type: String,
    val displayName: String,
    val primaryUrl: String?,
    val secondaryUrl: String?,
    val username: String?,
    val secret: String?,
    val wifiOnly: Boolean = false,
    val enabled: Boolean = true,
    val maxBitrateMobile: Int = 0,
    val maxBitrateWifi: Int = 0,
    /** Active connection: 1 = primary, 2 = secondary (spec §5.1, force_provider_connection). */
    val activeConnection: Int = 1,
)

/** Provider ids are offset from the row id so they never collide with the local provider (id 1). */
const val PROVIDER_ID_BASE = 1000L

fun ProviderConfigEntity.toConfig(): ProviderConfig {
    // Honour the active-connection selection: use the secondary URL when selected (spec §5.1).
    val effectivePrimary = if (activeConnection == 2 && !secondaryUrl.isNullOrBlank()) secondaryUrl else primaryUrl
    return ProviderConfig(
        id = PROVIDER_ID_BASE + rowId,
        type = runCatching { ProviderType.valueOf(type) }.getOrDefault(ProviderType.SUBSONIC),
        displayName = displayName,
        primaryUrl = effectivePrimary,
        secondaryUrl = secondaryUrl,
        username = username,
        secret = secret,
        wifiOnly = wifiOnly,
        enabled = enabled,
        maxBitrateMobile = maxBitrateMobile,
        maxBitrateWifi = maxBitrateWifi,
    )
}

fun ProviderConfig.toEntity(rowId: Long = 0): ProviderConfigEntity = ProviderConfigEntity(
    rowId = rowId,
    type = type.name,
    displayName = displayName,
    primaryUrl = primaryUrl,
    secondaryUrl = secondaryUrl,
    username = username,
    secret = secret,
    wifiOnly = wifiOnly,
    enabled = enabled,
    maxBitrateMobile = maxBitrateMobile,
    maxBitrateWifi = maxBitrateWifi,
)

@Dao
interface ProviderDao {
    @Query("SELECT * FROM provider_configs ORDER BY rowId")
    fun all(): Flow<List<ProviderConfigEntity>>

    @Query("SELECT * FROM provider_configs WHERE enabled = 1 ORDER BY rowId")
    suspend fun allEnabled(): List<ProviderConfigEntity>

    @Query("SELECT * FROM provider_configs WHERE rowId = :rowId")
    suspend fun byRowId(rowId: Long): ProviderConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ProviderConfigEntity): Long

    @Query("DELETE FROM provider_configs WHERE rowId = :rowId")
    suspend fun delete(rowId: Long)

    @Query("UPDATE provider_configs SET activeConnection = :connection WHERE rowId = :rowId")
    suspend fun setActiveConnection(rowId: Long, connection: Int)
}
