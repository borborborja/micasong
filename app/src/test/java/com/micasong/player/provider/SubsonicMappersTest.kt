package com.micasong.player.provider

import com.micasong.player.data.provider.SubsonicMappers
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SubsonicMappersTest {

    private val songJson = JSONObject(
        """
        {
          "id": "300", "title": "Viva La Vida", "album": "Viva La Vida", "artist": "Coldplay",
          "albumId": "30", "artistId": "20", "track": 7, "discNumber": 1, "year": 2008,
          "genre": "Rock", "coverArt": "300", "size": 8421341, "contentType": "audio/flac",
          "duration": 242, "bitRate": 990, "samplingRate": 44100, "bitDepth": 16
        }
        """.trimIndent()
    )

    @Test
    fun `maps every field and uses the playable stream url`() {
        val track = SubsonicMappers.parseSong(songJson, providerId = 1001, streamUrl = "https://srv/stream?id=300", coverUrl = "https://srv/cover?id=300")
        assertEquals(SubsonicMappers.stableId("300"), track.id)
        assertEquals("https://srv/stream?id=300", track.mediaUri)   // playable, not the raw id
        assertEquals("Viva La Vida", track.title)
        assertEquals("Coldplay", track.artistName)
        assertEquals(SubsonicMappers.stableId("30"), track.albumId)
        assertEquals(SubsonicMappers.stableId("20"), track.artistId)
        assertEquals(7, track.trackNumber)
        assertEquals(1, track.discNumber)
        assertEquals(242_000L, track.durationMs)     // seconds → ms
        assertEquals(2008, track.year)
        assertEquals("Rock", track.genre)
        assertEquals("audio/flac", track.mimeType)
        assertEquals(990_000, track.bitrate)         // kbps → bps
        assertEquals(44_100, track.sampleRate)
        assertEquals(16, track.bitDepth)
        assertEquals(8421341L, track.sizeBytes)
        assertEquals("https://srv/cover?id=300", track.artworkUri)
        assertEquals(1001L, track.providerId)
    }

    @Test
    fun `stable id is consistent and positive`() {
        assertEquals(SubsonicMappers.stableId("abc"), SubsonicMappers.stableId("abc"))
        assert(SubsonicMappers.stableId("abc") > 0)
    }

    @Test
    fun `missing optional fields become null`() {
        val minimal = JSONObject("""{ "id": "1", "title": "T", "duration": 10 }""")
        val track = SubsonicMappers.parseSong(minimal, 1001, "url", null)
        assertNull(track.albumId)
        assertNull(track.genre)
        assertNull(track.bitrate)
        assertNull(track.artworkUri)
        assertEquals(10_000L, track.durationMs)
    }
}
