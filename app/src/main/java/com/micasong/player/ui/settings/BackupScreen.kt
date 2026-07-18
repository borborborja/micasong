package com.micasong.player.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext
import com.micasong.player.data.backup.BackupContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    onBack: () -> Unit,
    viewModel: BackupViewModel = hiltViewModel(),
) {
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var password by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    // SAF: create a .micabkp document to write the encrypted archive into.
    val createLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            status = "Creando copia…"
            val result = runCatching {
                val bytes = viewModel.buildArchive(password)
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                        ?: error("No se pudo abrir el archivo")
                }
                bytes.size
            }
            status = result.fold(
                onSuccess = { "Copia guardada (${it / 1024} KB)" },
                onFailure = { "Error al crear la copia: ${it.message}" },
            )
            busy = false
        }
    }

    // SAF: open an existing archive to restore from.
    val openLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            status = "Restaurando…"
            val result = runCatching {
                val bytes = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: error("No se pudo leer el archivo")
                }
                viewModel.restore(bytes, password)
            }
            status = result.fold(
                onSuccess = { r ->
                    if (r.ok) {
                        "${r.message}: ${r.playlistsRestored} listas, ${r.providersRestored} servidores" +
                            if (r.settingsRestored) ", ajustes" else ""
                    } else r.message
                },
                onFailure = { "Error al restaurar: ${it.message}" },
            )
            busy = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Copia de seguridad") },
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
                Text(
                    "La copia se cifra con tu contraseña porque puede contener credenciales de servidores. Guárdala en un lugar seguro: sin la contraseña no se puede restaurar.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
            }

            item {
                Text("Contenido", style = MaterialTheme.typography.titleMedium)
            }
            items(BackupViewModel.SUPPORTED) { content ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = content in selection,
                        onCheckedChange = { viewModel.toggle(content) },
                    )
                    Text(contentLabel(content), Modifier.padding(start = 8.dp))
                }
            }

            item {
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Contraseña") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                val canRun = password.isNotBlank() && selection.isNotEmpty() && !busy
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { createLauncher.launch("micasong-${System.currentTimeMillis()}.micabkp") },
                        enabled = canRun,
                        modifier = Modifier.weight(1f),
                    ) { Text("Crear copia") }
                    OutlinedButton(
                        onClick = { openLauncher.launch(arrayOf("*/*")) },
                        enabled = password.isNotBlank() && !busy,
                        modifier = Modifier.weight(1f),
                    ) { Text("Restaurar") }
                }
                status?.let {
                    Spacer(Modifier.height(16.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

private fun contentLabel(content: BackupContent): String = when (content) {
    BackupContent.SETTINGS -> "Ajustes"
    BackupContent.PROVIDERS -> "Servidores (incluye credenciales)"
    BackupContent.PLAYLISTS -> "Listas de reproducción"
    else -> content.name
}
