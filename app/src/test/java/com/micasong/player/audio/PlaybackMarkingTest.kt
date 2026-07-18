package com.micasong.player.audio

import com.micasong.player.data.audio.MarkingThresholds
import com.micasong.player.data.audio.PlaybackMarking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackMarkingTest {

    private val dur = 300_000L   // 5 minutes

    @Test
    fun `counts as played past the scrobble threshold`() {
        val c = PlaybackMarking.evaluate(positionMs = 290_000, durationMs = dur)   // ~96%
        assertTrue(c.countsAsPlayed)
        assertFalse(c.countsAsSkip)
    }

    @Test
    fun `not played before the threshold`() {
        assertFalse(PlaybackMarking.evaluate(positionMs = 200_000, durationMs = dur).countsAsPlayed)   // ~66%
    }

    @Test
    fun `skip counts only when abandoned early by the user`() {
        assertTrue(PlaybackMarking.evaluate(120_000, dur, userSkipped = true).countsAsSkip)     // 40% → skip
        assertFalse(PlaybackMarking.evaluate(180_001, dur, userSkipped = true).countsAsSkip)    // >50% → not a skip
        assertFalse(PlaybackMarking.evaluate(120_000, dur, userSkipped = false).countsAsSkip)   // natural, not a skip
    }

    @Test
    fun `resume saved after min time but not once basically finished`() {
        assertFalse(PlaybackMarking.shouldSaveResume(5_000, dur))       // below 10s min
        assertTrue(PlaybackMarking.shouldSaveResume(60_000, dur))       // 20% in, worth saving
        assertFalse(PlaybackMarking.shouldSaveResume(297_000, dur))     // 99% → finished, no resume
    }

    @Test
    fun `audiobook resume rolls back`() {
        val pos = PlaybackMarking.resumePosition(savedMs = 120_000, isAudiobook = true)
        assertEquals(105_000L, pos)   // 15s rollback
    }

    @Test
    fun `music resume does not roll back`() {
        assertEquals(120_000L, PlaybackMarking.resumePosition(120_000, isAudiobook = false))
    }

    @Test
    fun `rollback never goes negative`() {
        assertEquals(0L, PlaybackMarking.resumePosition(5_000, isAudiobook = true))
    }

    @Test
    fun `custom thresholds are honoured`() {
        val t = MarkingThresholds(minPlayedPercentToScrobble = 80, maxSkipPercent = 20)
        assertTrue(PlaybackMarking.evaluate(250_000, dur, t).countsAsPlayed)          // 83% ≥ 80
        assertFalse(PlaybackMarking.evaluate(90_000, dur, t, userSkipped = true).countsAsSkip)  // 30% > 20
    }

    @Test
    fun `zero duration is safe`() {
        val c = PlaybackMarking.evaluate(positionMs = 1000, durationMs = 0)
        assertFalse(c.countsAsPlayed)
    }
}
