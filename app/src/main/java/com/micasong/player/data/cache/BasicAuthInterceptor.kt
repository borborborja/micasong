package com.micasong.player.data.cache

import okhttp3.Interceptor
import okhttp3.Response

/**
 * OkHttp mirror of [com.micasong.player.playback.credentialMediaSourceFactory] (spec §46): backends
 * that stream with HTTP Basic auth (Kodi `/vfs/`, WebDAV, radio) carry `user:pass@host` in the URL.
 * OkHttp parses the userinfo but never sends it, so downloads and artwork loads would 401 — strip
 * it and send a real `Authorization: Basic …` header instead.
 */
object BasicAuthInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url
        if (url.username.isEmpty()) return chain.proceed(request)

        val credentials = "${url.username}:${url.password}"
        val encoded = java.util.Base64.getEncoder().encodeToString(credentials.toByteArray(Charsets.UTF_8))
        return chain.proceed(
            request.newBuilder()
                .url(url.newBuilder().username("").password("").build())
                .header("Authorization", "Basic $encoded")
                .build()
        )
    }
}
