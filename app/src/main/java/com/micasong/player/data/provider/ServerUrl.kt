package com.micasong.player.data.provider

/**
 * Normalizes a user-entered server URL (spec §5). Users often type a bare `host:port` (e.g. a LAN
 * Navidrome), which has no scheme and can't be parsed/opened — default such inputs to `http://`
 * (cleartext is allowed for self-hosted servers) and strip a trailing slash. Pure and testable.
 */
object ServerUrl {
    fun normalize(raw: String?): String? {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        val withScheme = if ("://" in trimmed) trimmed else "http://$trimmed"
        return withScheme.trimEnd('/')
    }
}
