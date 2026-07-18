package com.micasong.player.di

import com.micasong.player.playback.CastSessionManager
import com.micasong.player.playback.RealCastSessionManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Full build: bind the real Chromecast-backed session manager. */
@Module
@InstallIn(SingletonComponent::class)
abstract class CastModule {
    @Binds
    @Singleton
    abstract fun bindCastSessionManager(impl: RealCastSessionManager): CastSessionManager
}
