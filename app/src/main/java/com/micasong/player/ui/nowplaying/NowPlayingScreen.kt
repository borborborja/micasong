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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.StarHalf
import androidx.compose.foundation.clickable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.mutableStateOf
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
import com.micasong.player.ui.CastButton
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
    val sleepRemaining by viewModel.sleepRemainingMs.collectAsStateWithLifecycle()
    val queue by viewModel.queue.collectAsStateWithLifecycle()
    val smartFlow by viewModel.smartFlowMode.collectAsStateWithLifecycle()
    val smartQueue by viewModel.smartQueueMode.collectAsStateWithLifecycle()

    val lyrics by viewModel.currentLyrics.collectAsStateWithLifecycle()

    var scrubbing by remember { mutableFloatStateOf(-1f) }
    var sleepMenuOpen by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }

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
            CastButton()
            IconButton(onClick = { showLyrics = true }) {
                Icon(Icons.Filled.Lyrics, contentDescription = "Letra")
            }
            IconButton(onClick = { showQueue = true }) {
                Icon(Icons.Filled.QueueMusic, contentDescription = "Cola de reproducción")
            }
            Box {
                IconButton(onClick = { sleepMenuOpen = true }) {
                    Icon(
                        Icons.Filled.Bedtime,
                        contentDescription = "Temporizador para dormir",
                        tint = if (sleepRemaining != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DropdownMenu(expanded = sleepMenuOpen, onDismissRequest = { sleepMenuOpen = false }) {
                    listOf(15, 30, 45, 60).forEach { minutes ->
                        DropdownMenuItem(
                            text = { Text("$minutes min") },
                            onClick = { viewModel.setSleepTimer(minutes); sleepMenuOpen = false },
                        )
                    }
                    if (sleepRemaining != null) {
                        DropdownMenuItem(
                            text = { Text("Desactivar") },
                            onClick = { viewModel.cancelSleepTimer(); sleepMenuOpen = false },
                        )
                    }
                }
            }
            IconButton(onClick = viewModel::toggleFavoriteCurrent) {
                Icon(
                    if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = if (isFavorite) "Quitar de favoritos" else "Añadir a favoritos",
                    tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        sleepRemaining?.let { remaining ->
            Text(
                "Se pausará en ${formatDuration(remaining)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 4.dp),
            )
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

    if (showLyrics) {
        LyricsSheet(lyrics = lyrics, positionMs = state.positionMs, onDismiss = { showLyrics = false })
    }

    if (showQueue) {
        QueueSheet(
            queue = queue,
            smartFlow = smartFlow,
            smartQueue = smartQueue,
            onSetSmartFlow = viewModel::setSmartFlowMode,
            onSetSmartQueue = viewModel::setSmartQueueMode,
            onDismiss = { showQueue = false },
            onJump = viewModel::jumpToQueueItem,
            onRemove = viewModel::removeQueueItem,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueueSheet(
    queue: List<com.micasong.player.playback.QueueItem>,
    smartFlow: com.micasong.player.data.smart.SmartFlowMode?,
    smartQueue: com.micasong.player.data.smart.SmartQueueMode?,
    onSetSmartFlow: (com.micasong.player.data.smart.SmartFlowMode?) -> Unit,
    onSetSmartQueue: (com.micasong.player.data.smart.SmartQueueMode?) -> Unit,
    onDismiss: () -> Unit,
    onJump: (Int) -> Unit,
    onRemove: (Int) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Text(
            "Cola de reproducción (${queue.size})",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        SmartFlowControls(smartFlow, smartQueue, onSetSmartFlow, onSetSmartQueue)
        LazyColumn(Modifier.fillMaxWidth()) {
            items(queue, key = { it.mediaId + it.index }) { item ->
                ListItem(
                    headlineContent = {
                        Text(
                            item.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = if (item.isCurrent) FontWeight.Bold else FontWeight.Normal,
                        )
                    },
                    supportingContent = {
                        Text(item.artist, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    leadingContent = {
                        MediaArtwork(item.artworkUri, Modifier.size(44.dp), corner = 6)
                    },
                    trailingContent = {
                        IconButton(onClick = { onRemove(item.index) }) {
                            Icon(Icons.Filled.Close, contentDescription = "Quitar de la cola")
                        }
                    },
                    colors = if (item.isCurrent) {
                        ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    } else {
                        ListItemDefaults.colors()
                    },
                    modifier = Modifier.clickable { onJump(item.index); onDismiss() },
                )
            }
        }
        Spacer(Modifier.height(16.dp))
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LyricsSheet(
    lyrics: com.micasong.player.data.lyrics.Lyrics?,
    positionMs: Long,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Text(
            "Letra",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        if (lyrics == null || lyrics.lines.isEmpty()) {
            Text(
                "No hay letra disponible para esta pista.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp),
            )
            Spacer(Modifier.height(24.dp))
            return@ModalBottomSheet
        }
        val active = lyrics.activeIndexAt(positionMs)
        val listState = rememberLazyListState()
        LaunchedEffect(active) {
            if (active >= 0) listState.animateScrollToItem(active.coerceAtLeast(0))
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth().heightIn(max = 460.dp),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
        ) {
            itemsIndexed(lyrics.lines) { index, line ->
                Text(
                    line.text.ifBlank { "♪" },
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (index == active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (index == active) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SmartFlowControls(
    smartFlow: com.micasong.player.data.smart.SmartFlowMode?,
    smartQueue: com.micasong.player.data.smart.SmartQueueMode?,
    onSetSmartFlow: (com.micasong.player.data.smart.SmartFlowMode?) -> Unit,
    onSetSmartQueue: (com.micasong.player.data.smart.SmartQueueMode?) -> Unit,
) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        Text("Cola inteligente", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(selected = smartQueue == null, onClick = { onSetSmartQueue(null) }, label = { Text("Off") })
            com.micasong.player.data.smart.SmartQueueMode.entries.forEach { mode ->
                FilterChip(
                    selected = smartQueue == mode,
                    onClick = { onSetSmartQueue(mode) },
                    label = { Text(smartQueueLabel(mode)) },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("Flujo inteligente", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(selected = smartFlow == null, onClick = { onSetSmartFlow(null) }, label = { Text("Off") })
            com.micasong.player.data.smart.SmartFlowMode.entries.forEach { mode ->
                FilterChip(
                    selected = smartFlow == mode,
                    onClick = { onSetSmartFlow(mode) },
                    label = { Text(smartFlowLabel(mode)) },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

private fun smartQueueLabel(m: com.micasong.player.data.smart.SmartQueueMode): String = when (m) {
    com.micasong.player.data.smart.SmartQueueMode.GENRE -> "Género"
    com.micasong.player.data.smart.SmartQueueMode.ARTIST -> "Artista"
    com.micasong.player.data.smart.SmartQueueMode.RANDOM -> "Aleatorio"
}

private fun smartFlowLabel(m: com.micasong.player.data.smart.SmartFlowMode): String = when (m) {
    com.micasong.player.data.smart.SmartFlowMode.SHUFFLE_SPECIALIST -> "Mezcla"
    com.micasong.player.data.smart.SmartFlowMode.TRANSITION_MAESTRO -> "Transiciones"
    com.micasong.player.data.smart.SmartFlowMode.DOUBLE_SHOT -> "Doble artista"
    com.micasong.player.data.smart.SmartFlowMode.ARTIST_FAN -> "Solo artista"
    com.micasong.player.data.smart.SmartFlowMode.ECHO_MATCH -> "Eco"
    com.micasong.player.data.smart.SmartFlowMode.ERA_ENTHUSIAST -> "Época"
    com.micasong.player.data.smart.SmartFlowMode.STEADY_VIBES -> "Ritmo"
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
