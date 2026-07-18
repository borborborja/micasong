package com.micasong.player.data.cache

import com.micasong.player.data.model.Track

/**
 * Offline cache model and policy engine (spec §34-35). Three tiers coexist:
 *  - **Playback cache** — transient pre-load for flaky networks,
 *  - **Rolling cache** — size-capped, evicts the least-recently-accessed first,
 *  - **Permanent cache** — never auto-removed, no size limit.
 *
 * Automatic rules (artists / genres / playlists / favorites) drive what gets cached; when a
 * track stops matching every rule its auto-cached copy is removed. All logic here is pure and
 * unit-testable; the actual file I/O lives in the (device-only) download manager.
 */
enum class CacheTier { PLAYBACK, ROLLING, PERMANENT }

data class CachedTrack(
    val trackId: Long,
    val tier: CacheTier,
    val sizeBytes: Long,
    val lastAccess: Long,
    /** True when added by an automatic rule (so it may be removed when it leaves the rule). */
    val auto: Boolean = false,
)

/** Automatic offline-cache rules (spec §34). */
data class AutoCacheRules(
    val artistIds: Set<Long> = emptySet(),
    val genres: Set<String> = emptySet(),
    val playlistTrackIds: Set<Long> = emptySet(),
    val cacheFavorites: Boolean = false,
) {
    val isEmpty: Boolean
        get() = artistIds.isEmpty() && genres.isEmpty() && playlistTrackIds.isEmpty() && !cacheFavorites
}

/** The delta the sync engine should apply to the auto-cached set. */
data class CachePlan(val toAdd: Set<Long>, val toRemove: Set<Long>)

object RollingCache {
    /**
     * Ids to evict so the rolling tier fits within [maxBytes], least-recently-accessed first.
     * Permanent and playback items are never counted or evicted here.
     */
    fun evictionPlan(items: List<CachedTrack>, maxBytes: Long): List<Long> {
        val rolling = items.filter { it.tier == CacheTier.ROLLING }.sortedBy { it.lastAccess }
        var total = rolling.sumOf { it.sizeBytes }
        val evict = mutableListOf<Long>()
        var i = 0
        while (total > maxBytes && i < rolling.size) {
            evict += rolling[i].trackId
            total -= rolling[i].sizeBytes
            i++
        }
        return evict
    }

    fun rollingSize(items: List<CachedTrack>): Long =
        items.filter { it.tier == CacheTier.ROLLING }.sumOf { it.sizeBytes }
}

object AutoCacheEngine {

    /** Does a track match any active auto-cache rule? */
    fun matches(track: Track, rules: AutoCacheRules): Boolean {
        if (rules.isEmpty) return false
        val genres = rules.genres.map { it.lowercase() }
        return track.artistId in rules.artistIds ||
            (track.genre?.lowercase() in genres) ||
            track.id in rules.playlistTrackIds ||
            (rules.cacheFavorites && track.isFavorite)
    }

    /**
     * Compute which tracks to newly cache and which auto-cached tracks to remove because they no
     * longer match any rule (spec §34: "the file is removed when the item leaves the rule").
     */
    fun reconcile(library: List<Track>, rules: AutoCacheRules, currentlyAutoCachedIds: Set<Long>): CachePlan {
        val shouldCache = library.filter { matches(it, rules) }.map { it.id }.toSet()
        return CachePlan(
            toAdd = shouldCache - currentlyAutoCachedIds,
            toRemove = currentlyAutoCachedIds - shouldCache,
        )
    }
}
