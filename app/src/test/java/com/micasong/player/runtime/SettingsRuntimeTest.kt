package com.micasong.player.runtime

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.micasong.player.data.settings.ScreenOrientation
import com.micasong.player.data.settings.SettingsRepository
import com.micasong.player.data.settings.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Verifies the §44 preferences persist and that restore-defaults resets them. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsRuntimeTest {

    private val settings = SettingsRepository(ApplicationProvider.getApplicationContext<Context>())

    @Test
    fun `advanced preferences persist and reset`() = runBlocking {
        settings.setKeepScreenOn(true)
        settings.setHideStatusBar(true)
        settings.setScreenOrientation(ScreenOrientation.LANDSCAPE)
        settings.setShowTrackNumber(false)
        settings.setThemeMode(ThemeMode.BLACK)

        var s = settings.settings.first()
        assertTrue(s.keepScreenOn)
        assertTrue(s.hideStatusBar)
        assertEquals(ScreenOrientation.LANDSCAPE, s.screenOrientation)
        assertFalse(s.showTrackNumber)
        assertEquals(ThemeMode.BLACK, s.themeMode)

        settings.resetToDefaults()
        s = settings.settings.first()
        assertFalse(s.keepScreenOn)
        assertFalse(s.hideStatusBar)
        assertEquals(ScreenOrientation.SYSTEM, s.screenOrientation)
        assertTrue(s.showTrackNumber)
        assertEquals(ThemeMode.SYSTEM, s.themeMode)
    }
}
