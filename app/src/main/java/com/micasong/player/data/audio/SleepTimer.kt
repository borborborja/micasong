package com.micasong.player.data.audio

/**
 * Immutable sleep-timer state (spec §27: `sleep.timer.seconds` / `sleep.timer.eos`). When the
 * countdown reaches zero the timer either stops playback immediately or, in *end-of-song* mode,
 * waits for the current track to finish first.
 */
data class SleepTimerState(
    val active: Boolean = false,
    val remainingMs: Long = 0L,
    val endOfSong: Boolean = false,
    val expired: Boolean = false,
) {
    val remainingSeconds: Long get() = (remainingMs + 999) / 1000
}

/** Pure state machine for the sleep timer; the service ticks it and reacts to [shouldStopNow]. */
object SleepTimer {

    fun start(durationMs: Long, endOfSong: Boolean = false): SleepTimerState =
        SleepTimerState(active = true, remainingMs = durationMs.coerceAtLeast(0), endOfSong = endOfSong, expired = durationMs <= 0)

    fun cancel(): SleepTimerState = SleepTimerState()

    /** Advance the countdown by [elapsedMs]; a no-op once cancelled. */
    fun tick(state: SleepTimerState, elapsedMs: Long): SleepTimerState {
        if (!state.active) return state
        val remaining = (state.remainingMs - elapsedMs).coerceAtLeast(0)
        return state.copy(remainingMs = remaining, expired = remaining == 0L)
    }

    /** Add more time to a running (or expired) timer. */
    fun extend(state: SleepTimerState, extraMs: Long): SleepTimerState {
        if (!state.active) return start(extraMs, state.endOfSong)
        val remaining = (state.remainingMs + extraMs).coerceAtLeast(0)
        return state.copy(remainingMs = remaining, expired = false)
    }

    /**
     * Whether playback should stop right now. In end-of-song mode the timer waits for
     * [currentSongEnded] before firing; otherwise it fires as soon as it expires.
     */
    fun shouldStopNow(state: SleepTimerState, currentSongEnded: Boolean): Boolean {
        if (!state.active || !state.expired) return false
        return if (state.endOfSong) currentSongEnded else true
    }
}
