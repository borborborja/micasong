package com.micasong.player.data.smart

import com.micasong.player.data.model.Track
import kotlin.random.Random

/**
 * Personal-mix generator (spec §17). Reproduces the documented algorithm:
 *  1. Drop tracks excluded from mixes, recently played, and those rated 1-2★.
 *  2. Bucket the rest by listening habits (favorites / most-played / best-rated → 3★+ → rest).
 *  3. Draw a size-limited selection weighted toward the stronger buckets.
 *  4. Re-sequence for a balanced spread of artists and albums (no clustering).
 *
 * Ratings use MiCaSong's half-star 0..10 scale (1★ = 2, 3★ = 6, 5★ = 10).
 */
object PersonalMixGenerator {

    // Rough draw proportions per bucket; tuned to favour habits while keeping variety.
    private const val TOP_SHARE = 0.5
    private const val MID_SHARE = 0.3

    fun generate(
        candidates: List<Track>,
        size: Int,
        recentlyPlayedIds: Set<Long> = emptySet(),
        random: Random = Random.Default,
    ): List<Track> {
        if (size <= 0) return emptyList()

        val eligible = candidates.filter {
            !it.excludedFromMixes &&
                it.id !in recentlyPlayedIds &&
                it.userRating !in 1..4   // drop explicit 1-2★
        }
        if (eligible.isEmpty()) return emptyList()

        val top = eligible.filter { it.isFavorite || it.userRating >= 8 || it.playCount >= 5 }
        val mid = eligible.filter { it !in top && it.userRating in 6..7 }
        val rest = eligible.filter { it !in top && it !in mid }

        val picked = LinkedHashSet<Track>()
        drawInto(picked, top.shuffled(random), (size * TOP_SHARE).toInt())
        drawInto(picked, mid.shuffled(random), (size * MID_SHARE).toInt())
        drawInto(picked, rest.shuffled(random), size)          // fill remainder from the rest
        // Backfill from anything eligible if buckets were thin.
        drawInto(picked, eligible.shuffled(random), size)

        return spread(picked.take(size), random)
    }

    private fun drawInto(target: LinkedHashSet<Track>, source: List<Track>, want: Int) {
        var added = 0
        for (t in source) {
            if (added >= want) break
            if (target.add(t)) added++
        }
    }

    /**
     * Greedy re-sequencing to avoid consecutive tracks from the same artist/album, giving the
     * "balanced distribution of albums/artists" the spec calls for.
     */
    private fun spread(tracks: List<Track>, random: Random): List<Track> {
        if (tracks.size <= 2) return tracks
        val pool = tracks.shuffled(random).toMutableList()
        val result = ArrayList<Track>(pool.size)
        var lastArtist: Long? = null
        var lastAlbum: Long? = null
        while (pool.isNotEmpty()) {
            val idx = pool.indexOfFirst { it.artistId != lastArtist && it.albumId != lastAlbum }
                .let { if (it >= 0) it else pool.indexOfFirst { t -> t.artistId != lastArtist } }
                .let { if (it >= 0) it else 0 }
            val next = pool.removeAt(idx)
            result += next
            lastArtist = next.artistId
            lastAlbum = next.albumId
        }
        return result
    }
}
