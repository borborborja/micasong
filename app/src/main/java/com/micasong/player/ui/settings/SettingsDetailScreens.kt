package com.micasong.player.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.micasong.player.data.settings.ThemeMode

/** Shared scaffold for every settings detail screen: a top bar with a back button. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailScaffold(title: String, onBack: () -> Unit, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(vertical = 8.dp), content = content)
    }
}

/** Interfaz: theme, dynamic color and image corners (spec §44 › Interfaz). */
@Composable
fun InterfazSettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    DetailScaffold("Interfaz", onBack) {
        item { CategoryHeading("Tema") }
        item { SettingRow(title = "Modo del tema", subtitle = themeLabel(state.themeMode)) }
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = state.themeMode == mode,
                        onClick = { viewModel.setThemeMode(mode) },
                        label = { Text(themeLabel(mode)) },
                    )
                }
            }
        }
        item {
            SwitchRow(
                title = "Material You (color dinámico)",
                subtitle = "Genera la paleta desde el fondo de pantalla",
                checked = state.dynamicColor,
                onChange = viewModel::setDynamicColor,
            )
        }
        item {
            SwitchRow(
                title = "Esquinas redondeadas",
                subtitle = if (state.roundedCorners) "Activado" else "Imágenes cuadradas",
                checked = state.roundedCorners,
                onChange = viewModel::setRoundedCorners,
            )
        }
        item {
            SwitchRow(
                title = "Expandir el reproductor automáticamente",
                subtitle = "Al empezar la reproducción",
                checked = state.expandPlayerAutomatically,
                onChange = viewModel::setExpandPlayer,
            )
        }
    }
}

/** Reproducción: equalizer, gapless, weighted shuffle and mixes (spec §44 › Playback). */
@Composable
fun ReproduccionSettingsScreen(
    onBack: () -> Unit,
    onOpenEqualizer: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    DetailScaffold("Reproducción", onBack) {
        item {
            SettingRow(
                title = "Ecualizador",
                subtitle = "GEQ 5/10/15/31 bandas · presets · preamp · solo local",
                onClick = onOpenEqualizer,
            )
        }
        item {
            SwitchRow(
                title = "Reproducción sin cortes (gapless)",
                subtitle = "Transiciones continuas entre pistas",
                checked = state.gaplessPlayback,
                onChange = viewModel::setGapless,
            )
        }
        item {
            SwitchRow(
                title = "Aleatorio ponderado",
                subtitle = "Minimiza repeticiones de artista y álbum",
                checked = state.weightedShuffle,
                onChange = viewModel::setWeightedShuffle,
            )
        }
        item {
            SettingRow(
                title = "Tamaño de las mezclas personales",
                subtitle = "${state.personalMixSize} pistas",
            )
        }
    }
}

/** Avanzado: sync now + a note (spec §44 › Advanced). */
@Composable
fun AdvancedSettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    DetailScaffold("Avanzado", onBack) {
        item {
            SettingRow(
                title = "Sincronizar ahora",
                subtitle = "Vuelve a escanear todos los proveedores",
                onClick = viewModel::resync,
            )
        }
        item {
            InfoParagraph(
                "Depuración, límites de transcodificación, PIN de ajustes y limpieza de caché " +
                    "se añadirán en próximas versiones.",
            )
        }
    }
}

/** Sync manager (spec §44 Miscelánea): trigger a full re-scan of every provider. */
@Composable
fun SyncManagerScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    DetailScaffold("Administrador de sincronización", onBack) {
        item {
            InfoParagraph("Vuelve a escanear la biblioteca de todos los proveedores configurados.")
        }
        item {
            Button(
                onClick = { viewModel.resync() },
                modifier = Modifier.padding(horizontal = 16.dp),
            ) { Text("Sincronizar todo") }
        }
    }
}

/** Sin conexión, caché y descarga — placeholder until the download settings are wired (§44). */
@Composable
fun OfflineSettingsScreen(onBack: () -> Unit) = InfoScreen(
    "Sin conexión, caché y descarga", onBack,
    "Aquí podrás elegir la calidad de descarga, el tamaño de la caché y el modo sin conexión. " +
        "El motor de descargas ya existe; los ajustes llegarán en una próxima versión.",
)

/** Android Auto — informational (§44). */
@Composable
fun AndroidAutoSettingsScreen(onBack: () -> Unit) = InfoScreen(
    "Android Auto", onBack,
    "MiCaSong ya es compatible con Android Auto: conecta el teléfono al coche y navega por tu " +
        "biblioteca, favoritos y búsquedas por voz. Las opciones de personalización llegarán pronto.",
)

/** Manage generated files — placeholder (§44). */
@Composable
fun GeneratedFilesScreen(onBack: () -> Unit) = InfoScreen(
    "Administrar archivos generados", onBack,
    "Registros de depuración y copias de seguridad generadas aparecerán aquí para compartirlos o borrarlos.",
)

@Composable
private fun InfoScreen(title: String, onBack: () -> Unit, text: String) {
    DetailScaffold(title, onBack) {
        item { InfoParagraph(text) }
    }
}

@Composable
private fun InfoParagraph(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(16.dp),
    )
}
