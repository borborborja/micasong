package com.micasong.player.ui.nowplaying

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.StarHalf
import androidx.compose.foundation.clickable
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import com.micasong.player.ui.PlayerViewModel
import com.micasong.player.ui.components.MediaArtwork
import com.micasong.player.ui.components.formatDuration

/** Expanded Now Playing screen (spec §24.2). */
@Composable
fun NowPlayingScreen(
    onCollapse: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val rating by viewModel.currentRating.collectAsStateWithLifecycle()
    val isFavorite by viewModel.currentFavorite.collectAsStateWithLifecycle()

    var scrubbing by remember { mutableFloatStateOf(-1f) }

    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onCollapse) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Contraer")
            }
            Text(
                "Reproduciendo ahora",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
            )
            IconButton(onClick = viewModel::toggleFavoriteCurrent) {
                Icon(
                    if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = if (isFavorite) "Quitar de favoritos" else "Añadir a favoritos",
                    tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(24.dp))
        MediaArtwork(
            state.artworkUri,
            Modifier.fillMaxWidth().aspectRatio(1f),
            corner = 20,
        )

        Spacer(Modifier.height(28.dp))
        Text(
            state.title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Text(
            state.artist,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(Modifier.height(24.dp))
        val fraction = if (scrubbing >= 0f) scrubbing
        else if (state.durationMs > 0) (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f)
        else 0f

        Slider(
            value = fraction,
            onValueChange = { scrubbing = it },
            onValueChangeFinished = {
                if (state.durationMs > 0) viewModel.seekTo((scrubbing * state.durationMs).toLong())
                scrubbing = -1f
            },
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatDuration(state.positionMs), style = MaterialTheme.typography.labelMedium)
            Text(formatDuration(state.durationMs), style = MaterialTheme.typography.labelMedium)
        }

        Spacer(Modifier.height(16.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = viewModel::toggleShuffle) {
                Icon(
                    Icons.Filled.Shuffle,
                    contentDescription = "Aleatorio",
                    tint = if (state.shuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = viewModel::previous) {
                Icon(Icons.Filled.SkipPrevious, contentDescription = "Anterior", modifier = Modifier.size(40.dp))
            }
            FilledIconButton(onClick = viewModel::togglePlayPause, modifier = Modifier.size(72.dp)) {
                Icon(
                    if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (state.isPlaying) "Pausar" else "Reproducir",
                    modifier = Modifier.size(40.dp),
                )
            }
            IconButton(onClick = viewModel::next) {
                Icon(Icons.Filled.SkipNext, contentDescription = "Siguiente", modifier = Modifier.size(40.dp))
            }
            IconButton(onClick = viewModel::cycleRepeat) {
                Icon(
                    if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                    contentDescription = "Repetir",
                    tint = if (state.repeatMode != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        SpeedSelector(current = state.speed, onSelect = viewModel::setSpeed)

        if (state.hasItem) {
            Spacer(Modifier.height(16.dp))
            RatingBar(rating = rating, onRate = viewModel::setRating)
        }
    }
}

/**
 * Five-star rating control on a 0–10 scale (half-star granularity, spec §11). Tapping a star sets
 * its whole-star value; tapping the star that already matches the rating clears it.
 */
@Composable
private fun RatingBar(rating: Int, onRate: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        (1..5).forEach { star ->
            val value = star * 2
            val icon = when {
                rating >= value -> Icons.Filled.Star
                rating == value - 1 -> Icons.Filled.StarHalf
                else -> Icons.Filled.StarBorder
            }
            Icon(
                icon,
                contentDescription = "Valorar con $star estrella${if (star > 1) "s" else ""}",
                tint = if (rating >= value - 1) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(36.dp)
                    .clickable { onRate(if (rating == value) 0 else value) },
            )
        }
    }
}

private val SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

@Composable
private fun SpeedSelector(current: Float, onSelect: (Float) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Velocidad", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SPEEDS.forEach { speed ->
            FilterChip(
                selected = kotlin.math.abs(current - speed) < 0.01f,
                onClick = { onSelect(speed) },
                label = { Text(if (speed == 1f) "1×" else "${speed}×".replace(".0×", "×")) },
            )
        }
    }
}
