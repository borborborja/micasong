package com.micasong.player.sync

import com.micasong.player.data.sync.SyncDiffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncDifferTest {

    private data class Item(val id: Long, val version: Int)

    private fun diff(old: List<Item>, new: List<Item>) =
        SyncDiffer.diff(old, new, keyOf = { it.id }, contentEquals = { a, b -> a.version == b.version })

    @Test
    fun `detects added removed changed and unchanged`() {
        val old = listOf(Item(1, 1), Item(2, 1), Item(3, 1))
        val new = listOf(Item(1, 1), Item(2, 2), Item(4, 1))   // 1 same, 2 changed, 3 removed, 4 added
        val d = diff(old, new)
        assertEquals(listOf(4L), d.added.map { it.id })
        assertEquals(listOf(3L), d.removed.map { it.id })
        assertEquals(listOf(2L), d.changed.map { it.id })
        assertEquals(1, d.unchangedCount)
        assertEquals(3, d.touchedCount)
    }

    @Test
    fun `empty old means everything added`() {
        val d = diff(emptyList(), listOf(Item(1, 1), Item(2, 1)))
        assertEquals(2, d.added.size)
        assertTrue(d.removed.isEmpty())
        assertTrue(d.changed.isEmpty())
    }

    @Test
    fun `empty new means everything removed`() {
        val d = diff(listOf(Item(1, 1), Item(2, 1)), emptyList())
        assertEquals(2, d.removed.size)
        assertTrue(d.added.isEmpty())
    }

    @Test
    fun `identical snapshots produce an empty diff`() {
        val snapshot = listOf(Item(1, 1), Item(2, 5))
        val d = diff(snapshot, snapshot)
        assertTrue(d.isEmpty)
        assertEquals(2, d.unchangedCount)
    }

    @Test
    fun `changed comparator drives change detection not equality`() {
        // Same version → unchanged even though these are different instances.
        val d = diff(listOf(Item(1, 7)), listOf(Item(1, 7)))
        assertTrue(d.changed.isEmpty())
        assertEquals(1, d.unchangedCount)
    }

    @Test
    fun `default content comparator uses value equality`() {
        val d = SyncDiffer.diff(
            old = listOf(Item(1, 1)),
            new = listOf(Item(1, 2)),
            keyOf = { it.id },
        )
        assertEquals(listOf(1L), d.changed.map { it.id })
        assertFalse(d.isEmpty)
    }
}
