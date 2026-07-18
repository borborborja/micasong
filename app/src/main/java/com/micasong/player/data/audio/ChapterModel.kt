package com.micasong.player.data.audio

/** A single audiobook/podcast chapter (spec §19, ID3v2 chapters). */
data class Chapter(val index: Int, val title: String, val startMs: Long, val endMs: Long) {
    val durationMs: Long get() = (endMs - startMs).coerceAtLeast(0)
    operator fun contains(positionMs: Long): Boolean = positionMs in startMs until endMs
}

/** The full chapter list for a track. */
data class ChapterInfo(val chapters: List<Chapter>) {
    val count: Int get() = chapters.size
    val isEmpty: Boolean get() = chapters.isEmpty()

    /** The chapter containing [positionMs] (clamped to the last chapter past the end). */
    fun chapterAt(positionMs: Long): Chapter? {
        if (chapters.isEmpty()) return null
        chapters.firstOrNull { positionMs in it }?.let { return it }
        return if (positionMs >= chapters.last().endMs) chapters.last() else chapters.first()
    }
}

/**
 * Snapshot of chapter navigation for the current position — the fields the Now Playing string
 * templates expose (spec §19, §27): `chapter.count/index/title/position/remaining/duration`,
 * `next.chapter.title`, and the `chapter.all.*` total progress relative to the first chapter.
 */
data class ChapterNavState(
    val count: Int,
    val index: Int,                 // 1-based
    val title: String,
    val positionInChapterMs: Long,
    val remainingInChapterMs: Long,
    val chapterDurationMs: Long,
    val nextChapterTitle: String?,
    val totalProgressMs: Long,
    val totalDurationMs: Long,
)

object Chapters {

    /** Build contiguous chapters from `(title, startMs)` markers and the track's total duration. */
    fun fromMarkers(markers: List<Pair<String, Long>>, totalDurationMs: Long): ChapterInfo {
        if (markers.isEmpty()) return ChapterInfo(emptyList())
        val sorted = markers.sortedBy { it.second }
        val chapters = sorted.mapIndexed { i, (title, start) ->
            val end = if (i < sorted.lastIndex) sorted[i + 1].second else totalDurationMs
            Chapter(index = i, title = title, startMs = start, endMs = end)
        }
        return ChapterInfo(chapters)
    }

    fun navStateAt(info: ChapterInfo, positionMs: Long): ChapterNavState? {
        val current = info.chapterAt(positionMs) ?: return null
        val next = info.chapters.getOrNull(current.index + 1)
        val first = info.chapters.first()
        val last = info.chapters.last()
        return ChapterNavState(
            count = info.count,
            index = current.index + 1,
            title = current.title,
            positionInChapterMs = (positionMs - current.startMs).coerceAtLeast(0),
            remainingInChapterMs = (current.endMs - positionMs).coerceAtLeast(0),
            chapterDurationMs = current.durationMs,
            nextChapterTitle = next?.title,
            totalProgressMs = (positionMs - first.startMs).coerceAtLeast(0),
            totalDurationMs = (last.endMs - first.startMs).coerceAtLeast(0),
        )
    }
}
