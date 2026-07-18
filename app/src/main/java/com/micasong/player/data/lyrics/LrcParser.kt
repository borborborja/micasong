package com.micasong.player.data.lyrics

/** One synced lyric line: its start time and text (spec §41). */
data class LyricLine(val timeMs: Long, val text: String)

/** Parsed lyrics — either plain (no timings) or synced (LRC). */
data class Lyrics(val lines: List<LyricLine>, val synced: Boolean) {
    /** Index of the active line for a playback position, or -1 before the first timestamp. */
    fun activeIndexAt(positionMs: Long): Int {
        if (!synced) return -1
        var idx = -1
        for (i in lines.indices) {
            if (lines[i].timeMs <= positionMs) idx = i else break
        }
        return idx
    }
}

/**
 * Parser for LRC synced lyrics (spec §41). Handles multiple timestamps per line (`[..][..]text`,
 * which expand to several lines), metadata tags like `[ar:]`/`[ti:]` (ignored for display), and
 * plain (unsynced) lyrics. Lines are returned sorted by time; lines sharing identical timestamps
 * are merged in source order.
 */
object LrcParser {

    private val TIME_TAG = Regex("\\[(\\d{1,2}):(\\d{1,2})(?:[.:](\\d{1,3}))?]")
    private val META_TAG = Regex("^\\[(ar|ti|al|by|offset|length|re|ve):.*]$", RegexOption.IGNORE_CASE)

    fun parse(raw: String?): Lyrics {
        if (raw.isNullOrBlank()) return Lyrics(emptyList(), synced = false)

        val out = ArrayList<LyricLine>()
        var sawTiming = false

        for (rawLine in raw.lineSequence()) {
            val line = rawLine.trimEnd()
            if (line.isBlank()) continue
            if (META_TAG.matches(line.trim())) continue

            val stamps = TIME_TAG.findAll(line).toList()
            if (stamps.isEmpty()) {
                // Plain line (only meaningful when the file has no timing at all).
                out += LyricLine(timeMs = 0, text = line.trim())
                continue
            }
            sawTiming = true
            val text = line.substring(stamps.last().range.last + 1).trim()
            for (stamp in stamps) {
                out += LyricLine(timeMs = stamp.toMillis(), text = text)
            }
        }

        return if (sawTiming) {
            Lyrics(out.filter { it.timeMs >= 0 }.sortedBy { it.timeMs }, synced = true)
        } else {
            Lyrics(out, synced = false)
        }
    }

    private fun MatchResult.toMillis(): Long {
        val min = groupValues[1].toLong()
        val sec = groupValues[2].toLong()
        val fracRaw = groupValues[3]
        val frac = when {
            fracRaw.isEmpty() -> 0L
            fracRaw.length == 1 -> fracRaw.toLong() * 100
            fracRaw.length == 2 -> fracRaw.toLong() * 10
            else -> fracRaw.take(3).toLong()
        }
        return (min * 60 + sec) * 1000 + frac
    }
}
