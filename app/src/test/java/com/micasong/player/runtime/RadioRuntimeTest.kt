package com.micasong.player.runtime

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.micasong.player.data.db.MiCaSongDatabase
import com.micasong.player.data.radio.InternetRadio
import com.micasong.player.data.radio.RadioStation
import com.micasong.player.data.repository.MediaRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Verifies radio-station persistence and the click-to-queue ordering (spec §10). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RadioRuntimeTest {

    private lateinit var db: MiCaSongDatabase
    private lateinit var repository: MediaRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, MiCaSongDatabase::class.java).allowMainThreadQueries().build()
        repository = MediaRepository(context, db.musicDao(), db.playlistDao(), db.providerDao(), db.downloadDao(), com.micasong.player.data.cache.DownloadTrigger {}, db.radioDao())
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `stations persist and delete`() = runBlocking {
        val id = repository.addRadioStation("SomaFM", "http://ice.somafm.com/groovesalad")
        repository.addRadioStation("KEXP", "http://kexp.streamguys1.com/kexp160.aac")
        assertEquals(listOf("KEXP", "SomaFM"), repository.radioStations.first().map { it.name })

        repository.deleteRadioStation(id)
        assertEquals(listOf("KEXP"), repository.radioStations.first().map { it.name })
    }

    @Test
    fun `clicked station leads the queue`() {
        val stations = listOf(
            RadioStation(1, "A", "http://a"),
            RadioStation(2, "B", "http://b"),
            RadioStation(3, "C", "http://c"),
        )
        val ordered = InternetRadio.orderedQueue(stations, clickedId = 3)
        assertEquals(listOf("C", "A", "B"), ordered.map { it.name })
    }

    @Test
    fun `basic auth is split from the url`() {
        val auth = InternetRadio.extractBasicAuth("http://user:pass@host/stream")
        assertTrue(auth.hasAuth)
        assertEquals("user", auth.username)
        assertEquals("pass", auth.password)
    }
}
