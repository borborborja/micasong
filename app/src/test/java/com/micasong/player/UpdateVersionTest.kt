package com.micasong.player

import com.micasong.player.data.update.UpdateManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Verifies the update version comparison (handles the -foss/-full version suffixes). */
class UpdateVersionTest {

    @Test
    fun `detects a newer release`() {
        assertTrue(UpdateManager.isNewer("0.0.15", "0.0.14"))
        assertTrue(UpdateManager.isNewer("0.1.0", "0.0.14"))
        assertTrue(UpdateManager.isNewer("1.0.0", "0.0.14-foss"))
        assertTrue(UpdateManager.isNewer("0.0.15", "0.0.14-full"))
    }

    @Test
    fun `same or older is not newer`() {
        assertFalse(UpdateManager.isNewer("0.0.14", "0.0.14-foss"))
        assertFalse(UpdateManager.isNewer("0.0.13", "0.0.14"))
        assertFalse(UpdateManager.isNewer("0.0.9", "0.0.14"))
    }
}
