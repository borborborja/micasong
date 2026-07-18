package com.micasong.player.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.micasong.player.data.repository.MediaRepository
import com.micasong.player.data.smart.SmartPlaylistDefinition
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Backs the smart-playlist rule editor (spec §31): persists the built definition. */
@HiltViewModel
class SmartPlaylistEditorViewModel @Inject constructor(
    private val repository: MediaRepository,
) : ViewModel() {

    fun save(name: String, def: SmartPlaylistDefinition, onDone: () -> Unit) {
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.createSmartPlaylist(name.trim(), def)
            onDone()
        }
    }
}
