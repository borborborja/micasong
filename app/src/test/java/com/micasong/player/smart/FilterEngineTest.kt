package com.micasong.player.smart

import com.micasong.player.data.smart.FilterEngine
import com.micasong.player.data.smart.FilterNode
import com.micasong.player.data.smart.FilterOperator
import com.micasong.player.data.smart.FilterTarget
import com.micasong.player.data.smart.MatchMode
import com.micasong.player.track
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterEngineTest {

    @Test
    fun `string contains is case-insensitive`() {
        val rule = FilterNode.Rule(FilterTarget.ARTIST, FilterOperator.CONTAINS, "beat")
        assertTrue(FilterEngine.evaluate(rule, track(1, artist = "The Beatles")))
        assertFalse(FilterEngine.evaluate(rule, track(2, artist = "Queen")))
    }

    @Test
    fun `numeric greater than on year`() {
        val rule = FilterNode.Rule(FilterTarget.YEAR, FilterOperator.GREATER, "2000")
        assertTrue(FilterEngine.evaluate(rule, track(1, year = 2020)))
        assertFalse(FilterEngine.evaluate(rule, track(2, year = 1999)))
        assertFalse(FilterEngine.evaluate(rule, track(3, year = null)))
    }

    @Test
    fun `is missing detects absent thumbnail`() {
        val rule = FilterNode.Rule(FilterTarget.THUMBNAIL, FilterOperator.IS_MISSING)
        assertTrue(FilterEngine.evaluate(rule, track(1, artworkUri = null)))
        assertFalse(FilterEngine.evaluate(rule, track(2, artworkUri = "art://2")))
    }

    @Test
    fun `favorite boolean equals`() {
        val rule = FilterNode.Rule(FilterTarget.FAVORITE, FilterOperator.EQUALS, "true")
        assertTrue(FilterEngine.evaluate(rule, track(1, favorite = true)))
        assertFalse(FilterEngine.evaluate(rule, track(2, favorite = false)))
    }

    @Test
    fun `ALL group requires every child`() {
        val group = FilterNode.Group(
            match = MatchMode.ALL,
            children = listOf(
                FilterNode.Rule(FilterTarget.FAVORITE, FilterOperator.EQUALS, "true"),
                FilterNode.Rule(FilterTarget.YEAR, FilterOperator.GREATER_EQUALS, "2010"),
            ),
        )
        assertTrue(FilterEngine.evaluate(group, track(1, favorite = true, year = 2015)))
        assertFalse(FilterEngine.evaluate(group, track(2, favorite = true, year = 2000)))
    }

    @Test
    fun `ANY group requires at least one child`() {
        val group = FilterNode.Group(
            match = MatchMode.ANY,
            children = listOf(
                FilterNode.Rule(FilterTarget.GENRE, FilterOperator.EQUALS, "Jazz"),
                FilterNode.Rule(FilterTarget.RATING, FilterOperator.GREATER_EQUALS, "8"),
            ),
        )
        assertTrue(FilterEngine.evaluate(group, track(1, genre = "Jazz", rating = 0)))
        assertTrue(FilterEngine.evaluate(group, track(2, genre = "Rock", rating = 10)))
        assertFalse(FilterEngine.evaluate(group, track(3, genre = "Rock", rating = 4)))
    }

    @Test
    fun `nested groups compose`() {
        val filter = FilterNode.Group(
            match = MatchMode.ALL,
            children = listOf(
                FilterNode.Rule(FilterTarget.ARTIST, FilterOperator.CONTAINS, "a"),
                FilterNode.Group(
                    match = MatchMode.ANY,
                    children = listOf(
                        FilterNode.Rule(FilterTarget.YEAR, FilterOperator.LESS, "1990"),
                        FilterNode.Rule(FilterTarget.FAVORITE, FilterOperator.EQUALS, "true"),
                    ),
                ),
            ),
        )
        assertTrue(FilterEngine.evaluate(filter, track(1, artist = "Aretha", year = 1985)))
        assertTrue(FilterEngine.evaluate(filter, track(2, artist = "Aretha", favorite = true, year = 2020)))
        assertFalse(FilterEngine.evaluate(filter, track(3, artist = "Aretha", year = 2020)))
    }
}
