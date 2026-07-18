package com.micasong.player.di

import com.micasong.player.data.cache.DownloadManager
import com.micasong.player.data.cache.DownloadTrigger
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Binds the concrete [DownloadManager] as the [DownloadTrigger] the repository depends on. */
@Module
@InstallIn(SingletonComponent::class)
abstract class CacheModule {
    @Binds
    @Singleton
    abstract fun bindDownloadTrigger(impl: DownloadManager): DownloadTrigger
}
