package com.micasong.player.runtime

import android.content.Context
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import com.micasong.player.data.provider.LocalProvider
import com.micasong.player.data.provider.ProviderConfig
import com.micasong.player.data.provider.ProviderType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.fakes.RoboCursor

/**
 * Runtime verification of the local MediaStore scan using Robolectric. A fake MediaStore cursor is
 * fed through a shadow ContentResolver and [LocalProvider.sync] is run for real — this exercises
 * the (API-30-aware) projection and the cursor→entity parsing end to end on the JVM.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocalProviderRuntimeTest {

    private val columns = listOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ALBUM,
        MediaStore.Audio.Media.ALBUM_ID,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.ARTIST_ID,
        MediaStore.Audio.Media.ALBUM_ARTIST,
        MediaStore.Audio.Media.TRACK,
        MediaStore.Audio.Media.DURATION,
        MediaStore.Audio.Media.YEAR,
        MediaStore.Audio.Media.MIME_TYPE,
        MediaStore.Audio.Media.SIZE,
        MediaStore.Audio.Media.DATE_ADDED,
        MediaStore.Audio.Media.GENRE,
        MediaStore.Audio.Media.BITRATE,
    )

    private fun cursorOf(vararg rows: Array<Any?>): RoboCursor = RoboCursor().apply {
        setColumnNames(columns)
        setResults(arrayOf(*rows))
    }

    private fun install(context: Context, cursor: RoboCursor) {
        Shadows.shadowOf(context.contentResolver)
            .setCursor(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, cursor)
    }

    @Test
    fun `scans MediaStore rows into tracks, albums, artists and genres`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        install(
            context,
            cursorOf(
                arrayOf(1L, "Viva La Vida", "Viva La Vida", 100L, "Coldplay", 20L, "Coldplay", 7, 242_000L, 2008, "audio/flac", 8_000_000L, 111L, "Rock", 990),
                arrayOf(2L, "Clocks", "A Rush of Blood", 101L, "Coldplay", 20L, "Coldplay", 5, 307_000L, 2002, "audio/mpeg", 7_000_000L, 112L, "Rock", 320),
                arrayOf(3L, "Weird Fishes", "In Rainbows", 102L, "Radiohead", 21L, "Radiohead", 4, 318_000L, 2007, "audio/flac", 9_000_000L, 113L, "Alternative", 1000),
            ),
        )

        val snapshot = LocalProvider(context, ProviderConfig(1, ProviderType.LOCAL, "Local")).sync()

        assertEquals(3, snapshot.tracks.size)
        val viva = snapshot.tracks.first { it.id == 1L }
        assertEquals("Viva La Vida", viva.title)
        assertEquals("Coldplay", viva.artistName)
        assertEquals(7, viva.trackNumber)
        assertEquals(242_000L, viva.durationMs)
        assertEquals(2008, viva.year)
        assertEquals("Rock", viva.genre)

        assertEquals(3, snapshot.albums.size)
        assertEquals(2, snapshot.artists.size)
        assertEquals(2, snapshot.artists.first { it.name == "Coldplay" }.trackCount)
        assertTrue(snapshot.genres.any { it.name == "Rock" && it.trackCount == 2 })
        assertTrue(snapshot.genres.any { it.name == "Alternative" })
    }

    @Test
    fun `empty MediaStore yields an empty snapshot without crashing`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        install(context, cursorOf())
        val snapshot = LocalProvider(context, ProviderConfig(1, ProviderType.LOCAL, "Local")).sync()
        assertTrue(snapshot.tracks.isEmpty())
        assertTrue(snapshot.albums.isEmpty())
    }
}
