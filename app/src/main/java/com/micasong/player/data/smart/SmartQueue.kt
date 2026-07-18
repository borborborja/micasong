package com.micasong.player.data.smart

import com.micasong.player.data.model.Track
import kotlin.random.Random

/** Smart Queue modes (spec §16): extend the queue at the end, potentially indefinitely. */
enum class SmartQueueMode { GENRE, ARTIST, RANDOM }

/**
 * Smart Queue (spec §16). Given the tail of the current queue and the library, it produces a
 * batch of tracks to append at the end — matching the genres or artists of the recent tracks,
 * or fully random. Already-queued tracks are avoided so the extension stays fresh.
 */
object SmartQueueExtender {

    fun extend(
        recentTracks: List<Track>,
        library: List<Track>,
        mode: SmartQueueMode,
        count: Int,
        alreadyQueuedIds: Set<Long> = recentTracks.map { it.id }.toSet(),
        random: Random = Random.Default,
    ): List<Track> {
        if (count <= 0) return emptyList()
        val pool = library.filter { it.id !in alreadyQueuedIds && !it.excludedFromMixes }
        if (pool.isEmpty()) return emptyList()

        val candidates = when (mode) {
            SmartQueueMode.RANDOM -> pool
            SmartQueueMode.GENRE -> {
                val genres = recentTracks.mapNotNull { it.genre?.lowercase() }.toSet()
                pool.filter { it.genre?.lowercase() in genres }.ifEmpty { pool }
            }
            SmartQueueMode.ARTIST -> {
                val artists = recentTracks.mapNotNull { it.artistId }.toSet()
                pool.filter { it.artistId in artists }.ifEmpty { pool }
            }
        }
        return candidates.shuffled(random).take(count)
    }
}
