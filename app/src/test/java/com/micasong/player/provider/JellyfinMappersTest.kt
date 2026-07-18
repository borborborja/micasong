package com.micasong.player.provider

import com.micasong.player.data.provider.JellyfinMappers
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JellyfinMappersTest {

    private val item = JSONObject(
        """
        {
          "Id": "abc123", "Name": "Creep", "Album": "Pablo Honey", "AlbumId": "alb1",
          "AlbumArtist": "Radiohead", "Artists": ["Radiohead"],
          "ArtistItems": [{ "Id": "art1", "Name": "Radiohead" }],
          "IndexNumber": 2, "ParentIndexNumber": 1, "ProductionYear": 1993,
          "Genres": ["Alternative Rock"], "RunTimeTicks": 2380000000
        }
        """.trimIndent()
    )

    @Test
    fun `maps fields and uses playable stream url`() {
        val track = JellyfinMappers.parseItem(item, providerId = 1002, streamUrl = "https://jf/Audio/abc123/stream?static=true", coverUrl = "https://jf/Items/abc123/Images/Primary")
        assertEquals(JellyfinMappers.stableId("abc123"), track.id)
        assertEquals("https://jf/Audio/abc123/stream?static=true", track.mediaUri)   // playable
        assertEquals("Creep", track.title)
        assertEquals("Radiohead", track.artistName)
        assertEquals(JellyfinMappers.stableId("alb1"), track.albumId)
        assertEquals(JellyfinMappers.stableId("art1"), track.artistId)   // from ArtistItems
        assertEquals(2, track.trackNumber)
        assertEquals(1, track.discNumber)
        assertEquals(238_000L, track.durationMs)   // ticks / 10000
        assertEquals(1993, track.year)
        assertEquals("Alternative Rock", track.genre)
        assertEquals("https://jf/Items/abc123/Images/Primary", track.artworkUri)
        assertEquals(1002L, track.providerId)
    }

    @Test
    fun `artist id falls back to the artist name when no ArtistItems`() {
        val minimal = JSONObject("""{ "Id": "x", "Name": "T", "AlbumArtist": "Bjork", "RunTimeTicks": 10000000 }""")
        val track = JellyfinMappers.parseItem(minimal, 1002, "url", null)
        assertEquals(JellyfinMappers.stableId("Bjork"), track.artistId)
        assertEquals("Bjork", track.artistName)
        assertEquals(1000L, track.durationMs)
        assertNull(track.genre)
    }
}
