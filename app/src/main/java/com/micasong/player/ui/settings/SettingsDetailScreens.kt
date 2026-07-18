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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
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
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var themeMsg by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    val importTheme = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val raw = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } }.getOrNull()
            }
            if (raw == null) { themeMsg = "No se pudo leer el archivo"; return@launch }
            viewModel.importCustomTheme(raw) { ok -> themeMsg = if (ok) "Tema aplicado" else "JSON de tema no válido" }
        }
    }
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

        item { CategoryHeading("Avanzado") }
        item {
            SwitchRow(
                title = "Mantener pantalla encendida",
                subtitle = "No apagar la pantalla mientras la app está en primer plano",
                checked = state.keepScreenOn,
                onChange = viewModel::setKeepScreenOn,
            )
        }
        item {
            SwitchRow(
                title = "Ocultar barra de estado",
                subtitle = "Modo inmersivo",
                checked = state.hideStatusBar,
                onChange = viewModel::setHideStatusBar,
            )
        }
        item {
            SwitchRow(
                title = "Mostrar número de pista",
                subtitle = "En las listas de canciones",
                checked = state.showTrackNumber,
                onChange = viewModel::setShowTrackNumber,
            )
        }
        item { SettingRow(title = "Orientación de pantalla", subtitle = orientationLabel(state.screenOrientation)) }
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                com.micasong.player.data.settings.ScreenOrientation.entries.forEach { o ->
                    FilterChip(
                        selected = state.screenOrientation == o,
                        onClick = { viewModel.setScreenOrientation(o) },
                        label = { Text(orientationLabel(o)) },
                    )
                }
            }
        }

        item { CategoryHeading("Tema personalizado") }
        item {
            SettingRow(
                title = "Importar tema (JSON)",
                subtitle = themeMsg ?: if (state.customThemeJson != null) "Tema personalizado activo" else "Compatible con Material Theme Builder",
                onClick = { importTheme.launch(arrayOf("application/json", "text/*", "*/*")) },
            )
        }
        if (state.customThemeJson != null) {
            item {
                SettingRow(
                    title = "Quitar tema personalizado",
                    subtitle = "Volver a los colores dinámicos o de marca",
                    onClick = { viewModel.clearCustomTheme(); themeMsg = null },
                )
            }
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

        item { CategoryHeading("ReplayGain (normalización)") }
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                com.micasong.player.data.audio.ReplayGainMode.entries.forEach { mode ->
                    FilterChip(
                        selected = state.replayGainMode == mode,
                        onClick = { viewModel.setReplayGainMode(mode) },
                        label = { Text(replayGainLabel(mode)) },
                    )
                }
            }
        }

        item { CategoryHeading("Transiciones") }
        item { SettingRow(title = "Fundido entre pistas", subtitle = if (state.crossfadeMs == 0) "Desactivado" else "${state.crossfadeMs / 1000} s") }
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(0, 2000, 4000, 8000).forEach { ms ->
                    FilterChip(
                        selected = state.crossfadeMs == ms,
                        onClick = { viewModel.setCrossfade(ms) },
                        label = { Text(if (ms == 0) "Off" else "${ms / 1000}s") },
                    )
                }
            }
        }
    }
}

private fun replayGainLabel(m: com.micasong.player.data.audio.ReplayGainMode): String = when (m) {
    com.micasong.player.data.audio.ReplayGainMode.OFF -> "Off"
    com.micasong.player.data.audio.ReplayGainMode.TRACK -> "Pista"
    com.micasong.player.data.audio.ReplayGainMode.ALBUM -> "Álbum"
    com.micasong.player.data.audio.ReplayGainMode.AUTO -> "Auto"
}

/** Avanzado: sync, database preferences and restore-defaults (spec §44 › Advanced). */
@Composable
fun AdvancedSettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    DetailScaffold("Avanzado", onBack) {
        item {
            SettingRow(
                title = "Sincronizar ahora",
                subtitle = "Vuelve a escanear todos los proveedores",
                onClick = viewModel::resync,
            )
        }

        item { CategoryHeading("Base de datos") }
        item {
            SwitchRow(
                title = "Media estrella",
                subtitle = "Permite valoraciones con medias estrellas",
                checked = state.halfStars,
                onChange = viewModel::setHalfStars,
            )
        }
        item {
            SwitchRow(
                title = "Ignorar artículos al ordenar",
                subtitle = "Ignora El/La/Los/The al alfabetizar",
                checked = state.ignoreArticlesOnSort,
                onChange = viewModel::setIgnoreArticles,
            )
        }

        item { CategoryHeading("Mantenimiento") }
        item {
            SettingRow(
                title = "Restaurar valores predeterminados",
                subtitle = "Devuelve todos los ajustes a su estado inicial",
                onClick = viewModel::resetToDefaults,
            )
        }
        item {
            InfoParagraph("Depuración, límites de transcodificación y PIN de ajustes se añadirán en próximas versiones.")
        }
    }
}

private fun orientationLabel(o: com.micasong.player.data.settings.ScreenOrientation): String = when (o) {
    com.micasong.player.data.settings.ScreenOrientation.SYSTEM -> "Sistema"
    com.micasong.player.data.settings.ScreenOrientation.PORTRAIT -> "Vertical"
    com.micasong.player.data.settings.ScreenOrientation.LANDSCAPE -> "Horizontal"
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

/** Sin conexión, caché y descarga (spec §35): real download/cache controls. */
@Composable
fun OfflineSettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    DetailScaffold("Sin conexión, caché y descarga", onBack) {
        item { CategoryHeading("Descargas") }
        item {
            SwitchRow(
                title = "Descargas solo por Wi-Fi",
                subtitle = "No descargar con datos móviles",
                checked = state.downloadsWifiOnly,
                onChange = viewModel::setDownloadsWifiOnly,
            )
        }
        item {
            SwitchRow(
                title = "Guardar favoritos sin conexión",
                subtitle = "Descarga automáticamente las pistas marcadas como favoritas",
                checked = state.autoCacheFavorites,
                onChange = viewModel::setAutoCacheFavorites,
            )
        }
        item {
            SettingRow(
                title = "Actualizar caché automática",
                subtitle = "Vuelve a aplicar las reglas de descarga automática",
                onClick = viewModel::refreshAutoCache,
            )
        }

        item { CategoryHeading("Caché rotativa") }
        item {
            SettingRow(
                title = "Tamaño de la caché rotativa",
                subtitle = "${state.rollingCacheMb} MB",
            )
        }
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(512, 1024, 2048, 4096).forEach { mb ->
                    FilterChip(
                        selected = state.rollingCacheMb == mb,
                        onClick = { viewModel.setRollingCacheMb(mb) },
                        label = { Text(if (mb >= 1024) "${mb / 1024} GB" else "$mb MB") },
                    )
                }
            }
        }
        item {
            SettingRow(
                title = "Liberar espacio",
                subtitle = "Elimina las descargas rotativas más antiguas por encima del límite",
                onClick = viewModel::freeRollingCache,
            )
        }
    }
}

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
