package com.micasong.player.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.micasong.player.data.settings.ThemeMode

@Composable
fun SettingsScreen(
    onOpenProviders: () -> Unit = {},
    onOpenEqualizer: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Text(
                "Ajustes",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(16.dp),
            )
        }

        // ---- Interfaz: tema, fuente y colores (spec §44) ----
        item { CategoryHeading("Interfaz") }
        item {
            SettingRow(title = "Modo del tema", subtitle = themeLabel(state.themeMode)) {}
        }
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

        // ---- Reproducción (spec §11, §17) ----
        item { CategoryHeading("Reproducción") }
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
            SwitchRow(
                title = "Expandir el reproductor automáticamente",
                subtitle = "Al empezar la reproducción",
                checked = state.expandPlayerAutomatically,
                onChange = viewModel::setExpandPlayer,
            )
        }
        item {
            SettingRow(
                title = "Tamaño de las mezclas personales",
                subtitle = "${state.personalMixSize} pistas",
            ) {}
        }

        // ---- Biblioteca / proveedores (spec §4, §9) ----
        item { CategoryHeading("Biblioteca") }
        item {
            SettingRow(
                title = "Sincronizar ahora",
                subtitle = "Vuelve a escanear todos los proveedores",
            ) { viewModel.resync() }
        }
        item {
            SettingRow(
                title = "Proveedores de medios",
                subtitle = "Este dispositivo · añadir Subsonic, Jellyfin, Plex…",
                onClick = onOpenProviders,
            )
        }

        item { Spacer(Modifier.height(16.dp)) }
        item {
            val edition = if (com.micasong.player.BuildConfig.IS_FOSS) "edición FOSS" else "edición completa"
            Text(
                "MiCaSong · reproductor multi-proveedor offline-first · $edition",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }
}

@Composable
private fun CategoryHeading(text: String) {
    Row(Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.height(18.dp))
        Spacer(Modifier.height(0.dp))
        Text(
            text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun SettingRow(title: String, subtitle: String, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

private fun themeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> "Sistema"
    ThemeMode.LIGHT -> "Claro"
    ThemeMode.DARK -> "Oscuro"
    ThemeMode.BLACK -> "Negro"
}
