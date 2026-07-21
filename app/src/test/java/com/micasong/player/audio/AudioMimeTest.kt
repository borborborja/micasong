package com.micasong.player.audio

import com.micasong.player.data.audio.AudioMime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AudioMimeTest {

    @Test
    fun `declared real mime wins`() {
        assertEquals("audio/flac", AudioMime.forUrl("http://s/track.mp3", declared = "audio/flac"))
    }

    @Test
    fun `bare codec names are not mimes`() {
        // Plex reports codec names like "mp3" — must not be passed through as a MIME.
        assertNull(AudioMime.declaredOrNull("mp3"))
        assertEquals("audio/mpeg", AudioMime.declaredOrNull("audio/mpeg"))
    }

    @Test
    fun `extension is used when nothing declared`() {
        assertEquals("audio/flac", AudioMime.forUrl("http://s/music/track.flac"))
        assertEquals("audio/mp4", AudioMime.forUrl("http://s/music/book.m4b"))
        assertEquals("audio/opus", AudioMime.forUrl("http://s/a.opus"))
    }

    @Test
    fun `query strings do not hide the extension`() {
        assertEquals("audio/ogg", AudioMime.forUrl("http://s/stream.ogg?u=x&t=y"))
    }

    @Test
    fun `unknown falls back to mpeg`() {
        // Subsonic stream URLs have no extension at all: /rest/stream.view?id=…
        assertEquals("audio/mpeg", AudioMime.forUrl("http://s/rest/stream.view?id=42"))
        assertEquals("audio/mpeg", AudioMime.forUrl(null))
    }
}
