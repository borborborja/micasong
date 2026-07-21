package com.micasong.player.data.audio

/**
 * Best-effort audio MIME resolution (spec §36). Google Cast's queue items must declare a MIME type
 * (media3's DefaultMediaItemConverter throws without one), but not every provider reports it —
 * Plex reports a bare codec name, Jellyfin/Kodi none at all. Pure and testable.
 */
object AudioMime {

    private val byExtension = mapOf(
        "mp3" to "audio/mpeg",
        "mp2" to "audio/mpeg",
        "flac" to "audio/flac",
        "ogg" to "audio/ogg",
        "oga" to "audio/ogg",
        "opus" to "audio/opus",
        "m4a" to "audio/mp4",
        "m4b" to "audio/mp4",
        "mp4" to "audio/mp4",
        "aac" to "audio/aac",
        "wav" to "audio/wav",
        "wma" to "audio/x-ms-wma",
        "aif" to "audio/aiff",
        "aiff" to "audio/aiff",
        "dsf" to "audio/x-dsf",
        "dff" to "audio/x-dff",
        "m3u8" to "application/x-mpegURL",
    )

    /**
     * Resolve a MIME type for a playable URL: a declared real MIME wins ("audio/flac"), else the
     * URL's file extension, else `audio/mpeg` as the safest guess a Cast receiver will accept.
     */
    fun forUrl(url: String?, declared: String? = null): String {
        if (declared != null && '/' in declared) return declared
        val ext = url
            ?.substringBefore('?')
            ?.substringAfterLast('/', "")
            ?.substringAfterLast('.', "")
            ?.lowercase()
        return byExtension[ext] ?: "audio/mpeg"
    }

    /** A declared MIME only if it is a real MIME string (Plex reports bare codec names like "mp3"). */
    fun declaredOrNull(declared: String?): String? = declared?.takeIf { '/' in it }
}
