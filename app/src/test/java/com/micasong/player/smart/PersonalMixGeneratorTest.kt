package com.micasong.player.smart

import com.micasong.player.data.smart.PersonalMixGenerator
import com.micasong.player.track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PersonalMixGeneratorTest {

    private val library = (1..40L).map { id ->
        track(
            id = id,
            artist = "Artist ${id % 6}",
            artistId = id % 6,
            albumId = id % 10,
            rating = (id % 6).toInt() * 2,   // 0,2,4,6,8,10 cycling
            playCount = (id % 4).toInt(),
            favorite = id % 7 == 0L,
        )
    }

    @Test
    fun `excludes one and two star tracks`() {
        val mix = PersonalMixGenerator.generate(library, size = 30, random = Random(1))
        assertTrue(mix.none { it.userRating in 1..4 })
    }

    @Test
    fun `excludes recently played and excluded-from-mixes`() {
        val recent = setOf(5L, 6L, 7L)
        val withExcluded = library + track(99, rating = 10, excludedFromMixes = true)
        val mix = PersonalMixGenerator.generate(withExcluded, size = 40, recentlyPlayedIds = recent, random = Random(2))
        assertTrue(mix.none { it.id in recent })
        assertTrue(mix.none { it.id == 99L })
    }

    @Test
    fun `respects requested size and has no duplicates`() {
        val mix = PersonalMixGenerator.generate(library, size = 12, random = Random(3))
        assertTrue(mix.size <= 12)
        assertEquals(mix.size, mix.map { it.id }.toSet().size)
    }

    @Test
    fun `deterministic for a fixed seed`() {
        val a = PersonalMixGenerator.generate(library, size = 15, random = Random(7))
        val b = PersonalMixGenerator.generate(library, size = 15, random = Random(7))
        assertEquals(a.map { it.id }, b.map { it.id })
    }

    @Test
    fun `empty candidates yields empty mix`() {
        assertTrue(PersonalMixGenerator.generate(emptyList(), size = 10).isEmpty())
    }
}
