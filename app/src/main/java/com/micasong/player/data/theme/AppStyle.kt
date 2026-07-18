package com.micasong.player.data.theme

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Main navigation styles (spec §21, §28). */
@Serializable
enum class NavBarStyle { BOTTOM_BAR, BOTTOM_DOCKED_TOOLBAR, BOTTOM_FLOATING_TOOLBAR, BOTTOM_FLOATING_FAB, SIDE_RAIL }

/** Media list display styles (spec §23). */
@Serializable
enum class ListStyle { LIST, SINGLE_LINE, TEXT_ONLY, GRID_ROUND, GRID_SQUARE, ROUND_CARD, OVERLAY_GRID, LANDSCAPE_GRID, FANART_GRID, MULTILINE_ROW, CAROUSEL }

/**
 * A complete app style — a bundle of interface configuration that can be switched, saved,
 * imported/exported and loaded via the API (`load_app_style`, spec §28). Serializable so styles
 * are shareable as JSON (the reference app also bundles the launcher icon, out of scope here).
 */
@Serializable
data class AppStyle(
    val name: String,
    val navBarStyle: NavBarStyle = NavBarStyle.BOTTOM_BAR,
    val navBarLabels: Boolean = true,
    val fullWidthNav: Boolean = false,
    val defaultListStyle: ListStyle = ListStyle.LIST,
    val gridColumnsPortrait: Int = 2,
    val gridColumnsLandscape: Int = 4,
    val roundedCorners: Boolean = true,
    val floatingPlayer: Boolean = false,
)

object AppStyles {

    /** The nine built-in presets (spec §28). */
    val PRESET_NAMES = listOf(
        "Modern", "Floating", "Universal", "Classic Navigation", "Individual Page",
        "Old School Basic", "Adventurer", "Medium Tablet", "Large Tablet",
    )

    fun preset(name: String): AppStyle = when (name) {
        "Modern" -> AppStyle(name, NavBarStyle.BOTTOM_FLOATING_TOOLBAR, defaultListStyle = ListStyle.ROUND_CARD, floatingPlayer = true)
        "Floating" -> AppStyle(name, NavBarStyle.BOTTOM_FLOATING_FAB, defaultListStyle = ListStyle.GRID_ROUND, floatingPlayer = true)
        "Universal" -> AppStyle(name, NavBarStyle.BOTTOM_BAR, defaultListStyle = ListStyle.LIST)
        "Classic Navigation" -> AppStyle(name, NavBarStyle.BOTTOM_BAR, navBarLabels = true, defaultListStyle = ListStyle.LIST, fullWidthNav = true)
        "Individual Page" -> AppStyle(name, NavBarStyle.BOTTOM_DOCKED_TOOLBAR, defaultListStyle = ListStyle.MULTILINE_ROW)
        "Old School Basic" -> AppStyle(name, NavBarStyle.BOTTOM_BAR, navBarLabels = false, defaultListStyle = ListStyle.TEXT_ONLY, roundedCorners = false)
        "Adventurer" -> AppStyle(name, NavBarStyle.BOTTOM_FLOATING_TOOLBAR, defaultListStyle = ListStyle.OVERLAY_GRID, floatingPlayer = true)
        "Medium Tablet" -> AppStyle(name, NavBarStyle.SIDE_RAIL, defaultListStyle = ListStyle.GRID_SQUARE, gridColumnsPortrait = 3, gridColumnsLandscape = 5)
        "Large Tablet" -> AppStyle(name, NavBarStyle.SIDE_RAIL, defaultListStyle = ListStyle.GRID_SQUARE, gridColumnsPortrait = 4, gridColumnsLandscape = 6)
        else -> AppStyle(name)
    }

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun toJson(style: AppStyle): String = json.encodeToString(AppStyle.serializer(), style)
    fun fromJson(text: String?): AppStyle? =
        text?.takeIf { it.isNotBlank() }?.let { runCatching { json.decodeFromString(AppStyle.serializer(), it) }.getOrNull() }
}
