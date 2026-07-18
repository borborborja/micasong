package com.micasong.player.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.micasong.player.ui.album.AlbumDetailScreen
import com.micasong.player.ui.artist.ArtistDetailScreen
import com.micasong.player.ui.genre.GenreDetailScreen
import com.micasong.player.ui.home.HomeScreen
import com.micasong.player.ui.library.LibraryScreen
import com.micasong.player.ui.navigation.Routes
import com.micasong.player.ui.navigation.TopLevelDestination
import com.micasong.player.ui.nowplaying.MiniPlayer
import com.micasong.player.ui.nowplaying.NowPlayingScreen
import com.micasong.player.ui.playlist.PlaylistDetailScreen
import com.micasong.player.ui.search.SearchScreen
import com.micasong.player.ui.settings.SettingsScreen

@Composable
fun MiCaSongApp() {
    val navController = rememberNavController()
    val playerViewModel: PlayerViewModel = hiltViewModel()
    val nowPlaying by playerViewModel.state.collectAsStateWithLifecycle()

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val topLevelRoutes = TopLevelDestination.entries.map { it.route }
    val isTopLevel = currentRoute in topLevelRoutes
    val isNowPlaying = currentRoute == Routes.NOW_PLAYING

    Scaffold(
        bottomBar = {
            if (isTopLevel || (!isNowPlaying && nowPlaying.hasItem)) {
                Column {
                    AnimatedVisibility(
                        visible = nowPlaying.hasItem && !isNowPlaying,
                        enter = slideInVertically { it },
                        exit = slideOutVertically { it },
                    ) {
                        MiniPlayer(
                            state = nowPlaying,
                            onTogglePlay = playerViewModel::togglePlayPause,
                            onNext = playerViewModel::next,
                            onClick = { navController.navigate(Routes.NOW_PLAYING) },
                        )
                    }
                    if (isTopLevel) {
                        BottomBar(currentRoute = currentRoute, navController = navController)
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = TopLevelDestination.HOME.route,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            composable(TopLevelDestination.HOME.route) {
                HomeScreen(
                    onOpenAlbum = { navController.navigate(Routes.album(it)) },
                    onOpenArtist = { navController.navigate(Routes.artist(it)) },
                )
            }
            composable(TopLevelDestination.LIBRARY.route) {
                LibraryScreen(
                    onOpenAlbum = { navController.navigate(Routes.album(it)) },
                    onOpenArtist = { navController.navigate(Routes.artist(it)) },
                    onOpenGenre = { navController.navigate(Routes.genre(it)) },
                    onOpenPlaylist = { navController.navigate(Routes.playlist(it)) },
                )
            }
            composable(TopLevelDestination.SEARCH.route) {
                SearchScreen(
                    onOpenAlbum = { navController.navigate(Routes.album(it)) },
                    onOpenArtist = { navController.navigate(Routes.artist(it)) },
                )
            }
            composable(TopLevelDestination.SETTINGS.route) {
                SettingsScreen(
                    onOpenProviders = { navController.navigate(Routes.PROVIDERS) },
                    onOpenEqualizer = { navController.navigate(Routes.EQUALIZER) },
                    onOpenBackup = { navController.navigate(Routes.BACKUP) },
                )
            }
            composable(Routes.PROVIDERS) {
                com.micasong.player.ui.settings.ProvidersScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.EQUALIZER) {
                com.micasong.player.ui.settings.EqScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.BACKUP) {
                com.micasong.player.ui.settings.BackupScreen(onBack = { navController.popBackStack() })
            }

            composable(
                route = Routes.ALBUM,
                arguments = listOf(navArgument("albumId") { type = NavType.LongType }),
            ) {
                AlbumDetailScreen(
                    onBack = { navController.popBackStack() },
                    onOpenArtist = { navController.navigate(Routes.artist(it)) },
                )
            }
            composable(
                route = Routes.ARTIST,
                arguments = listOf(navArgument("artistId") { type = NavType.LongType }),
            ) {
                ArtistDetailScreen(
                    onBack = { navController.popBackStack() },
                    onOpenAlbum = { navController.navigate(Routes.album(it)) },
                )
            }
            composable(
                route = Routes.GENRE,
                arguments = listOf(navArgument("genreName") { type = NavType.StringType }),
            ) {
                GenreDetailScreen(onBack = { navController.popBackStack() })
            }
            composable(
                route = Routes.PLAYLIST,
                arguments = listOf(navArgument("playlistId") { type = NavType.LongType }),
            ) {
                PlaylistDetailScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.NOW_PLAYING) {
                NowPlayingScreen(onCollapse = { navController.popBackStack() })
            }
        }
    }
}

@Composable
private fun BottomBar(
    currentRoute: String?,
    navController: androidx.navigation.NavHostController,
) {
    NavigationBar {
        TopLevelDestination.entries.forEach { dest ->
            val selected = currentRoute == dest.route
            NavigationBarItem(
                selected = selected,
                onClick = {
                    navController.navigate(dest.route) {
                        popUpTo(TopLevelDestination.HOME.route) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = {
                    Icon(
                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                        contentDescription = null,
                    )
                },
                label = { Text(stringResource(dest.labelRes)) },
                colors = androidx.compose.material3.NavigationBarItemDefaults.colors(
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            )
        }
    }
}
