package com.micasong.player.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.micasong.player.data.provider.JellyfinProvider
import com.micasong.player.data.provider.ProviderConfig
import com.micasong.player.data.provider.ProviderType
import com.micasong.player.data.provider.ServerUrl
import com.micasong.player.data.repository.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProvidersViewModel @Inject constructor(
    private val repository: MediaRepository,
) : ViewModel() {

    val providers = repository.providerConfigs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Live sync progress (spec §9) so the screen shows what's happening after adding a server. */
    val syncState = repository.syncState
    val trackCount = repository.trackCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun clearError() { _error.value = null }

    fun addServer(
        type: ProviderType,
        name: String,
        url: String,
        username: String,
        secret: String,
    ) {
        val normalizedUrl = ServerUrl.normalize(url)
        if (normalizedUrl == null) {
            _error.value = "Introduce una URL válida (p. ej. http://192.168.1.10:4533)"
            return
        }
        viewModelScope.launch {
            _busy.value = true
            try {
                // Validate + persist the provider (quick); if it checks out, kick off the sync on
                // the app scope so it keeps running even if the user leaves this screen.
                val config = buildConfig(type, name, normalizedUrl, username, secret) ?: return@launch
                repository.addProvider(config)
                repository.triggerSync()
            } finally {
                _busy.value = false
            }
        }
    }

    private suspend fun buildConfig(
        type: ProviderType,
        name: String,
        url: String,
        username: String,
        secret: String,
    ): ProviderConfig? {
        val displayName = name.ifBlank { url }
        return when (type) {
            ProviderType.SUBSONIC -> {
                // Validate reachability + auth up front so errors surface instead of an empty library.
                val probe = com.micasong.player.data.provider.SubsonicProvider(
                    ProviderConfig(id = 0, type = type, displayName = displayName, primaryUrl = url,
                        username = username.trim(), secret = secret),
                )
                val error = probe.testConnection()
                if (error != null) {
                    _error.value = error
                    null
                } else {
                    ProviderConfig(
                        id = 0, type = type, displayName = displayName, primaryUrl = url,
                        username = username.trim().ifBlank { null }, secret = secret.ifBlank { null },
                    )
                }
            }
            ProviderType.JELLYFIN -> {
                // Jellyfin needs a token + user id, obtained by logging in with user + password.
                val session = JellyfinProvider.authenticate(url, username.trim(), secret)
                if (session == null) {
                    _error.value = "No se pudo iniciar sesión en Jellyfin. Revisa la URL, el usuario y la contraseña."
                    null
                } else {
                    ProviderConfig(
                        id = 0, type = type, displayName = displayName, primaryUrl = url,
                        username = session.userId, secret = session.token,
                    )
                }
            }
            ProviderType.EMBY -> {
                val session = com.micasong.player.data.provider.EmbyProvider.authenticate(url, username.trim(), secret)
                if (session == null) {
                    _error.value = "No se pudo iniciar sesión en Emby. Revisa la URL, el usuario y la contraseña."
                    null
                } else {
                    ProviderConfig(
                        id = 0, type = type, displayName = displayName, primaryUrl = url,
                        username = session.userId, secret = session.token,
                    )
                }
            }
            ProviderType.AUDIOBOOKSHELF -> {
                val session = com.micasong.player.data.provider.AudioBookShelfProvider.authenticate(url, username.trim(), secret)
                if (session == null) {
                    _error.value = "No se pudo iniciar sesión en AudioBookShelf. Revisa la URL, el usuario y la contraseña."
                    null
                } else {
                    ProviderConfig(id = 0, type = type, displayName = displayName, primaryUrl = url, secret = session.token)
                }
            }
            else -> ProviderConfig(
                id = 0, type = type, displayName = displayName, primaryUrl = url,
                username = username.trim().ifBlank { null }, secret = secret.ifBlank { null },
            )
        }
    }

    fun remove(providerId: Long) {
        val rowId = providerId - 1000L
        viewModelScope.launch { repository.removeProvider(rowId) }
    }
}
