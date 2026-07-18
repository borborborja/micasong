package com.micasong.player.runtime

import com.micasong.player.data.audio.Chapters
import com.micasong.player.data.provider.AudioBookShelfProvider
import com.micasong.player.data.provider.ProviderConfig
import com.micasong.player.data.provider.ProviderType
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Runtime verification of the AudioBookShelf client: book → audiobook track with chapters. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AudioBookShelfSyncRuntimeTest {

    private lateinit var server: MockWebServer

    @Before
    fun start() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.requestUrl?.encodedPath ?: ""
                val body = when {
                    path.endsWith("/api/libraries") ->
                        """{"libraries":[{"id":"lib1","name":"Books","mediaType":"book"}]}"""
                    path.endsWith("/api/libraries/lib1/items") ->
                        """{"results":[{"id":"book1"}]}"""
                    path.endsWith("/api/items/book1") ->
                        """{"id":"book1","media":{"duration":3600.0,"size":50000000,
                            "metadata":{"title":"Dune","authorName":"Frank Herbert","publishedYear":"1965"},
                            "audioFiles":[{"ino":"777"}],
                            "chapters":[{"start":0,"end":1800,"title":"Chapter 1"},{"start":1800,"end":3600,"title":"Chapter 2"}]}}"""
                    else -> "{}"
                }
                return MockResponse().addHeader("Content-Type", "application/json").setBody(body)
            }
        }
        server.start()
    }

    @After
    fun stop() = server.shutdown()

    @Test
    fun `maps a book to an audiobook track with chapters`() = runBlocking {
        val base = server.url("/").toString().trimEnd('/')
        val provider = AudioBookShelfProvider(
            ProviderConfig(id = 1006, type = ProviderType.AUDIOBOOKSHELF, displayName = "ABS", primaryUrl = base, secret = "tok"),
        )

        val snapshot = provider.sync()

        assertEquals(1, snapshot.tracks.size)
        val t = snapshot.tracks.first()
        assertEquals("Dune", t.title)
        assertEquals("Frank Herbert", t.artistName)
        assertEquals(3_600_000L, t.durationMs)
        assertTrue(t.isAudiobook)
        assertTrue(t.mediaUri.contains("/api/items/book1/file/777"))
        assertTrue(t.mediaUri.contains("token=tok"))

        val chapters = Chapters.fromJson(t.chaptersJson, t.durationMs)
        assertEquals(2, chapters.count)
        assertEquals("Chapter 2", chapters.chapterAt(2_000_000)!!.title)
    }
}
