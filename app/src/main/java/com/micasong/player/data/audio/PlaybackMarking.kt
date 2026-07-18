package com.micasong.player.data.audio

/** User-configurable thresholds that govern playback bookkeeping (spec §11, §20). */
data class MarkingThresholds(
    val minPlayedPercentToScrobble: Int = 95,   // % listened before counting a play
    val minResumeSeconds: Int = 10,             // min elapsed before a resume point is worth saving
    val maxSkipPercent: Int = 50,               // skip only counts if abandoned before this %
    val audiobookRollbackSeconds: Int = 15,     // rewind on resume for audiobooks
)

/** What should happen to a track's stats when playback of it ends. */
data class TrackCompletion(
    val countsAsPlayed: Boolean,
    val countsAsSkip: Boolean,
    val saveResume: Boolean,
)

/**
 * Pure decision logic for play counts, skip counts and resume points (spec §11, §20). Keeping
 * these thresholds in one testable place means [com.micasong.player.playback.PlaybackStatsListener]
 * can stay a thin observer.
 */
object PlaybackMarking {

    private fun percent(positionMs: Long, durationMs: Long): Int =
        if (durationMs <= 0) 0 else ((positionMs.toDouble() / durationMs) * 100).toInt().coerceIn(0, 100)

    /**
     * Decide the bookkeeping when a track finishes or is left. [userSkipped] distinguishes a user
     * pressing "next" from a natural end.
     */
    fun evaluate(
        positionMs: Long,
        durationMs: Long,
        thresholds: MarkingThresholds = MarkingThresholds(),
        userSkipped: Boolean = false,
    ): TrackCompletion {
        val pct = percent(positionMs, durationMs)
        val played = pct >= thresholds.minPlayedPercentToScrobble
        val skip = userSkipped && !played && pct <= thresholds.maxSkipPercent
        return TrackCompletion(
            countsAsPlayed = played,
            countsAsSkip = skip,
            saveResume = shouldSaveResume(positionMs, durationMs, thresholds),
        )
    }

    /** A resume point is worth saving once past the minimum time but before the track is complete. */
    fun shouldSaveResume(positionMs: Long, durationMs: Long, thresholds: MarkingThresholds = MarkingThresholds()): Boolean {
        if (positionMs < thresholds.minResumeSeconds * 1000L) return false
        return percent(positionMs, durationMs) < thresholds.minPlayedPercentToScrobble
    }

    /** The position to resume from, applying the audiobook rollback (spec §20). */
    fun resumePosition(savedMs: Long, isAudiobook: Boolean, thresholds: MarkingThresholds = MarkingThresholds()): Long {
        if (!isAudiobook) return savedMs.coerceAtLeast(0)
        return (savedMs - thresholds.audiobookRollbackSeconds * 1000L).coerceAtLeast(0)
    }
}
