package com.micasong.player.smart

import com.micasong.player.data.smart.WeightedShuffle
import com.micasong.player.track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class WeightedShuffleTest {

    private fun adjacentSameArtist(ids: List<Long>): Int =
        ids.zipWithNext().count { (a, b) -> a == b }

    @Test
    fun `result is a permutation of the input`() {
        val tracks = (1..20L).map { track(it, artistId = it % 4) }
        val shuffled = WeightedShuffle.shuffleTracks(tracks, Random(1))
        assertEquals(tracks.map { it.id }.toSet(), shuffled.map { it.id }.toSet())
        assertEquals(tracks.size, shuffled.size)
    }

    @Test
    fun `balanced two-artist list alternates perfectly`() {
        val tracks = (1..10L).map { track(it, artistId = if (it <= 5) 1 else 2) }
        val shuffled = WeightedShuffle.shuffleTracks(tracks, Random(2))
        // 5 of artist 1 and 5 of artist 2 → optimal spread has zero adjacent same-artist.
        assertEquals(0, adjacentSameArtist(shuffled.map { it.artistId!! }))
    }

    @Test
    fun `heavily skewed artist still spread out`() {
        // 8 tracks of artist 1, 2 of artist 2 → some adjacency unavoidable, but minimised.
        val tracks = (1..10L).map { track(it, artistId = if (it <= 8) 1 else 2) }
        val shuffled = WeightedShuffle.shuffleTracks(tracks, Random(3))
        // Best case interleaves the two rare tracks → at most 6 adjacent same-artist pairs.
        assertTrue(adjacentSameArtist(shuffled.map { it.artistId!! }) <= 6)
    }

    @Test
    fun `deterministic for a fixed seed`() {
        val tracks = (1..15L).map { track(it, artistId = it % 3) }
        val a = WeightedShuffle.shuffleTracks(tracks, Random(9)).map { it.id }
        val b = WeightedShuffle.shuffleTracks(tracks, Random(9)).map { it.id }
        assertEquals(a, b)
    }

    @Test
    fun `spreads albums within an artist`() {
        // One artist, two albums (5 each). Albums should interleave too.
        val tracks = (1..10L).map { track(it, artistId = 1, albumId = if (it <= 5) 100 else 200) }
        val shuffled = WeightedShuffle.shuffleTracks(tracks, Random(4))
        val albumAdjacent = shuffled.map { it.albumId!! }.zipWithNext().count { (a, b) -> a == b }
        assertEquals(0, albumAdjacent)
    }

    @Test
    fun `handles tiny and empty inputs`() {
        assertTrue(WeightedShuffle.shuffleTracks(emptyList()).isEmpty())
        assertEquals(1, WeightedShuffle.shuffleTracks(listOf(track(1))).size)
    }
}
