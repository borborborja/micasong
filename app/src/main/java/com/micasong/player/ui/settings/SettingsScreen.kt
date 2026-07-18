package com.micasong.player.ui.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.micasong.player.BuildConfig

/**
 * Settings root (spec §44): a navigable list of configuration categories plus a "Miscelánea" group
 * of management shortcuts. Each row opens its own detail screen; the actual controls live there.
 */
@Composable
fun SettingsScreen(
    onOpenCategory: (SettingsCategory) -> Unit = {},
) {
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Text(
                "Ajustes",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(16.dp),
            )
        }

        item { CategoryHeading("Configuración") }
        items(SettingsCategory.CONFIG) { category ->
            SettingRow(
                title = category.title,
                subtitle = category.subtitle,
                icon = category.icon,
                onClick = { onOpenCategory(category) },
            )
        }

        item { CategoryHeading("Miscelánea") }
        items(SettingsCategory.MISC) { category ->
            SettingRow(
                title = category.title,
                subtitle = category.subtitle,
                icon = category.icon,
                onClick = { onOpenCategory(category) },
            )
        }

        item { Spacer(Modifier.height(16.dp)) }
        item {
            val edition = if (BuildConfig.IS_FOSS) "edición FOSS" else "edición completa"
            Text(
                "MiCaSong · reproductor multi-proveedor offline-first · $edition",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }
}
