package com.micasong.player.theme

import com.micasong.player.data.smart.SmartQueueMode
import com.micasong.player.data.theme.Profiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileTest {

    @Test
    fun `music mode bundles style queue and filters`() {
        val music = Profiles.musicMode()
        assertEquals("Modern", music.appStyle.name)
        assertTrue(music.smartQueue.enabled)
        assertEquals(SmartQueueMode.ARTIST, music.smartQueue.mode)
        assertTrue("music" in music.filters.mediaTypes)
        assertFalse(music.isAudiobookMode)
    }

    @Test
    fun `audiobook mode is distinct`() {
        val ab = Profiles.audiobookMode()
        assertTrue(ab.isAudiobookMode)
        assertFalse(ab.smartQueue.enabled)
        assertEquals("Old School Basic", ab.appStyle.name)
    }

    @Test
    fun `defaults contain both modes`() {
        val ids = Profiles.defaults().map { it.id }
        assertEquals(listOf("music", "audiobook"), ids)
    }

    @Test
    fun `json round trip preserves the whole bundle`() {
        val music = Profiles.musicMode()
        val restored = Profiles.fromJson(Profiles.toJson(music))
        assertEquals(music, restored)
    }

    @Test
    fun `invalid json returns null`() {
        assertNull(Profiles.fromJson(null))
        assertNull(Profiles.fromJson("garbage"))
    }
}
