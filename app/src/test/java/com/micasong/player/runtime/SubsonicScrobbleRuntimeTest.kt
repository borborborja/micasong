package com.micasong.player.runtime

import com.micasong.player.data.db.TrackEntity
import com.micasong.player.data.provider.ProviderConfig
import com.micasong.player.data.provider.ProviderType
import com.micasong.player.data.provider.SubsonicProvider
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Verifies a completed play submits a Subsonic scrobble carrying the server track id (spec §47). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SubsonicScrobbleRuntimeTest {

    private lateinit var server: MockWebServer

    @Before
    fun start() {
        server = MockWebServer()
        server.enqueue(MockResponse().setBody("""{"subsonic-response":{"status":"ok"}}"""))
        server.start()
    }

    @After
    fun stop() = server.shutdown()

    @Test
    fun `scrobble hits scrobble endpoint with the server id`() = runBlocking {
        val base = server.url("/").toString().trimEnd('/')
        val provider = SubsonicProvider(
            ProviderConfig(id = 1000, type = ProviderType.SUBSONIC, displayName = "S", primaryUrl = base, username = "u", secret = "p"),
        )
        val track = TrackEntity(
            id = 1, providerId = 1000, mediaUri = "$base/rest/stream.view?id=srv-42&u=u", title = "T", titleSort = "t",
            albumId = null, albumName = null, artistId = null, artistName = null, albumArtist = null,
            trackNumber = null, discNumber = null, durationMs = 1000, year = null, genre = null,
            mimeType = null, bitrate = null, sampleRate = null, bitDepth = null, sizeBytes = null,
            artworkUri = null, dateAdded = 0,
        )

        provider.scrobble(track)

        val req = server.takeRequest()
        assertTrue(req.path!!.contains("/scrobble"))
        assertEquals("srv-42", req.requestUrl?.queryParameter("id"))
        assertEquals("true", req.requestUrl?.queryParameter("submission"))
    }
}
