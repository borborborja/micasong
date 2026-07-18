package com.micasong.player.di

import android.content.Context
import androidx.room.Room
import com.micasong.player.data.db.DownloadDao
import com.micasong.player.data.db.MiCaSongDatabase
import com.micasong.player.data.db.MusicDao
import com.micasong.player.data.db.PlaylistDao
import com.micasong.player.data.db.ProviderDao
import com.micasong.player.data.db.RadioDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): MiCaSongDatabase =
        Room.databaseBuilder(context, MiCaSongDatabase::class.java, MiCaSongDatabase.NAME)
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideMusicDao(db: MiCaSongDatabase): MusicDao = db.musicDao()

    @Provides
    fun providePlaylistDao(db: MiCaSongDatabase): PlaylistDao = db.playlistDao()

    @Provides
    fun provideProviderDao(db: MiCaSongDatabase): ProviderDao = db.providerDao()

    @Provides
    fun provideDownloadDao(db: MiCaSongDatabase): DownloadDao = db.downloadDao()

    @Provides
    fun provideRadioDao(db: MiCaSongDatabase): RadioDao = db.radioDao()
}
