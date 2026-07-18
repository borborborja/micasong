package com.micasong.player.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

/** Top-level routes reachable from the bottom navigation bar (spec §21). */
enum class TopLevelDestination(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    HOME("home", com.micasong.player.R.string.nav_home, Icons.Filled.Home, Icons.Outlined.Home),
    LIBRARY("library", com.micasong.player.R.string.nav_library, Icons.Filled.LibraryMusic, Icons.Outlined.LibraryMusic),
    SEARCH("search", com.micasong.player.R.string.nav_search, Icons.Filled.Search, Icons.Outlined.Search),
    SETTINGS("settings", com.micasong.player.R.string.nav_settings, Icons.Filled.Settings, Icons.Outlined.Settings),
}

/** Detail routes with arguments. */
object Routes {
    const val ALBUM = "album/{albumId}"
    const val ARTIST = "artist/{artistId}"
    const val GENRE = "genre/{genreName}"
    const val PLAYLIST = "playlist/{playlistId}"
    const val NOW_PLAYING = "now_playing"
    const val PROVIDERS = "providers"
    const val EQUALIZER = "equalizer"

    fun album(id: Long) = "album/$id"
    fun artist(id: Long) = "artist/$id"
    fun genre(name: String) = "genre/${android.net.Uri.encode(name)}"
    fun playlist(id: Long) = "playlist/$id"
}
