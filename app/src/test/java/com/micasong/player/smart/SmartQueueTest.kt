package com.micasong.player.smart

import com.micasong.player.data.smart.SmartQueueExtender
import com.micasong.player.data.smart.SmartQueueMode
import com.micasong.player.track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SmartQueueTest {

    private val library = listOf(
        track(1, artist = "A", artistId = 1, genre = "Rock"),
        track(2, artist = "A", artistId = 1, genre = "Rock"),
        track(3, artist = "B", artistId = 2, genre = "Jazz"),
        track(4, artist = "C", artistId = 3, genre = "Rock"),
        track(5, artist = "B", artistId = 2, genre = "Jazz"),
    )

    @Test
    fun `genre mode keeps to recent genres`() {
        val recent = listOf(track(1, genre = "Rock"))
        val ext = SmartQueueExtender.extend(recent, library, SmartQueueMode.GENRE, count = 5, random = Random(1))
        assertTrue(ext.all { it.genre == "Rock" })
        assertTrue(ext.none { it.id == 1L })   // recent track excluded
    }

    @Test
    fun `artist mode keeps to recent artists`() {
        val recent = listOf(track(3, artist = "B", artistId = 2))
        val ext = SmartQueueExtender.extend(recent, library, SmartQueueMode.ARTIST, count = 5, random = Random(1))
        assertTrue(ext.all { it.artistId == 2L })
    }

    @Test
    fun `count is respected`() {
        val ext = SmartQueueExtender.extend(emptyList(), library, SmartQueueMode.RANDOM, count = 2, random = Random(3))
        assertEquals(2, ext.size)
    }

    @Test
    fun `excludes already queued`() {
        val ext = SmartQueueExtender.extend(
            recentTracks = emptyList(),
            library = library,
            mode = SmartQueueMode.RANDOM,
            count = 10,
            alreadyQueuedIds = setOf(1, 2, 3),
            random = Random(4),
        )
        assertTrue(ext.none { it.id in setOf(1L, 2L, 3L) })
    }

    @Test
    fun `deterministic for a fixed seed`() {
        val a = SmartQueueExtender.extend(emptyList(), library, SmartQueueMode.RANDOM, 3, random = Random(9))
        val b = SmartQueueExtender.extend(emptyList(), library, SmartQueueMode.RANDOM, 3, random = Random(9))
        assertEquals(a.map { it.id }, b.map { it.id })
    }
}
