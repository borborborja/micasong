package com.micasong.player.di

import com.micasong.player.playback.CastSessionManager
import com.micasong.player.playback.NoopCastSessionManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** FOSS binding: casting is a no-op (no Google Play Services). */
@Module
@InstallIn(SingletonComponent::class)
abstract class CastModule {
    @Binds
    @Singleton
    abstract fun bindCastSessionManager(impl: NoopCastSessionManager): CastSessionManager
}
