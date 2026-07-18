package com.micasong.player.ui.album

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.micasong.player.ui.components.MediaArtwork
import com.micasong.player.ui.components.formatDuration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumDetailScreen(
    onBack: () -> Unit,
    onOpenArtist: (Long) -> Unit,
    viewModel: AlbumDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val album = state.album

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(album?.name ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item {
                Column(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    MediaArtwork(album?.artworkUri, Modifier.size(240.dp), corner = 16)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        album?.name ?: "",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(4.dp))
                    // Subtitle: "1:01:42 · 2024 · FLAC · 24/96"-style descriptor (spec §22.3)
                    Text(
                        text = buildSubtitle(state),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = album?.albumArtist ?: "",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Spacer(Modifier.height(16.dp))
                    ActionBar(
                        isFavorite = album?.isFavorite == true,
                        onPlay = { viewModel.play() },
                        onShuffle = { viewModel.play(shuffle = true) },
                        onAddQueue = { viewModel.addToQueue() },
                        onFavorite = { viewModel.toggleFavorite() },
                    )
                }
            }

            itemsIndexed(state.tracks, key = { _, t -> t.id }) { index, track ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = (track.trackNumber ?: (index + 1)).toString(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(28.dp).padding(end = 8.dp),
                        textAlign = TextAlign.End,
                    )
                    Column(
                        Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                            .clickableRow { viewModel.playAt(index) },
                    ) {
                        Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(
                        formatDuration(track.durationMs),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionBar(
    isFavorite: Boolean,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onAddQueue: () -> Unit,
    onFavorite: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilledIconButton(onClick = onPlay, modifier = Modifier.size(56.dp)) {
            Icon(Icons.Filled.PlayArrow, contentDescription = "Reproducir")
        }
        Spacer(Modifier.size(8.dp))
        IconButton(onClick = onShuffle) { Icon(Icons.Filled.Shuffle, contentDescription = "Aleatorio") }
        IconButton(onClick = onAddQueue) { Icon(Icons.Filled.Add, contentDescription = "Añadir a la cola") }
        IconButton(onClick = onFavorite) {
            Icon(
                if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = "Favorito",
                tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun buildSubtitle(state: AlbumDetailState): String {
    val album = state.album ?: return ""
    val parts = buildList {
        add(formatDuration(state.totalDurationMs))
        album.year?.let { add(it.toString()) }
        add("${album.trackCount} pistas")
        state.tracks.firstOrNull()?.qualityLabel?.let { add(it) }
    }
    return parts.joinToString(" · ")
}

private fun Modifier.clickableRow(onClick: () -> Unit): Modifier =
    this.clickable { onClick() }
