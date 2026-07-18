package com.micasong.player.data.provider

import java.net.URLEncoder

/**
 * Pure, testable Jellyfin authentication and URL building (spec §46). Jellyfin authenticates via
 * `POST /Users/AuthenticateByName` and then carries the access token in the `Authorization:
 * MediaBrowser …` header (a `X-Emby-Token` header is equivalent). Extracted from
 * [JellyfinProvider] so the header format and query construction can be verified.
 */
object JellyfinAuth {

    const val CLIENT = "MiCaSong"
    const val VERSION = "0.1.0"

    /** The `Authorization: MediaBrowser …` header value, optionally including the session token. */
    fun authorizationHeader(deviceId: String, deviceName: String = "Android", token: String? = null): String {
        val base = "MediaBrowser Client=\"$CLIENT\", Device=\"$deviceName\", DeviceId=\"$deviceId\", Version=\"$VERSION\""
        return if (!token.isNullOrBlank()) "$base, Token=\"$token\"" else base
    }

    /** Build a full `{base}{path}?{query}` URL with URL-encoded values. */
    fun endpointUrl(baseUrl: String, path: String, params: Map<String, String> = emptyMap()): String {
        val base = baseUrl.trimEnd('/')
        val p = if (path.startsWith("/")) path else "/$path"
        if (params.isEmpty()) return "$base$p"
        val query = params.entries.joinToString("&") { (k, v) -> "$k=${enc(v)}" }
        return "$base$p?$query"
    }

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")
}
