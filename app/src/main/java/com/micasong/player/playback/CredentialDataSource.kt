package com.micasong.player.playback

import android.content.Context
import android.util.Base64
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource

/**
 * A media-source factory that turns `http://user:pass@host/…` URLs into authenticated requests
 * (spec §46). Providers whose backends use HTTP Basic auth for streaming (Kodi's `/vfs/`, WebDAV,
 * internet radio with embedded credentials) put the credentials in the URL userinfo; this strips
 * them and sends a proper `Authorization: Basic …` header instead, which ExoPlayer's HTTP data
 * source needs. `content://` / `file://` (local + downloaded) URIs pass through untouched.
 */
fun credentialMediaSourceFactory(context: Context): MediaSource.Factory {
    val base = DefaultDataSource.Factory(context)
    val resolving = ResolvingDataSource.Factory(base, ResolvingDataSource.Resolver { dataSpec ->
        val uri = dataSpec.uri
        val userInfo = uri.userInfo
        if (uri.scheme?.startsWith("http") == true && !userInfo.isNullOrEmpty()) {
            val creds = Base64.encodeToString(userInfo.toByteArray(), Base64.NO_WRAP)
            val authority = uri.host + if (uri.port > 0) ":${uri.port}" else ""
            val cleaned = uri.buildUpon().encodedAuthority(authority).build()
            dataSpec.withUri(cleaned).withAdditionalHeaders(mapOf("Authorization" to "Basic $creds"))
        } else {
            dataSpec
        }
    })
    return DefaultMediaSourceFactory(resolving as DataSource.Factory)
}
