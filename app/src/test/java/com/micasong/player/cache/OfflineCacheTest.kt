package com.micasong.player.cache

import com.micasong.player.data.cache.AutoCacheEngine
import com.micasong.player.data.cache.AutoCacheRules
import com.micasong.player.data.cache.CacheTier
import com.micasong.player.data.cache.CachedTrack
import com.micasong.player.data.cache.RollingCache
import com.micasong.player.track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineCacheTest {

    // ---- Rolling cache eviction ----

    private fun cached(id: Long, size: Long, access: Long, tier: CacheTier = CacheTier.ROLLING) =
        CachedTrack(trackId = id, tier = tier, sizeBytes = size, lastAccess = access)

    @Test
    fun `eviction removes least recently accessed until under budget`() {
        val items = listOf(
            cached(1, size = 100, access = 10),   // oldest
            cached(2, size = 100, access = 20),
            cached(3, size = 100, access = 30),   // newest
        )
        // budget 250 → total 300 → evict oldest (id 1) → 200 <= 250
        assertEquals(listOf(1L), RollingCache.evictionPlan(items, maxBytes = 250))
    }

    @Test
    fun `eviction can drop several items`() {
        val items = (1L..5L).map { cached(it, size = 100, access = it) }
        // total 500, budget 250 → evict 1,2,3 leaving 200
        assertEquals(listOf(1L, 2L, 3L), RollingCache.evictionPlan(items, maxBytes = 250))
    }

    @Test
    fun `permanent items are never evicted or counted`() {
        val items = listOf(
            cached(1, size = 1000, access = 1, tier = CacheTier.PERMANENT),
            cached(2, size = 100, access = 2, tier = CacheTier.ROLLING),
        )
        assertEquals(100L, RollingCache.rollingSize(items))
        assertTrue(RollingCache.evictionPlan(items, maxBytes = 50).none { it == 1L })
    }

    @Test
    fun `nothing evicted when under budget`() {
        val items = listOf(cached(1, 100, 1), cached(2, 100, 2))
        assertTrue(RollingCache.evictionPlan(items, maxBytes = 500).isEmpty())
    }

    // ---- Automatic rules ----

    private val library = listOf(
        track(1, artistId = 10, genre = "Jazz", favorite = false),
        track(2, artistId = 11, genre = "Rock", favorite = true),
        track(3, artistId = 12, genre = "Jazz", favorite = false),
        track(4, artistId = 13, genre = "Pop", favorite = false),
    )

    @Test
    fun `matches by artist genre playlist and favorites`() {
        val rules = AutoCacheRules(artistIds = setOf(13), genres = setOf("jazz"), cacheFavorites = true)
        assertTrue(AutoCacheEngine.matches(library[0], rules))   // genre Jazz
        assertTrue(AutoCacheEngine.matches(library[1], rules))   // favorite
        assertTrue(AutoCacheEngine.matches(library[3], rules))   // artist 13
    }

    @Test
    fun `empty rules match nothing`() {
        assertFalse(AutoCacheEngine.matches(library[0], AutoCacheRules()))
    }

    @Test
    fun `reconcile adds new matches and removes items that left the rule`() {
        val rules = AutoCacheRules(genres = setOf("Jazz"))
        // currently id 4 (Pop) is auto-cached but no longer matches → remove; ids 1,3 are new
        val plan = AutoCacheEngine.reconcile(library, rules, currentlyAutoCachedIds = setOf(4))
        assertEquals(setOf(1L, 3L), plan.toAdd)
        assertEquals(setOf(4L), plan.toRemove)
    }

    @Test
    fun `reconcile keeps still-matching items`() {
        val rules = AutoCacheRules(genres = setOf("Jazz"))
        val plan = AutoCacheEngine.reconcile(library, rules, currentlyAutoCachedIds = setOf(1))
        assertFalse(1L in plan.toRemove)      // still matches → not removed
        assertEquals(setOf(3L), plan.toAdd)
    }
}
