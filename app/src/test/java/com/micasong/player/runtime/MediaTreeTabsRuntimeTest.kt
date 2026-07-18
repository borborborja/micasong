package com.micasong.player.runtime

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.micasong.player.data.db.MiCaSongDatabase
import com.micasong.player.data.repository.MediaRepository
import com.micasong.player.data.settings.SettingsRepository
import com.micasong.player.playback.MediaTree
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Verifies the Android Auto root exposes only the tabs the user enabled (spec §38). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MediaTreeTabsRuntimeTest {

    private lateinit var db: MiCaSongDatabase
    private lateinit var settings: SettingsRepository
    private lateinit var tree: MediaTree

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, MiCaSongDatabase::class.java).allowMainThreadQueries().build()
        settings = SettingsRepository(context)
        val repo = MediaRepository(context, db.musicDao(), db.playlistDao(), db.providerDao(), db.downloadDao(), com.micasong.player.data.cache.DownloadTrigger {}, db.radioDao())
        tree = MediaTree(repo, settings)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `root shows all tabs by default and honours disabling`() = runBlocking {
        assertEquals(4, tree.children("root").size)

        settings.setAutoTab("recent", false)
        settings.setAutoTab("favorites", false)
        val tabs = tree.children("root").map { it.mediaId }
        assertEquals(listOf("tab_home", "tab_library"), tabs)

        // Never empty: if everything is off, Biblioteca remains as a fallback.
        settings.setAutoTab("home", false)
        settings.setAutoTab("library", false)
        val fallback = tree.children("root").map { it.mediaId }
        assertTrue(fallback.contains("tab_library"))
    }
}
