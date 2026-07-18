package com.micasong.player.data.session

import com.micasong.player.data.audio.ChapterInfo

/** Configurable custom actions for the notification / media session (spec §18, §37). */
enum class MediaAction {
    NONE,
    TOGGLE_FAVORITE,
    CYCLE_REPEAT,
    CYCLE_SHUFFLE,
    RADIO_MIX,
    RATE,
    REWIND_10,
    FORWARD_10,
    REWIND_30,
    FORWARD_30,
    PREV_CHAPTER,
    NEXT_CHAPTER,
    STOP;

    val isSeek: Boolean
        get() = this == REWIND_10 || this == FORWARD_10 || this == REWIND_30 || this == FORWARD_30 ||
            this == PREV_CHAPTER || this == NEXT_CHAPTER
}

/** The three configurable action slots plus skip visibility (spec §37). */
data class MediaButtonConfig(
    val showSkipPrevious: Boolean = true,
    val showSkipNext: Boolean = true,
    val action1: MediaAction = MediaAction.TOGGLE_FAVORITE,
    val action2: MediaAction = MediaAction.CYCLE_REPEAT,
    val action3: MediaAction = MediaAction.CYCLE_SHUFFLE,
) {
    val customActions: List<MediaAction>
        get() = listOf(action1, action2, action3).filter { it != MediaAction.NONE }
}

/** Headset multi-click mapping (spec §18). */
data class HeadsetConfig(
    val singleClick: MediaAction = MediaAction.NONE,   // NONE = play/pause (default system behaviour)
    val doubleClick: MediaAction = MediaAction.FORWARD_10,
    val tripleClick: MediaAction = MediaAction.REWIND_10,
    val slowerDetection: Boolean = false,
)

/**
 * Resolves seek-style actions to an absolute target position (spec §18, §37). Chapter actions use
 * the common "within 3 s → jump to the current chapter start, otherwise to the previous one" rule.
 * Pure and unit-testable.
 */
object MediaActionResolver {

    private const val CHAPTER_RESTART_WINDOW_MS = 3_000L

    /**
     * The new position for a seek action, or null if the action isn't a seek (or a chapter action
     * was requested with no chapters — the caller should skip track instead).
     */
    fun resolveSeekTarget(
        action: MediaAction,
        positionMs: Long,
        durationMs: Long,
        chapters: ChapterInfo? = null,
    ): Long? = when (action) {
        MediaAction.REWIND_10 -> (positionMs - 10_000).coerceAtLeast(0)
        MediaAction.FORWARD_10 -> (positionMs + 10_000).coerceAtMost(durationMs)
        MediaAction.REWIND_30 -> (positionMs - 30_000).coerceAtLeast(0)
        MediaAction.FORWARD_30 -> (positionMs + 30_000).coerceAtMost(durationMs)
        MediaAction.PREV_CHAPTER -> prevChapterTarget(positionMs, chapters)
        MediaAction.NEXT_CHAPTER -> nextChapterTarget(positionMs, durationMs, chapters)
        else -> null
    }

    private fun prevChapterTarget(positionMs: Long, chapters: ChapterInfo?): Long? {
        val current = chapters?.chapterAt(positionMs) ?: return null
        val intoChapter = positionMs - current.startMs
        if (intoChapter > CHAPTER_RESTART_WINDOW_MS) return current.startMs
        val prev = chapters.chapters.getOrNull(current.index - 1) ?: return current.startMs
        return prev.startMs
    }

    private fun nextChapterTarget(positionMs: Long, durationMs: Long, chapters: ChapterInfo?): Long? {
        val current = chapters?.chapterAt(positionMs) ?: return null
        val next = chapters.chapters.getOrNull(current.index + 1) ?: return durationMs
        return next.startMs
    }
}
