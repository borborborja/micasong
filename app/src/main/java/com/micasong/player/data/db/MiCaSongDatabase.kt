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
        DownloadEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
abstract class MiCaSongDatabase : RoomDatabase() {
    abstract fun musicDao(): MusicDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun providerDao(): ProviderDao
    abstract fun downloadDao(): DownloadDao

    companion object {
        const val NAME = "micasong.db"
    }
}
