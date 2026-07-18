package com.micasong.player.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.micasong.player.data.provider.ProviderConfig
import com.micasong.player.data.provider.ProviderType
import com.micasong.player.data.repository.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProvidersViewModel @Inject constructor(
    private val repository: MediaRepository,
) : ViewModel() {

    val providers = repository.providerConfigs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addServer(
        type: ProviderType,
        name: String,
        url: String,
        username: String,
        secret: String,
    ) {
        val config = ProviderConfig(
            id = 0,
            type = type,
            displayName = name.ifBlank { url },
            primaryUrl = url.trim().ifBlank { null },
            username = username.trim().ifBlank { null },
            secret = secret.ifBlank { null },
        )
        viewModelScope.launch {
            repository.addProvider(config)
            repository.syncAll()
        }
    }

    fun remove(providerId: Long) {
        // Provider ids are PROVIDER_ID_BASE + rowId; recover the row id.
        val rowId = providerId - 1000L
        viewModelScope.launch { repository.removeProvider(rowId) }
    }
}
