package com.micasong.player.provider

import com.micasong.player.data.provider.StableId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StableIdTest {

    @Test
    fun `adjacent ids do not collide (the bug this replaced)`() {
        assertNotEquals(StableId.of("a1"), StableId.of("a2"))
        assertNotEquals(StableId.of("al1"), StableId.of("al2"))
        assertNotEquals(StableId.of("100"), StableId.of("101"))
    }

    @Test
    fun `deterministic`() {
        assertEquals(StableId.of("radiohead-guid"), StableId.of("radiohead-guid"))
    }

    @Test
    fun `always non-negative and non-zero`() {
        for (s in listOf("", "a", "0", "very-long-server-identifier-abc123", "áéí")) {
            val id = StableId.of(s)
            assertTrue("id must be > 0 for '$s'", id > 0)
        }
    }

    @Test
    fun `no collisions across a large adjacent range`() {
        val ids = (1..2000).map { StableId.of("item$it") }
        assertEquals(2000, ids.toSet().size)
    }
}
