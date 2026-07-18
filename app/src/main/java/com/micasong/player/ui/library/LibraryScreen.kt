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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
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
import com.micasong.player.data.model.Track
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
    var showCreate by remember { mutableStateOf(false) }
    var addToPlaylistTrack by remember { mutableStateOf<Track?>(null) }
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()

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
            2 -> SongsTab(viewModel, onAddToPlaylist = { addToPlaylistTrack = it })
            3 -> GenresTab(viewModel.genres.collectAsStateWithLifecycle().value, onOpenGenre)
            4 -> PlaylistsTab(playlists, onOpenPlaylist, onCreate = { showCreate = true })
        }
    }

    if (showCreate) {
        NameDialog(
            title = "Nueva lista de reproducción",
            onDismiss = { showCreate = false },
            onConfirm = { name -> viewModel.createPlaylist(name); showCreate = false },
        )
    }
    addToPlaylistTrack?.let { track ->
        AddToPlaylistDialog(
            playlists = playlists,
            onDismiss = { addToPlaylistTrack = null },
            onPick = { playlistId -> viewModel.addTrackToPlaylist(playlistId, track.id); addToPlaylistTrack = null },
            onCreateNew = { name -> viewModel.createPlaylistWithTrack(name, track.id); addToPlaylistTrack = null },
        )
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
private fun SongsTab(viewModel: LibraryViewModel, onAddToPlaylist: (Track) -> Unit) {
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
        itemsIndexed(tracks, key = { _, t -> t.id }) { index, track ->
            TrackRow(
                title = track.title,
                subtitle = track.artistName,
                artworkUri = track.artworkUri,
                durationLabel = formatDuration(track.durationMs),
                onClick = { viewModel.playAllTracks(index) },
                onOverflow = { onAddToPlaylist(track) },
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
private fun PlaylistsTab(playlists: List<Playlist>, onOpenPlaylist: (Long) -> Unit, onCreate: () -> Unit) {
    LazyColumn {
        item {
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onCreate).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.size(12.dp))
                Text("Nueva lista de reproducción", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
        if (playlists.isEmpty()) {
            item {
                Text(
                    "No hay listas de reproducción todavía.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
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

@Composable
private fun NameDialog(title: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Nombre") }, modifier = Modifier.fillMaxWidth())
        },
        confirmButton = { TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text("Crear") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun AddToPlaylistDialog(
    playlists: List<Playlist>,
    onDismiss: () -> Unit,
    onPick: (Long) -> Unit,
    onCreateNew: (String) -> Unit,
) {
    var creating by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Añadir a lista") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(
                    Modifier.fillMaxWidth().clickable { creating = true }.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.size(12.dp))
                    Text("Nueva lista…", color = MaterialTheme.colorScheme.primary)
                }
                if (creating) {
                    OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Nombre") }, modifier = Modifier.fillMaxWidth())
                }
                playlists.forEach { pl ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onPick(pl.id) }.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.PlaylistPlay, contentDescription = null)
                        Spacer(Modifier.size(12.dp))
                        Text(pl.name)
                    }
                }
            }
        },
        confirmButton = {
            if (creating) {
                TextButton(onClick = { onCreateNew(name) }, enabled = name.isNotBlank()) { Text("Crear y añadir") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
    )
}
