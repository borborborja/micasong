package com.micasong.player.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        TrackEntity::class,
        AlbumEntity::class,
        ArtistEntity::class,
        GenreEntity::class,
        PlaylistEntity::class,
        PlaylistTrackCrossRef::class,
        ProviderConfigEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class MiCaSongDatabase : RoomDatabase() {
    abstract fun musicDao(): MusicDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun providerDao(): ProviderDao

    companion object {
        const val NAME = "micasong.db"
    }
}
