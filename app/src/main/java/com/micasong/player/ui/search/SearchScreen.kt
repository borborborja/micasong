package com.micasong.player.ui.search

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.micasong.player.ui.components.MediaArtwork
import com.micasong.player.ui.components.SectionHeader
import com.micasong.player.ui.components.TrackRow
import com.micasong.player.ui.components.formatDuration

@Composable
fun SearchScreen(
    onOpenAlbum: (Long) -> Unit,
    onOpenArtist: (Long) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = viewModel::onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            placeholder = { Text("Buscar en tu biblioteca…") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
        )

        if (query.isNotBlank() && results.isEmpty) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Sin resultados", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Column
        }

        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            if (results.artists.isNotEmpty()) {
                item { SectionHeader("Artistas") }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(results.artists, key = { it.id }) { artist ->
                            Column(
                                Modifier.width(100.dp).clickable { onOpenArtist(artist.id) },
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                MediaArtwork(artist.artworkUri, Modifier.size(90.dp), corner = 999)
                                Spacer(Modifier.height(4.dp))
                                Text(artist.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
            if (results.albums.isNotEmpty()) {
                item { SectionHeader("Álbumes") }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(results.albums, key = { it.id }) { album ->
                            Column(Modifier.width(130.dp).clickable { onOpenAlbum(album.id) }) {
                                MediaArtwork(album.artworkUri, Modifier.size(130.dp), corner = 12)
                                Spacer(Modifier.height(4.dp))
                                Text(album.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
            if (results.tracks.isNotEmpty()) {
                item { SectionHeader("Pistas") }
                items(results.tracks, key = { it.id }) { track ->
                    TrackRow(
                        title = track.title,
                        subtitle = track.artistName,
                        artworkUri = track.artworkUri,
                        durationLabel = formatDuration(track.durationMs),
                        onClick = { viewModel.playTrack(track) },
                    )
                }
            }
        }
    }
}
