package com.micasong.player.runtime

import com.micasong.player.data.provider.PlexProvider
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

/** Runtime verification of the Plex client against a fake server: section discovery + track map. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlexSyncRuntimeTest {

    private lateinit var server: MockWebServer

    @Before
    fun start() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.requestUrl?.encodedPath ?: ""
                val json = when {
                    path.endsWith("/library/sections") ->
                        """{"MediaContainer":{"Directory":[
                            {"key":"1","type":"movie","title":"Films"},
                            {"key":"3","type":"artist","title":"Music"}
                        ]}}"""
                    path.contains("/library/sections/3/all") ->
                        """{"MediaContainer":{"Metadata":[
                            {"ratingKey":"55","title":"Paranoid Android","grandparentTitle":"Radiohead","grandparentRatingKey":"7","parentTitle":"OK Computer","parentRatingKey":"9","index":2,"parentIndex":1,"year":1997,"duration":383000,"addedAt":123,"thumb":"/library/metadata/55/thumb",
                             "Media":[{"bitrate":900,"audioCodec":"flac","Part":[{"key":"/library/parts/55/file.flac"}]}]}
                        ]}}"""
                    else -> """{"MediaContainer":{}}"""
                }
                return MockResponse().addHeader("Content-Type", "application/json").setBody(json)
            }
        }
        server.start()
    }

    @After
    fun stop() = server.shutdown()

    @Test
    fun `discovers music section and maps tracks with token stream url`() = runBlocking {
        val base = server.url("").toString().trimEnd('/')
        val provider = PlexProvider(
            ProviderConfig(id = 1004, type = ProviderType.PLEX, displayName = "Plex", primaryUrl = base, secret = "plextoken"),
        )

        val snapshot = provider.sync()

        assertEquals(1, snapshot.tracks.size)
        val t = snapshot.tracks.first()
        assertEquals("Paranoid Android", t.title)
        assertEquals("Radiohead", t.artistName)
        assertEquals("OK Computer", t.albumName)
        assertEquals(2, t.trackNumber)
        assertEquals(383_000L, t.durationMs)
        assertTrue(t.mediaUri.contains("/library/parts/55/file.flac"))
        assertTrue(t.mediaUri.contains("X-Plex-Token=plextoken"))
        assertEquals(1, snapshot.albums.size)
    }
}
