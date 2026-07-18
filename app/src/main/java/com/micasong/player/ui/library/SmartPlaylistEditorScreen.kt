package com.micasong.player.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.hilt.navigation.compose.hiltViewModel
import com.micasong.player.data.smart.FilterNode
import com.micasong.player.data.smart.FilterOperator
import com.micasong.player.data.smart.FilterTarget
import com.micasong.player.data.smart.MatchMode
import com.micasong.player.data.smart.SmartPlaylistDefinition
import com.micasong.player.data.smart.SortDirection
import com.micasong.player.data.smart.SortField

/**
 * Rule editor for a dynamic smart playlist (spec §31): a flat list of rules combined with ALL/ANY,
 * plus sort field/direction and an optional limit. Saving persists a [SmartPlaylistDefinition].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartPlaylistEditorScreen(
    onBack: () -> Unit,
    viewModel: SmartPlaylistEditorViewModel = hiltViewModel(),
) {
    var name by remember { mutableStateOf("") }
    var match by remember { mutableStateOf(MatchMode.ALL) }
    val rules: SnapshotStateList<FilterNode.Rule> = remember {
        mutableListOf(FilterNode.Rule(FilterTarget.FAVORITE, FilterOperator.EQUALS, "true")).toMutableStateList()
    }
    var sortField by remember { mutableStateOf(SortField.TITLE) }
    var sortDir by remember { mutableStateOf(SortDirection.ASC) }
    var limitText by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nueva lista inteligente") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item {
                Text("Coincidir", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = match == MatchMode.ALL, onClick = { match = MatchMode.ALL }, label = { Text("Todas las reglas") })
                    FilterChip(selected = match == MatchMode.ANY, onClick = { match = MatchMode.ANY }, label = { Text("Cualquier regla") })
                }
            }

            item { Text("Reglas", style = MaterialTheme.typography.titleMedium) }
            itemsIndexed(rules) { index, rule ->
                RuleRow(
                    rule = rule,
                    onChange = { rules[index] = it },
                    onRemove = { if (rules.size > 1) rules.removeAt(index) },
                )
            }
            item {
                TextButton(onClick = { rules.add(FilterNode.Rule(FilterTarget.ARTIST, FilterOperator.CONTAINS, "")) }) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text("  Añadir regla")
                }
            }

            item {
                Text("Ordenar por", style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EnumPicker(current = sortField, options = SortField.entries, labelFor = ::sortLabel) { sortField = it }
                    FilterChip(
                        selected = sortDir == SortDirection.DESC,
                        onClick = { sortDir = if (sortDir == SortDirection.ASC) SortDirection.DESC else SortDirection.ASC },
                        label = { Text(if (sortDir == SortDirection.ASC) "Ascendente" else "Descendente") },
                    )
                }
            }

            item {
                OutlinedTextField(
                    value = limitText,
                    onValueChange = { t -> limitText = t.filter { it.isDigit() } },
                    label = { Text("Límite (opcional)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item {
                Button(
                    onClick = {
                        val def = SmartPlaylistDefinition(
                            filter = FilterNode.Group(match, rules.toList()),
                            sortField = sortField,
                            sortDirection = sortDir,
                            limit = limitText.toIntOrNull()?.takeIf { it > 0 },
                        )
                        viewModel.save(name, def, onBack)
                    },
                    enabled = name.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Guardar lista inteligente") }
            }
        }
    }
}

@Composable
private fun RuleRow(rule: FilterNode.Rule, onChange: (FilterNode.Rule) -> Unit, onRemove: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EnumPicker(current = rule.target, options = FilterTarget.entries, labelFor = ::targetLabel) { onChange(rule.copy(target = it)) }
            EnumPicker(current = rule.operator, options = FilterOperator.entries, labelFor = ::operatorLabel) { onChange(rule.copy(operator = it)) }
            IconButton(onClick = onRemove) { Icon(Icons.Filled.Close, contentDescription = "Quitar regla") }
        }
        if (rule.operator != FilterOperator.IS_PRESENT && rule.operator != FilterOperator.IS_MISSING) {
            OutlinedTextField(
                value = rule.value,
                onValueChange = { onChange(rule.copy(value = it)) },
                label = { Text("Valor") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun <T> EnumPicker(current: T, options: List<T>, labelFor: (T) -> String, onSelect: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }) { Text(labelFor(current)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(labelFor(option)) }, onClick = { onSelect(option); open = false })
            }
        }
    }
}

private fun targetLabel(t: FilterTarget): String = when (t) {
    FilterTarget.TITLE -> "Título"
    FilterTarget.ARTIST -> "Artista"
    FilterTarget.ALBUM -> "Álbum"
    FilterTarget.GENRE -> "Género"
    FilterTarget.YEAR -> "Año"
    FilterTarget.RATING -> "Valoración"
    FilterTarget.DURATION_MS -> "Duración (ms)"
    FilterTarget.PLAY_COUNT -> "Reproducciones"
    FilterTarget.SKIP_COUNT -> "Saltos"
    FilterTarget.LAST_PLAYED -> "Última reproducción"
    FilterTarget.FAVORITE -> "Favorito"
    FilterTarget.IS_AUDIOBOOK -> "Es audiolibro"
    FilterTarget.BPM -> "BPM"
    FilterTarget.AVAILABLE_OFFLINE -> "Disponible sin conexión"
    FilterTarget.THUMBNAIL -> "Carátula"
}

private fun operatorLabel(o: FilterOperator): String = when (o) {
    FilterOperator.EQUALS -> "es igual a"
    FilterOperator.NOT_EQUALS -> "no es"
    FilterOperator.CONTAINS -> "contiene"
    FilterOperator.NOT_CONTAINS -> "no contiene"
    FilterOperator.GREATER -> "mayor que"
    FilterOperator.LESS -> "menor que"
    FilterOperator.GREATER_EQUALS -> "≥"
    FilterOperator.LESS_EQUALS -> "≤"
    FilterOperator.IS_PRESENT -> "existe"
    FilterOperator.IS_MISSING -> "falta"
}

private fun sortLabel(s: SortField): String = when (s) {
    SortField.TITLE -> "Título"
    SortField.ARTIST -> "Artista"
    SortField.ALBUM -> "Álbum"
    SortField.YEAR -> "Año"
    SortField.RATING -> "Valoración"
    SortField.PLAY_COUNT -> "Reproducciones"
    SortField.LAST_PLAYED -> "Última reproducción"
    SortField.DURATION -> "Duración"
    SortField.RANDOM -> "Aleatorio"
    SortField.STABLE_RANDOM -> "Aleatorio estable"
}
