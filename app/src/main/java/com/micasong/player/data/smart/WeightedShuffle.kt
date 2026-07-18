package com.micasong.player.data.smart

import com.micasong.player.data.model.Track
import kotlin.random.Random

/**
 * Weighted shuffle (spec §17). Unlike a plain random shuffle, this spreads tracks so the same
 * artist and album don't cluster together. It uses a greedy "least-recently-used" strategy: at
 * each position it picks the remaining track whose artist (then album) was played longest ago,
 * with a random base order breaking ties. Deterministic for a fixed [Random], so it's testable.
 * The user can still choose a truly random shuffle, which just calls [List.shuffled].
 */
object WeightedShuffle {

    fun shuffleTracks(tracks: List<Track>, random: Random = Random.Default): List<Track> =
        shuffle(tracks, artistKey = { it.artistId ?: it.artistName }, albumKey = { it.albumId ?: it.albumName }, random = random)

    fun <T> shuffle(
        items: List<T>,
        artistKey: (T) -> Any?,
        albumKey: (T) -> Any?,
        random: Random = Random.Default,
    ): List<T> {
        if (items.size <= 1) return items.toList()
        val pool = items.shuffled(random).toMutableList()   // random tie-break base
        val result = ArrayList<T>(items.size)
        val artistLast = HashMap<Any?, Int>()
        val albumLast = HashMap<Any?, Int>()
        val never = Int.MIN_VALUE / 2

        var pos = 0
        while (pool.isNotEmpty()) {
            var bestIdx = 0
            var bestScore = Long.MIN_VALUE
            for (i in pool.indices) {
                val artistDist = (pos - (artistLast[artistKey(pool[i])] ?: never)).toLong()
                val albumDist = (pos - (albumLast[albumKey(pool[i])] ?: never)).toLong()
                val score = artistDist * 1_000_000 + albumDist   // artist spread dominates album spread
                if (score > bestScore) {
                    bestScore = score
                    bestIdx = i
                }
            }
            val next = pool.removeAt(bestIdx)
            result += next
            artistLast[artistKey(next)] = pos
            albumLast[albumKey(next)] = pos
            pos++
        }
        return result
    }
}
