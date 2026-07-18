package com.micasong.player.smart

import com.micasong.player.data.smart.SmartFlow
import com.micasong.player.data.smart.SmartFlowMode
import com.micasong.player.track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SmartFlowTest {

    private val library = listOf(
        track(1, artist = "A", artistId = 1, genre = "Rock", year = 1985),
        track(2, artist = "A", artistId = 1, genre = "Rock", year = 1987),
        track(3, artist = "B", artistId = 2, genre = "Jazz", year = 2001),
        track(4, artist = "A", artistId = 1, genre = "Pop", year = 1989),
        track(5, artist = "C", artistId = 3, genre = "Rock", year = 1984),
    )
    private val current = track(1, artist = "A", artistId = 1, genre = "Rock", year = 1985)

    @Test
    fun `shuffle specialist inserts a single track`() {
        val ins = SmartFlow.nextInsertions(current, library, SmartFlowMode.SHUFFLE_SPECIALIST, random = Random(1))
        assertEquals(1, ins.size)
        assertFalse(ins.any { it.id == current.id })
    }

    @Test
    fun `double shot picks another track by the same artist`() {
        val ins = SmartFlow.nextInsertions(current, library, SmartFlowMode.DOUBLE_SHOT, random = Random(1))
        assertEquals(1, ins.size)
        assertEquals(1L, ins.first().artistId)
    }

    @Test
    fun `artist fan locks to the current artist`() {
        val ins = SmartFlow.nextInsertions(current, library, SmartFlowMode.ARTIST_FAN, maxInsertions = 12, random = Random(1))
        assertTrue(ins.isNotEmpty())
        assertTrue(ins.all { it.artistId == 1L })
    }

    @Test
    fun `era enthusiast stays within the decade`() {
        val ins = SmartFlow.nextInsertions(current, library, SmartFlowMode.ERA_ENTHUSIAST, random = Random(1))
        assertTrue(ins.all { (it.year ?: 0) / 10 == 198 })
    }

    @Test
    fun `sonic modes fall back to genre similarity`() {
        val ins = SmartFlow.nextInsertions(current, library, SmartFlowMode.ECHO_MATCH, random = Random(2))
        assertEquals(1, ins.size)
        assertEquals("Rock", ins.first().genre)
    }

    @Test
    fun `sonic modes are flagged`() {
        assertTrue(SmartFlowMode.TRANSITION_MAESTRO.requiresSonicAnalysis)
        assertTrue(SmartFlowMode.ECHO_MATCH.requiresSonicAnalysis)
        assertTrue(SmartFlowMode.STEADY_VIBES.requiresSonicAnalysis)
        assertFalse(SmartFlowMode.DOUBLE_SHOT.requiresSonicAnalysis)
    }

    @Test
    fun `zero insertions requested yields empty`() {
        assertTrue(SmartFlow.nextInsertions(current, library, SmartFlowMode.ARTIST_FAN, maxInsertions = 0).isEmpty())
    }
}
