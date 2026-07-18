package com.micasong.player.sync

import com.micasong.player.data.db.TrackEntity
import com.micasong.player.data.sync.ServerSyncMerge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerSyncMergeTest {

    private fun te(
        id: Long,
        title: String = "Track $id",
        album: String = "Album",
        isFavorite: Boolean = false,
        rating: Int = 0,
        playCount: Int = 0,
    ) = TrackEntity(
        id = id,
        providerId = 2,
        mediaUri = "srv://$id",
        title = title,
        titleSort = title.lowercase(),
        albumId = 1,
        albumName = album,
        artistId = 1,
        artistName = "Artist",
        albumArtist = "Artist",
        trackNumber = id.toInt(),
        discNumber = 1,
        durationMs = 200_000,
        year = 2020,
        genre = "Rock",
        mimeType = "audio/flac",
        bitrate = null,
        sampleRate = null,
        bitDepth = null,
        sizeBytes = null,
        artworkUri = null,
        dateAdded = 0,
        isFavorite = isFavorite,
        userRating = rating,
        playCount = playCount,
    )

    @Test
    fun `unchanged catalog with local state produces nothing`() {
        val old = listOf(te(1, isFavorite = true, rating = 8, playCount = 5))
        val new = listOf(te(1))   // server has no user state
        val apply = ServerSyncMerge.merge(old, new)
        assertTrue("user-state-only difference must not count as a change", apply.isEmpty)
    }

    @Test
    fun `changed catalog preserves local user state`() {
        val old = listOf(te(1, title = "Old Title", isFavorite = true, rating = 8, playCount = 5))
        val new = listOf(te(1, title = "New Title"))
        val apply = ServerSyncMerge.merge(old, new)
        assertEquals(1, apply.upsert.size)
        val merged = apply.upsert.first()
        assertEquals("New Title", merged.title)       // catalog updated
        assertTrue(merged.isFavorite)                 // user state preserved
        assertEquals(8, merged.userRating)
        assertEquals(5, merged.playCount)
    }

    @Test
    fun `added tracks are upserted`() {
        val apply = ServerSyncMerge.merge(old = emptyList(), new = listOf(te(1), te(2)))
        assertEquals(setOf(1L, 2L), apply.upsert.map { it.id }.toSet())
        assertTrue(apply.deleteIds.isEmpty())
    }

    @Test
    fun `removed tracks are deleted`() {
        val apply = ServerSyncMerge.merge(old = listOf(te(1), te(2)), new = listOf(te(1)))
        assertEquals(listOf(2L), apply.deleteIds)
        assertTrue(apply.upsert.isEmpty())
    }

    @Test
    fun `mixed change set`() {
        val old = listOf(te(1, isFavorite = true), te(2), te(3))
        val new = listOf(te(1, title = "Changed"), te(2), te(4))   // 1 changed, 2 same, 3 removed, 4 added
        val apply = ServerSyncMerge.merge(old, new)
        assertEquals(setOf(1L, 4L), apply.upsert.map { it.id }.toSet())
        assertEquals(listOf(3L), apply.deleteIds)
        assertTrue(apply.upsert.first { it.id == 1L }.isFavorite)   // preserved
    }
}
