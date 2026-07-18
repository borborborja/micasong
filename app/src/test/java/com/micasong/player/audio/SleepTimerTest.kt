package com.micasong.player.audio

import com.micasong.player.data.audio.SleepTimer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepTimerTest {

    @Test
    fun `start sets remaining and active`() {
        val s = SleepTimer.start(30_000)
        assertTrue(s.active)
        assertEquals(30_000L, s.remainingMs)
        assertFalse(s.expired)
        assertEquals(30L, s.remainingSeconds)
    }

    @Test
    fun `tick decrements toward expiry`() {
        var s = SleepTimer.start(10_000)
        s = SleepTimer.tick(s, 4_000)
        assertEquals(6_000L, s.remainingMs)
        assertFalse(s.expired)
        s = SleepTimer.tick(s, 6_000)
        assertEquals(0L, s.remainingMs)
        assertTrue(s.expired)
    }

    @Test
    fun `tick never goes negative`() {
        val s = SleepTimer.tick(SleepTimer.start(1_000), 5_000)
        assertEquals(0L, s.remainingMs)
    }

    @Test
    fun `immediate mode stops as soon as expired`() {
        val s = SleepTimer.tick(SleepTimer.start(1_000), 1_000)
        assertTrue(SleepTimer.shouldStopNow(s, currentSongEnded = false))
    }

    @Test
    fun `end of song mode waits for the song to end`() {
        val s = SleepTimer.tick(SleepTimer.start(1_000, endOfSong = true), 1_000)
        assertTrue(s.expired)
        assertFalse(SleepTimer.shouldStopNow(s, currentSongEnded = false))   // still playing → keep going
        assertTrue(SleepTimer.shouldStopNow(s, currentSongEnded = true))     // song ended → stop
    }

    @Test
    fun `not expired never stops`() {
        val s = SleepTimer.tick(SleepTimer.start(10_000), 3_000)
        assertFalse(SleepTimer.shouldStopNow(s, currentSongEnded = true))
    }

    @Test
    fun `cancel clears the timer`() {
        val s = SleepTimer.cancel()
        assertFalse(s.active)
        assertFalse(SleepTimer.shouldStopNow(SleepTimer.tick(s, 1000), currentSongEnded = true))
    }

    @Test
    fun `extend adds time and clears expiry`() {
        var s = SleepTimer.tick(SleepTimer.start(1_000), 1_000)   // expired
        s = SleepTimer.extend(s, 5_000)
        assertEquals(5_000L, s.remainingMs)
        assertFalse(s.expired)
    }
}
