package com.micasong.player.theme

import com.micasong.player.data.theme.AppStyle
import com.micasong.player.data.theme.AppStyles
import com.micasong.player.data.theme.ListStyle
import com.micasong.player.data.theme.NavBarStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppStyleTest {

    @Test
    fun `nine presets exist`() {
        assertEquals(9, AppStyles.PRESET_NAMES.size)
    }

    @Test
    fun `every preset resolves and keeps its name`() {
        AppStyles.PRESET_NAMES.forEach { name ->
            assertEquals(name, AppStyles.preset(name).name)
        }
    }

    @Test
    fun `tablet presets use a side rail with more columns`() {
        val large = AppStyles.preset("Large Tablet")
        assertEquals(NavBarStyle.SIDE_RAIL, large.navBarStyle)
        assertEquals(6, large.gridColumnsLandscape)
        assertTrue(large.gridColumnsPortrait > AppStyles.preset("Universal").gridColumnsPortrait)
    }

    @Test
    fun `old school basic has no labels and square corners`() {
        val old = AppStyles.preset("Old School Basic")
        assertFalse(old.navBarLabels)
        assertFalse(old.roundedCorners)
        assertEquals(ListStyle.TEXT_ONLY, old.defaultListStyle)
    }

    @Test
    fun `floating presets enable the floating player`() {
        assertTrue(AppStyles.preset("Modern").floatingPlayer)
        assertTrue(AppStyles.preset("Floating").floatingPlayer)
        assertFalse(AppStyles.preset("Universal").floatingPlayer)
    }

    @Test
    fun `unknown preset falls back to defaults`() {
        val style = AppStyles.preset("Nonexistent")
        assertEquals(NavBarStyle.BOTTOM_BAR, style.navBarStyle)
        assertEquals(ListStyle.LIST, style.defaultListStyle)
    }

    @Test
    fun `json round trip`() {
        val style = AppStyles.preset("Adventurer")
        assertEquals(style, AppStyles.fromJson(AppStyles.toJson(style)))
    }

    @Test
    fun `invalid json returns null`() {
        assertNull(AppStyles.fromJson(null))
        assertNull(AppStyles.fromJson("not json"))
    }
}
