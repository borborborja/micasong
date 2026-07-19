package com.micasong.player.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * The settings tree (spec §44), mirrored as a navigable list of categories. The first group are the
 * configuration areas; "Miscelánea" groups the management shortcuts. Each entry owns its own route.
 */
enum class SettingsCategory(
    val route: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
) {
    // ---- Configuración ----
    INTERFACE("settings/interface", "Interfaz", "Tema, colores y apariencia", Icons.Filled.Palette),
    PLAYBACK("settings/playback", "Reproducción", "Ecualizador, gapless y mezclas", Icons.Filled.PlayCircle),
    OFFLINE("settings/offline", "Sin conexión, caché y descarga", "Descargas y almacenamiento", Icons.Filled.CloudDownload),
    ANDROID_AUTO("settings/android_auto", "Android Auto", "Reproducción en el coche", Icons.Filled.DirectionsCar),
    ADVANCED("settings/advanced", "Avanzado", "Sincronización, mantenimiento y depuración", Icons.Filled.Tune),

    // ---- Miscelánea (management shortcuts) ----
    PROVIDERS("providers", "Administrar proveedores de medios", "Este dispositivo · Subsonic · Jellyfin", Icons.Outlined.Devices),
    SYNC("settings/sync", "Administrador de sincronización", "Vuelve a escanear tus proveedores", Icons.Filled.Sync),
    OFFLINE_FILES("settings/offline_files", "Administrar archivos sin conexión", "Descargas guardadas en el dispositivo", Icons.Filled.Storage),
    GENERATED_FILES("settings/generated", "Administrar archivos generados", "Registros y copias de seguridad", Icons.Filled.Folder),
    BACKUP("backup", "Copia de seguridad", "Exporta o restaura tu configuración", Icons.Filled.Backup),
    UPDATE("settings/update", "Actualizar la app", "Busca e instala la última versión de GitHub", Icons.Filled.SystemUpdate);

    companion object {
        val CONFIG = listOf(INTERFACE, PLAYBACK, OFFLINE, ANDROID_AUTO, ADVANCED)
        val MISC = listOf(PROVIDERS, SYNC, OFFLINE_FILES, GENERATED_FILES, BACKUP, UPDATE)
    }
}
