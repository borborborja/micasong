package com.micasong.player.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.micasong.player.data.update.UpdatePhase

/** In-app updater screen: checks the latest GitHub release and installs it (user request). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateScreen(
    onBack: () -> Unit,
    viewModel: UpdateViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        if (state.phase == UpdatePhase.IDLE) viewModel.check()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Actualizar la app") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Versión instalada: ${state.currentVersion}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            when (state.phase) {
                UpdatePhase.CHECKING -> {
                    CircularProgressIndicator()
                    Text("Buscando la última versión…")
                }
                UpdatePhase.UP_TO_DATE -> {
                    Text("Estás en la última versión.", style = MaterialTheme.typography.titleMedium)
                    OutlinedButton(onClick = viewModel::check) { Text("Volver a comprobar") }
                }
                UpdatePhase.AVAILABLE -> {
                    Text("Nueva versión disponible: ${state.latestVersion}", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                    Button(onClick = viewModel::downloadAndInstall) { Text("Descargar e instalar") }
                    Text(
                        "Se descargará en segundo plano y se abrirá el instalador. Puede que tengas que permitir \"instalar apps desconocidas\".",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                UpdatePhase.DOWNLOADING -> {
                    LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
                    Text("Descargando… ${(state.progress * 100).toInt()}%")
                }
                UpdatePhase.READY -> {
                    Text("Descarga completada. Abriendo el instalador…", textAlign = TextAlign.Center)
                    OutlinedButton(onClick = viewModel::downloadAndInstall) { Text("Reintentar instalación") }
                }
                UpdatePhase.ERROR -> {
                    Text(state.message ?: "Error al comprobar actualizaciones.", color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                    OutlinedButton(onClick = viewModel::check) { Text("Reintentar") }
                }
                UpdatePhase.IDLE -> OutlinedButton(onClick = viewModel::check) { Text("Buscar actualizaciones") }
            }
        }
    }
}
