package com.micasong.player.nowplaying

import com.micasong.player.data.audio.Chapters
import com.micasong.player.data.audio.SleepTimer
import com.micasong.player.data.nowplaying.NowPlayingContext
import com.micasong.player.data.nowplaying.NowPlayingFields
import com.micasong.player.data.template.StringTemplateEngine
import com.micasong.player.track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NowPlayingFieldsTest {

    private val ctx = NowPlayingContext(
        track = track(1, title = "Viva La Vida", artist = "Coldplay", album = "Viva La Vida", year = 2008),
        positionMs = 65_000,
        durationMs = 242_000,
        isPlaying = true,
        queueIndex = 2,
        queueSize = 12,
    )

    @Test
    fun `maps core metadata and formatted times`() {
        val f = NowPlayingFields.build(ctx)
        assertEquals("Viva La Vida", f["title"])
        assertEquals("Coldplay", f["artist"])
        assertEquals("2008", f["year"])
        assertEquals("1:05", f["position"])
        assertEquals("4:02", f["duration"])
        assertEquals("2:57", f["remaining"])
        assertEquals("3", f["queue.index"])       // 1-based
        assertEquals("12", f["queue.size"])
        assertEquals("false", f["player.paused"])
    }

    @Test
    fun `hires flag reflects sample depth`() {
        assertEquals("false", NowPlayingFields.build(ctx)["hires"])
    }

    @Test
    fun `chapter fields present only with chapters`() {
        assertNull(NowPlayingFields.build(ctx)["chapter.title"])
        val info = Chapters.fromMarkers(listOf("Intro" to 0L, "Part 1" to 60_000L), totalDurationMs = 242_000)
        val withChapter = ctx.copy(chapter = Chapters.navStateAt(info, 65_000))
        val f = NowPlayingFields.build(withChapter)
        assertEquals("Part 1", f["chapter.title"])
        assertEquals("2", f["chapter.index"])
    }

    @Test
    fun `sleep timer fields present only when active`() {
        assertNull(NowPlayingFields.build(ctx)["sleep.timer.seconds"])
        val withTimer = ctx.copy(sleepTimer = SleepTimer.start(90_000, endOfSong = true))
        val f = NowPlayingFields.build(withTimer)
        assertEquals("90", f["sleep.timer.seconds"])
        assertEquals("true", f["sleep.timer.eos"])
    }

    @Test
    fun `fields drive the string template engine end to end`() {
        val f = NowPlayingFields.build(ctx)
        val rendered = StringTemplateEngine.render("%title% — %artist%{ (%year%)}", f)
        assertEquals("Viva La Vida — Coldplay (2008)", rendered)
    }

    @Test
    fun `template conditionally shows chapter when present`() {
        val template = "%title%{ · %chapter.title%}"
        assertEquals("Viva La Vida", StringTemplateEngine.render(template, NowPlayingFields.build(ctx)))
        val info = Chapters.fromMarkers(listOf("Intro" to 0L), totalDurationMs = 242_000)
        val withChapter = ctx.copy(chapter = Chapters.navStateAt(info, 1000))
        assertEquals(
            "Viva La Vida · Intro",
            StringTemplateEngine.render(template, NowPlayingFields.build(withChapter)),
        )
    }

    @Test
    fun `renderer fields absent by default`() {
        val f = NowPlayingFields.build(ctx)
        assertNull(f["renderer.current"])
        assertFalse(f.containsKey("nonexistent"))
    }
}
