package com.micasong.player.session

import com.micasong.player.data.audio.Chapters
import com.micasong.player.data.session.HeadsetConfig
import com.micasong.player.data.session.MediaAction
import com.micasong.player.data.session.MediaActionResolver
import com.micasong.player.data.session.MediaButtonConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaActionsTest {

    private val chapters = Chapters.fromMarkers(
        listOf("A" to 0L, "B" to 60_000L, "C" to 120_000L),
        totalDurationMs = 180_000L,
    )

    @Test
    fun `rewind and forward clamp to bounds`() {
        assertEquals(0L, MediaActionResolver.resolveSeekTarget(MediaAction.REWIND_10, 5_000, 200_000))
        assertEquals(20_000L, MediaActionResolver.resolveSeekTarget(MediaAction.REWIND_10, 30_000, 200_000))
        assertEquals(200_000L, MediaActionResolver.resolveSeekTarget(MediaAction.FORWARD_30, 190_000, 200_000))
        assertEquals(60_000L, MediaActionResolver.resolveSeekTarget(MediaAction.FORWARD_30, 30_000, 200_000))
    }

    @Test
    fun `non seek actions have no target`() {
        assertNull(MediaActionResolver.resolveSeekTarget(MediaAction.TOGGLE_FAVORITE, 1000, 200_000))
        assertNull(MediaActionResolver.resolveSeekTarget(MediaAction.STOP, 1000, 200_000))
    }

    @Test
    fun `prev chapter restarts current chapter when past the window`() {
        // 70s → in chapter B (60s..120s), 10s in (> 3s) → jump to B start.
        assertEquals(60_000L, MediaActionResolver.resolveSeekTarget(MediaAction.PREV_CHAPTER, 70_000, 180_000, chapters))
    }

    @Test
    fun `prev chapter jumps to previous when within the window`() {
        // 61s → 1s into B (< 3s) → jump to A start.
        assertEquals(0L, MediaActionResolver.resolveSeekTarget(MediaAction.PREV_CHAPTER, 61_000, 180_000, chapters))
    }

    @Test
    fun `next chapter jumps forward and clamps to duration on last`() {
        assertEquals(120_000L, MediaActionResolver.resolveSeekTarget(MediaAction.NEXT_CHAPTER, 70_000, 180_000, chapters))
        assertEquals(180_000L, MediaActionResolver.resolveSeekTarget(MediaAction.NEXT_CHAPTER, 130_000, 180_000, chapters))
    }

    @Test
    fun `chapter actions without chapters return null`() {
        assertNull(MediaActionResolver.resolveSeekTarget(MediaAction.PREV_CHAPTER, 70_000, 180_000, null))
        assertNull(MediaActionResolver.resolveSeekTarget(MediaAction.NEXT_CHAPTER, 70_000, 180_000, null))
    }

    @Test
    fun `custom actions filter out none slots`() {
        val config = MediaButtonConfig(action2 = MediaAction.NONE)
        assertEquals(
            listOf(MediaAction.TOGGLE_FAVORITE, MediaAction.CYCLE_SHUFFLE),
            config.customActions,
        )
    }

    @Test
    fun `seek flag classification`() {
        assertTrue(MediaAction.REWIND_30.isSeek)
        assertTrue(MediaAction.NEXT_CHAPTER.isSeek)
        assertTrue(!MediaAction.TOGGLE_FAVORITE.isSeek)
    }

    @Test
    fun `headset defaults`() {
        val h = HeadsetConfig()
        assertEquals(MediaAction.NONE, h.singleClick)   // play/pause
        assertEquals(MediaAction.FORWARD_10, h.doubleClick)
        assertEquals(MediaAction.REWIND_10, h.tripleClick)
    }
}
