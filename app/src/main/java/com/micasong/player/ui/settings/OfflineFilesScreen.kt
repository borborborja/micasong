package com.micasong.player.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.micasong.player.data.cache.CacheTier
import com.micasong.player.data.cache.DownloadState

/** Manage offline files (spec §35): list downloads with progress and per-item actions. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineFilesScreen(
    onBack: () -> Unit,
    viewModel: OfflineFilesViewModel = hiltViewModel(),
) {
    val rows by viewModel.rows.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Archivos sin conexión") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
            )
        },
    ) { padding ->
        if (rows.isEmpty()) {
            Text(
                "No hay descargas. Usa \"Descargar\" en el menú de una pista para guardarla sin conexión.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(padding).padding(16.dp),
            )
            return@Scaffold
        }
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(vertical = 8.dp)) {
            items(rows, key = { it.trackId }) { row ->
                ListItem(
                    headlineContent = { Text(row.title) },
                    supportingContent = {
                        Column {
                            Text("${row.subtitle} · ${stateLabel(row.state)} · ${tierLabel(row.tier)}")
                            if (row.state == DownloadState.DOWNLOADING) {
                                LinearProgressIndicator(
                                    progress = { row.progress },
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                )
                            }
                        }
                    },
                    trailingContent = {
                        androidx.compose.foundation.layout.Row {
                            if (row.state == DownloadState.FAILED) {
                                IconButton(onClick = { viewModel.retry(row.trackId) }) {
                                    Icon(Icons.Filled.Refresh, contentDescription = "Reintentar")
                                }
                            }
                            IconButton(onClick = { viewModel.remove(row.trackId) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Eliminar descarga")
                            }
                        }
                    },
                )
            }
        }
    }
}

private fun stateLabel(s: DownloadState): String = when (s) {
    DownloadState.QUEUED -> "En cola"
    DownloadState.DOWNLOADING -> "Descargando"
    DownloadState.COMPLETED -> "Descargada"
    DownloadState.FAILED -> "Error"
}

private fun tierLabel(t: CacheTier): String = when (t) {
    CacheTier.PLAYBACK -> "Caché"
    CacheTier.ROLLING -> "Rotativa"
    CacheTier.PERMANENT -> "Permanente"
}
