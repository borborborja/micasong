package com.micasong.player.ui.queues

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.micasong.player.ui.PlayerViewModel

/** Multiple independent playback queues (spec §16): save the current queue, load or delete saved ones. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColasScreen(
    onBack: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val book by viewModel.queueBook.collectAsStateWithLifecycle(
        initialValue = com.micasong.player.data.queue.QueueBook()
    )
    var showSave by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Colas") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(vertical = 8.dp)) {
            item {
                ListItem(
                    headlineContent = { Text("Guardar cola actual", color = MaterialTheme.colorScheme.primary) },
                    leadingContent = { Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.clickable { showSave = true },
                )
            }
            if (book.queues.isEmpty()) {
                item {
                    Text(
                        "No hay colas guardadas. Guarda la cola actual para cambiar entre música y audiolibros sin perder el sitio.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            items(book.queues, key = { it.id }) { q ->
                ListItem(
                    headlineContent = { Text(q.name) },
                    supportingContent = { Text("${q.trackIds.size} pistas" + if (q.id == book.activeId) " · activa" else "") },
                    leadingContent = { Icon(Icons.Filled.QueueMusic, contentDescription = null) },
                    trailingContent = {
                        IconButton(onClick = { viewModel.deleteQueue(q.id) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Eliminar cola")
                        }
                    },
                    modifier = Modifier.clickable { viewModel.loadQueue(q.id) },
                )
            }
        }
    }

    if (showSave) {
        AlertDialog(
            onDismissRequest = { showSave = false },
            title = { Text("Guardar cola") },
            text = {
                Column {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nombre") }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.saveCurrentQueue(name); name = ""; showSave = false }) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = { showSave = false }) { Text("Cancelar") } },
        )
    }
}
