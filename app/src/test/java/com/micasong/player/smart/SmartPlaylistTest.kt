package com.micasong.player.smart

import com.micasong.player.data.smart.FilterNode
import com.micasong.player.data.smart.FilterOperator
import com.micasong.player.data.smart.FilterTarget
import com.micasong.player.data.smart.MatchMode
import com.micasong.player.data.smart.SmartPlaylistDefinition
import com.micasong.player.data.smart.SortDirection
import com.micasong.player.data.smart.SortField
import com.micasong.player.track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartPlaylistTest {

    private val library = listOf(
        track(1, title = "C", rating = 10, playCount = 3, year = 2020),
        track(2, title = "A", rating = 8, playCount = 10, year = 2019),
        track(3, title = "B", rating = 2, playCount = 1, year = 2005),
        track(4, title = "D", rating = 6, playCount = 7, year = 2021),
    )

    @Test
    fun `filter sort and limit applied in order`() {
        val def = SmartPlaylistDefinition(
            filter = FilterNode.Group(
                MatchMode.ALL,
                listOf(FilterNode.Rule(FilterTarget.RATING, FilterOperator.GREATER_EQUALS, "6")),
            ),
            sortField = SortField.PLAY_COUNT,
            sortDirection = SortDirection.DESC,
            limit = 2,
        )
        val result = def.apply(library)
        assertEquals(2, result.size)
        // ratings >= 6 are tracks 1,2,4 → by playCount desc: 2 (10), 4 (7) → limit 2
        assertEquals(listOf(2L, 4L), result.map { it.id })
    }

    @Test
    fun `title ascending sort`() {
        val def = SmartPlaylistDefinition(sortField = SortField.TITLE, sortDirection = SortDirection.ASC)
        val result = def.apply(library)
        assertEquals(listOf("A", "B", "C", "D"), result.map { it.title })
    }

    @Test
    fun `stable random is reproducible for same seed`() {
        val def = SmartPlaylistDefinition(sortField = SortField.STABLE_RANDOM, stableSeed = 42L)
        assertEquals(def.apply(library).map { it.id }, def.apply(library).map { it.id })
    }

    @Test
    fun `json round trip preserves definition`() {
        val def = SmartPlaylistDefinition(
            filter = FilterNode.Group(
                MatchMode.ANY,
                listOf(FilterNode.Rule(FilterTarget.FAVORITE, FilterOperator.EQUALS, "true")),
            ),
            sortField = SortField.YEAR,
            sortDirection = SortDirection.DESC,
            limit = 50,
        )
        val json = SmartPlaylistDefinition.toJson(def)
        val restored = SmartPlaylistDefinition.fromJson(json)
        assertEquals(def, restored)
    }

    @Test
    fun `empty group matches everything`() {
        val def = SmartPlaylistDefinition(filter = FilterNode.Group(MatchMode.ALL, emptyList()))
        assertEquals(library.size, def.apply(library).size)
    }
}
