package com.micasong.player.data.playlist

/** One entry parsed from an M3U/PLS file (spec §32). */
data class PlaylistEntry(
    val path: String,
    val title: String? = null,
    val durationSec: Int? = null,
)

data class ParsedPlaylist(val entries: List<PlaylistEntry>) {
    val paths: List<String> get() = entries.map { it.path }
}

/**
 * Parser for M3U/M3U8 and PLS playlist files (spec §32 — playlist import from file providers).
 * Extracts each entry's path/URL plus any `#EXTINF` (M3U) or `TitleN`/`LengthN` (PLS) metadata.
 * Pure and unit-testable; path resolution to library tracks happens later in the import step.
 */
object PlaylistFileParser {

    /** Dispatch by extension when known, otherwise sniff the content. */
    fun parse(content: String, fileName: String? = null): ParsedPlaylist {
        val ext = fileName?.substringAfterLast('.', "")?.lowercase()
        return when {
            ext == "pls" -> parsePls(content)
            ext == "m3u" || ext == "m3u8" -> parseM3u(content)
            content.trimStart().startsWith("[playlist]", ignoreCase = true) -> parsePls(content)
            else -> parseM3u(content)
        }
    }

    fun parseM3u(content: String): ParsedPlaylist {
        val entries = ArrayList<PlaylistEntry>()
        var pendingTitle: String? = null
        var pendingDuration: Int? = null

        for (rawLine in content.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            if (line.startsWith("#")) {
                if (line.startsWith("#EXTINF:", ignoreCase = true)) {
                    val info = line.substring("#EXTINF:".length)   // strip prefix (case-insensitive match above)
                    val comma = info.indexOf(',')
                    if (comma >= 0) {
                        pendingDuration = info.substring(0, comma).trim().toDoubleOrNull()?.toInt()?.takeIf { it >= 0 }
                        pendingTitle = info.substring(comma + 1).trim().ifEmpty { null }
                    }
                }
                continue   // other #-directives (#EXTM3U, #PLAYLIST…) are ignored
            }
            entries += PlaylistEntry(path = line, title = pendingTitle, durationSec = pendingDuration)
            pendingTitle = null
            pendingDuration = null
        }
        return ParsedPlaylist(entries)
    }

    fun parsePls(content: String): ParsedPlaylist {
        val files = sortedMapOf<Int, String>()
        val titles = HashMap<Int, String>()
        val lengths = HashMap<Int, Int>()

        for (rawLine in content.lineSequence()) {
            val line = rawLine.trim()
            val eq = line.indexOf('=')
            if (eq <= 0) continue
            val key = line.substring(0, eq).trim()
            val value = line.substring(eq + 1).trim()
            when {
                key.startsWith("File", true) -> key.indexAfter("File")?.let { files[it] = value }
                key.startsWith("Title", true) -> key.indexAfter("Title")?.let { titles[it] = value }
                key.startsWith("Length", true) -> key.indexAfter("Length")?.let { n ->
                    value.toIntOrNull()?.let { lengths[n] = it }
                }
            }
        }
        val entries = files.map { (n, path) ->
            PlaylistEntry(path = path, title = titles[n], durationSec = lengths[n]?.takeIf { it >= 0 })
        }
        return ParsedPlaylist(entries)
    }

    /** Parse the trailing index of a PLS key like "File3" → 3. */
    private fun String.indexAfter(prefix: String): Int? =
        substring(prefix.length).trim().toIntOrNull()
}
