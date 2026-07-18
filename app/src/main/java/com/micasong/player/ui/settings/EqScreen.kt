package com.micasong.player.ui.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqScreen(
    onBack: () -> Unit,
    viewModel: EqViewModel = hiltViewModel(),
) {
    val profile by viewModel.profile.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ecualizador") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(16.dp)) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Activar ecualizador", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    Switch(checked = profile.enabled, onCheckedChange = viewModel::setEnabled)
                }
                Text(
                    "Solo se aplica en reproducción local (no al hacer cast).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
            }

            item {
                Text("Bandas", style = MaterialTheme.typography.titleMedium)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    viewModel.bandCounts.forEach { count ->
                        FilterChip(
                            selected = profile.bands.size == count,
                            onClick = { viewModel.setBandCount(count) },
                            label = { Text("$count") },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            item {
                Text("Presets", style = MaterialTheme.typography.titleMedium)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    viewModel.presetNames.forEach { name ->
                        FilterChip(selected = false, onClick = { viewModel.applyPreset(name) }, label = { Text(name) })
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            item {
                SliderRow(
                    label = "Preamp",
                    value = profile.preampDb,
                    valueLabel = "%+.1f dB".format(profile.preampDb),
                    range = -12f..12f,
                    onChange = viewModel::setPreamp,
                )
            }

            itemsIndexed(profile.bands) { index, band ->
                SliderRow(
                    label = freqLabel(band.freqHz),
                    value = band.gainDb,
                    valueLabel = "%+.1f".format(band.gainDb),
                    range = -12f..12f,
                    onChange = { viewModel.setBand(index, it) },
                )
            }

            item {
                Spacer(Modifier.height(8.dp))
                SliderRow(
                    label = "Refuerzo de graves",
                    value = profile.bassBoost.toFloat(),
                    valueLabel = "${profile.bassBoost}",
                    range = 0f..1000f,
                    onChange = { viewModel.setBassBoost(it.toInt()) },
                )
                SliderRow(
                    label = "Virtualizador",
                    value = profile.virtualizer.toFloat(),
                    valueLabel = "${profile.virtualizer}",
                    range = 0f..1000f,
                    onChange = { viewModel.setVirtualizer(it.toInt()) },
                )
            }
        }
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    valueLabel: String,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Text(valueLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
        Slider(value = value.coerceIn(range.start, range.endInclusive), onValueChange = onChange, valueRange = range)
    }
}

private fun freqLabel(hz: Int): String = if (hz >= 1000) "${hz / 1000}k Hz" else "$hz Hz"
