package com.micasong.player

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.micasong.player.data.cache.BasicAuthInterceptor
import dagger.hilt.android.HiltAndroidApp
import okhttp3.OkHttpClient

/**
 * MiCaSong application entry point.
 *
 * MiCaSong is an offline-first, multi-provider music & audiobook player that aggregates
 * several backends (local device, Subsonic/OpenSubsonic, Jellyfin, Plex, …) into a single
 * unified library, following the master specification. This class wires up the DI graph
 * (Hilt) and the shared image loader used across the UI and the media notification.
 */
@HiltAndroidApp
class MiCaSongApp : Application(), ImageLoaderFactory {

    /**
     * Persistent, memory- and disk-cached image loader. The spec calls for a persistent
     * image cache (§35) that survives process death; Coil's disk cache provides that.
     */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            // Artwork from Kodi/WebDAV carries Basic-auth credentials in the URL userinfo.
            .okHttpClient { OkHttpClient.Builder().addInterceptor(BasicAuthInterceptor).build() }
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.20)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(256L * 1024 * 1024)
                    .build()
            }
            .crossfade(true)
            .build()
}
