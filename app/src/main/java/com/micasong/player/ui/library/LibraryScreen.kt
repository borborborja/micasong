package com.micasong.player.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.micasong.player.data.model.Album
import com.micasong.player.data.model.Artist
import com.micasong.player.data.model.Genre
import com.micasong.player.data.model.Playlist
import com.micasong.player.ui.components.MediaArtwork
import com.micasong.player.ui.components.TrackRow
import com.micasong.player.ui.components.formatDuration

private val tabs = listOf("Álbumes", "Artistas", "Pistas", "Géneros", "Listas")

@Composable
fun LibraryScreen(
    onOpenAlbum: (Long) -> Unit,
    onOpenArtist: (Long) -> Unit,
    onOpenGenre: (String) -> Unit,
    onOpenPlaylist: (Long) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize()) {
        ScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 12.dp) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title) },
                )
            }
        }
        when (selectedTab) {
            0 -> AlbumsTab(viewModel.albums.collectAsStateWithLifecycle().value, onOpenAlbum)
            1 -> ArtistsTab(viewModel.artists.collectAsStateWithLifecycle().value, onOpenArtist)
            2 -> SongsTab(viewModel)
            3 -> GenresTab(viewModel.genres.collectAsStateWithLifecycle().value, onOpenGenre)
            4 -> PlaylistsTab(viewModel.playlists.collectAsStateWithLifecycle().value, onOpenPlaylist)
        }
    }
}

@Composable
private fun AlbumsTab(albums: List<Album>, onOpenAlbum: (Long) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(albums, key = { it.id }) { album ->
            Column(Modifier.clickable { onOpenAlbum(album.id) }) {
                MediaArtwork(album.artworkUri, Modifier.fillMaxWidth().aspectRatio(1f), corner = 12)
                Spacer(Modifier.height(6.dp))
                Text(album.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    album.albumArtist ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ArtistsTab(artists: List<Artist>, onOpenArtist: (Long) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(120.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(artists, key = { it.id }) { artist ->
            Column(
                Modifier.clickable { onOpenArtist(artist.id) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                MediaArtwork(artist.artworkUri, Modifier.size(110.dp).clip(CircleShape), corner = 999)
                Spacer(Modifier.height(6.dp))
                Text(
                    artist.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
                Text(
                    "${artist.albumCount} álbumes",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SongsTab(viewModel: LibraryViewModel) {
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
        itemsIndexed(tracks, key = { _, t -> t.id }) { index, track ->
            TrackRow(
                title = track.title,
                subtitle = track.artistName,
                artworkUri = track.artworkUri,
                durationLabel = formatDuration(track.durationMs),
                onClick = { viewModel.playAllTracks(index) },
            )
        }
    }
}

@Composable
private fun GenresTab(genres: List<Genre>, onOpenGenre: (String) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(8.dp)) {
        items(genres, key = { it.id }) { genre ->
            Text(
                text = genre.name,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenGenre(genre.name) }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            )
        }
    }
}

@Composable
private fun PlaylistsTab(playlists: List<Playlist>, onOpenPlaylist: (Long) -> Unit) {
    if (playlists.isEmpty()) {
        EmptyTab("No hay listas de reproducción todavía.")
        return
    }
    LazyColumn {
        items(playlists, key = { it.id }) { playlist ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onOpenPlaylist(playlist.id) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.PlaylistPlay, contentDescription = null)
                Spacer(Modifier.size(12.dp))
                Column {
                    Text(playlist.name, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "${playlist.trackCount} pistas",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyTab(message: String) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
    }
}
