package com.micasong.player.data.nowplaying

import com.micasong.player.data.audio.ChapterNavState
import com.micasong.player.data.audio.SleepTimerState
import com.micasong.player.data.model.Track

/** Everything the Now Playing template context needs for one render (spec §27). */
data class NowPlayingContext(
    val track: Track,
    val positionMs: Long,
    val durationMs: Long,
    val isPlaying: Boolean,
    val shuffle: Boolean = false,
    val repeatMode: Int = 0,          // 0 off · 1 all · 2 one
    val queueIndex: Int = 0,          // 0-based
    val queueSize: Int = 1,
    val chapter: ChapterNavState? = null,
    val sleepTimer: SleepTimerState? = null,
    val outputName: String? = null,
    val rendererName: String? = null,
    val rendererType: Int? = null,    // API TYPE 0/1/3/6 (spec §36)
)

/**
 * Builds the `%field%` map that [com.micasong.player.data.template.StringTemplateEngine] consumes
 * for the Now Playing screen (spec §27). This is the bridge that turns the current playback state
 * — track metadata, position, queue, chapters, sleep timer, renderer — into the documented field
 * names, so user templates like `%title%{ · %chapter.title%}` resolve. Pure and unit-testable.
 */
object NowPlayingFields {

    fun build(ctx: NowPlayingContext): Map<String, String?> {
        val t = ctx.track
        val fields = LinkedHashMap<String, String?>()

        // Track metadata
        fields["title"] = t.title
        fields["artist"] = t.artistName
        fields["album"] = t.albumName
        fields["albumartist"] = t.albumArtist
        fields["genre"] = t.genre
        fields["year"] = t.year?.toString()
        fields["tracknumber"] = t.trackNumber?.toString()
        fields["discnumber"] = t.discNumber?.toString()
        fields["rating"] = t.userRating.toString()
        fields["filepath"] = t.mediaUri
        fields["filename"] = t.mediaUri.substringAfterLast('/')

        // Format / quality (feeds string.hires / string.lossless / string.lossy)
        fields["codec"] = t.mimeType?.substringAfterLast('/')?.uppercase()
        fields["samplerate"] = t.sampleRate?.toString()
        fields["bitdepth"] = t.bitDepth?.toString()
        fields["hires"] = ((t.bitDepth ?: 0) >= 24 || (t.sampleRate ?: 0) > 48_000).toString()

        // Playback state
        fields["duration"] = time(ctx.durationMs)
        fields["position"] = time(ctx.positionMs)
        fields["remaining"] = time((ctx.durationMs - ctx.positionMs).coerceAtLeast(0))
        fields["player.paused"] = (!ctx.isPlaying).toString()
        fields["player.shuffle.mode"] = ctx.shuffle.toString()
        fields["player.repeat.mode"] = ctx.repeatMode.toString()
        fields["queue.index"] = (ctx.queueIndex + 1).toString()
        fields["queue.size"] = ctx.queueSize.toString()

        // Chapters (spec §19)
        ctx.chapter?.let { c ->
            fields["chapter.count"] = c.count.toString()
            fields["chapter.index"] = c.index.toString()
            fields["chapter.title"] = c.title
            fields["chapter.position"] = time(c.positionInChapterMs)
            fields["chapter.remaining"] = time(c.remainingInChapterMs)
            fields["chapter.duration"] = time(c.chapterDurationMs)
            fields["next.chapter.title"] = c.nextChapterTitle
            fields["chapter.all.position"] = time(c.totalProgressMs)
            fields["chapter.all.duration"] = time(c.totalDurationMs)
        }

        // Sleep timer (spec §27)
        ctx.sleepTimer?.takeIf { it.active }?.let { s ->
            fields["sleep.timer.seconds"] = s.remainingSeconds.toString()
            fields["sleep.timer.eos"] = s.endOfSong.toString()
        }

        // Renderer / output (spec §36)
        fields["output.current"] = ctx.outputName
        fields["renderer.current"] = ctx.rendererName
        fields["renderer.type"] = ctx.rendererType?.toString()

        return fields
    }

    /** Format milliseconds as m:ss or h:mm:ss. */
    private fun time(ms: Long): String {
        if (ms <= 0) return "0:00"
        val total = ms / 1000
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }
}
