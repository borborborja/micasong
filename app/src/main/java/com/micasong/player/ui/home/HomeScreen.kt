package com.micasong.player.ui.home

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.micasong.player.data.model.Album
import com.micasong.player.ui.components.MediaArtwork
import com.micasong.player.ui.components.SectionHeader
import com.micasong.player.ui.components.TrackRow
import com.micasong.player.ui.components.formatDuration

private val audioPermission: String
    get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE

@Composable
fun HomeScreen(
    onOpenAlbum: (Long) -> Unit,
    onOpenArtist: (Long) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, audioPermission) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (granted) viewModel.sync()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item { HomeTopBar() }

        if (state.sync.running) {
            item {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(
                        text = "${state.sync.message} ${(state.sync.progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { state.sync.progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        if (!state.hasLibrary && !state.sync.running) {
            item {
                EmptyLibrary(
                    hasPermission = hasPermission,
                    onGrant = { permissionLauncher.launch(audioPermission) },
                    onSync = { viewModel.sync() },
                )
            }
        } else {
            item {
                ShortcutGrid(
                    onTrackMix = viewModel::playTrackMix,
                    onAlbumMix = viewModel::playAlbumMix,
                    onDecadeMix = viewModel::playTrackMix,
                )
            }

            if (state.favoriteAlbums.isNotEmpty()) {
                item { SectionHeader(title = "Álbumes favoritos") }
                item { AlbumCarousel(albums = state.favoriteAlbums, onClick = onOpenAlbum, onPlay = viewModel::playAlbum) }
            }
            if (state.recentlyAdded.isNotEmpty()) {
                item { SectionHeader(title = "Añadidos recientemente") }
                item { AlbumCarousel(albums = state.recentlyAdded, onClick = onOpenAlbum, onPlay = viewModel::playAlbum) }
            }
            if (state.favoriteTracks.isNotEmpty()) {
                item { SectionHeader(title = "Pistas favoritas") }
                itemsIndexed(state.favoriteTracks, key = { _, t -> t.id }) { index, track ->
                    TrackRow(
                        title = track.title,
                        subtitle = track.artistName,
                        artworkUri = track.artworkUri,
                        durationLabel = formatDuration(track.durationMs),
                        onClick = { viewModel.playFavoriteTracks(index) },
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeTopBar() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "MiCaSong",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { }) { Icon(Icons.Filled.FilterList, contentDescription = "Filtros") }
        IconButton(onClick = { }) { Icon(Icons.Filled.Cast, contentDescription = "Cast") }
    }
}

@Composable
private fun ShortcutGrid(
    onTrackMix: () -> Unit,
    onAlbumMix: () -> Unit,
    onDecadeMix: () -> Unit,
) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ShortcutCard("Mix de pistas", Icons.Filled.Shuffle, Modifier.weight(1f), onTrackMix)
            ShortcutCard("Mix de álbumes", Icons.Filled.Album, Modifier.weight(1f), onAlbumMix)
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ShortcutCard("Mix de la década", Icons.Filled.CalendarMonth, Modifier.weight(1f), onDecadeMix)
            ShortcutCard("Colas", Icons.Filled.QueueMusic, Modifier.weight(1f)) { }
        }
    }
}

@Composable
private fun ShortcutCard(
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Card(
        modifier = modifier.height(64.dp),
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Text(label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun AlbumCarousel(
    albums: List<Album>,
    onClick: (Long) -> Unit,
    onPlay: (Album, Boolean) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(albums) { album ->
            Column(
                Modifier
                    .width(150.dp)
                    .clickable { onClick(album.id) }
            ) {
                MediaArtwork(
                    uri = album.artworkUri,
                    modifier = Modifier.size(150.dp),
                    corner = 12,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    album.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
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
private fun EmptyLibrary(
    hasPermission: Boolean,
    onGrant: () -> Unit,
    onSync: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "Tu biblioteca está vacía",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Concede permiso de acceso al audio y sincroniza para escanear la música de tu dispositivo.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            if (hasPermission) {
                Button(onClick = onSync) { Text("Sincronizar") }
            } else {
                Button(onClick = onGrant) { Text("Conceder permiso") }
            }
        }
    }
}
