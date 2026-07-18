package com.micasong.player.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import com.micasong.player.data.radio.RadioStation
import kotlinx.coroutines.flow.Flow

/** A saved internet-radio station (spec §10). */
@Entity(tableName = "radio_stations")
data class RadioStationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val streamUrl: String,
    val homepage: String? = null,
    val imageUrl: String? = null,
)

fun RadioStationEntity.toDomain() = RadioStation(id, name, streamUrl, homepage, imageUrl)

@Dao
interface RadioDao {
    @Query("SELECT * FROM radio_stations ORDER BY name COLLATE NOCASE ASC")
    fun all(): Flow<List<RadioStationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(station: RadioStationEntity): Long

    @Query("DELETE FROM radio_stations WHERE id = :id")
    suspend fun delete(id: Long)
}
