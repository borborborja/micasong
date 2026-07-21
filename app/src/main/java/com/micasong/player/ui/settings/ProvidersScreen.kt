package com.micasong.player.ui.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.micasong.player.data.provider.ProviderConfig
import com.micasong.player.data.provider.ProviderType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProvidersScreen(
    onBack: () -> Unit,
    viewModel: ProvidersViewModel = hiltViewModel(),
) {
    val providers by viewModel.providers.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val syncState by viewModel.syncState.collectAsStateWithLifecycle()
    val trackCount by viewModel.trackCount.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ProviderConfig?>(null) }

    Scaffold(
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { Text("Proveedores de medios") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.syncNow() }, enabled = !syncState.running) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Sincronizar todo")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Añadir servidor")
            }
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(vertical = 8.dp)) {
            // Live connection/sync status (spec §9).
            item {
                val syncing = busy || syncState.running
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    if (syncing) {
                        if (syncState.running && syncState.progress > 0f) {
                            androidx.compose.material3.LinearProgressIndicator(
                                progress = { syncState.progress },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth())
                        }
                        Text(
                            syncState.message.ifBlank { "Conectando y sincronizando…" } +
                                if (syncState.progress > 0f) " ${(syncState.progress * 100).toInt()}%" else "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    } else {
                        Text(
                            "$trackCount pistas en la biblioteca",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            error?.let { message ->
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(message, Modifier.weight(1f), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                        androidx.compose.material3.TextButton(onClick = { viewModel.clearError() }) { Text("OK") }
                    }
                }
            }
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Dns, contentDescription = null)
                    Spacer(Modifier.padding(horizontal = 6.dp))
                    Column {
                        Text("Este dispositivo", style = MaterialTheme.typography.bodyLarge)
                        Text("Local · siempre disponible", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            items(providers, key = { it.id }) { provider ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Dns, contentDescription = null)
                    Spacer(Modifier.padding(horizontal = 6.dp))
                    Column(Modifier.weight(1f)) {
                        Text(provider.displayName, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "${provider.type.name.lowercase().replaceFirstChar { it.uppercase() }} · ${provider.primaryUrl ?: ""}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { viewModel.syncNow(provider.id) }, enabled = !syncState.running) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Volver a sincronizar")
                    }
                    IconButton(onClick = { editing = provider }) {
                        Icon(Icons.Filled.Edit, contentDescription = "Editar")
                    }
                    IconButton(onClick = { viewModel.remove(provider.id) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Eliminar")
                    }
                }
            }
        }
    }

    if (showAdd) {
        ServerDialog(
            onDismiss = { showAdd = false },
            onConfirm = { type, name, url, user, secret ->
                viewModel.addServer(type, name, url, user, secret)
                showAdd = false
            },
        )
    }
    editing?.let { current ->
        ServerDialog(
            initial = current,
            onDismiss = { editing = null },
            onConfirm = { type, name, url, user, secret ->
                viewModel.updateServer(current.id, type, name, url, user, secret)
                editing = null
            },
        )
    }
}

/** Credential-stored backends can prefill user/secret on edit; token backends must re-login. */
private fun storesPlainCredentials(type: ProviderType): Boolean =
    type == ProviderType.SUBSONIC || type == ProviderType.KODI || type == ProviderType.WEBDAV || type == ProviderType.PLEX

@Composable
private fun ServerDialog(
    onDismiss: () -> Unit,
    onConfirm: (ProviderType, String, String, String, String) -> Unit,
    initial: ProviderConfig? = null,
) {
    var type by remember { mutableStateOf(initial?.type ?: ProviderType.SUBSONIC) }
    var name by remember { mutableStateOf(initial?.displayName ?: "") }
    var url by remember { mutableStateOf(initial?.primaryUrl ?: "") }
    var user by remember { mutableStateOf(initial?.takeIf { storesPlainCredentials(it.type) }?.username ?: "") }
    var secret by remember { mutableStateOf(initial?.takeIf { storesPlainCredentials(it.type) }?.secret ?: "") }
    val isEdit = initial != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEdit) "Editar servidor" else "Añadir servidor") },
        text = {
            Column {
                // The backend type is fixed once created (the library ids derive from it).
                if (!isEdit) {
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(type == ProviderType.SUBSONIC, { type = ProviderType.SUBSONIC }, { Text("Subsonic") })
                        FilterChip(type == ProviderType.JELLYFIN, { type = ProviderType.JELLYFIN }, { Text("Jellyfin") })
                        FilterChip(type == ProviderType.EMBY, { type = ProviderType.EMBY }, { Text("Emby") })
                        FilterChip(type == ProviderType.PLEX, { type = ProviderType.PLEX }, { Text("Plex") })
                        FilterChip(type == ProviderType.KODI, { type = ProviderType.KODI }, { Text("Kodi") })
                        FilterChip(type == ProviderType.WEBDAV, { type = ProviderType.WEBDAV }, { Text("WebDAV") })
                        FilterChip(type == ProviderType.AUDIOBOOKSHELF, { type = ProviderType.AUDIOBOOKSHELF }, { Text("AudioBookShelf") })
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Text(
                    if (isEdit && !storesPlainCredentials(type)) {
                        "Deja usuario y contraseña en blanco para conservar la sesión actual, o rellénalos para iniciar sesión de nuevo."
                    } else {
                        providerHint(type)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(name, { name = it }, label = { Text("Nombre") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    url, { url = it },
                    label = { Text("URL del servidor") },
                    placeholder = { Text("http://192.168.1.10:4533") },
                    supportingText = { Text("Incluye http:// o https:// y el puerto. No añadas /rest al final.") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                if (type != ProviderType.PLEX) {
                    OutlinedTextField(user, { user = it }, label = { Text("Usuario") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                OutlinedTextField(
                    secret, { secret = it },
                    label = {
                        Text(
                            when (type) {
                                ProviderType.PLEX -> "Token de Plex (X-Plex-Token)"
                                ProviderType.JELLYFIN, ProviderType.EMBY -> "Token / contraseña"
                                else -> "Contraseña"
                            }
                        )
                    },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(type, name, url, user, secret) }, enabled = url.isNotBlank()) {
                Text(if (isEdit) "Guardar y sincronizar" else "Conectar")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

private fun providerHint(type: ProviderType): String = when (type) {
    ProviderType.SUBSONIC -> "Subsonic/OpenSubsonic/Navidrome/Gonic. Usa tu usuario y contraseña; se comprobará la conexión al conectar."
    ProviderType.JELLYFIN -> "Jellyfin. Inicia sesión con tu usuario y contraseña."
    ProviderType.EMBY -> "Emby. Inicia sesión con tu usuario y contraseña."
    ProviderType.PLEX -> "Plex. Pega un token de acceso (X-Plex-Token)."
    ProviderType.KODI -> "Kodi 19+ con el servidor web activado. Usuario y contraseña del servidor web."
    ProviderType.WEBDAV -> "WebDAV. Usuario y contraseña; los archivos se listan por carpeta."
    ProviderType.AUDIOBOOKSHELF -> "AudioBookShelf (experimental). Usuario y contraseña."
    else -> ""
}
