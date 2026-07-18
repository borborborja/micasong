package com.micasong.player.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.micasong.player.data.repository.MediaRepository
import com.micasong.player.data.settings.SettingsRepository
import com.micasong.player.data.settings.ThemeMode
import com.micasong.player.data.settings.UserSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val repository: MediaRepository,
) : ViewModel() {

    val state = settings.settings.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), UserSettings()
    )

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { settings.setThemeMode(mode) }
    fun setDynamicColor(v: Boolean) = viewModelScope.launch { settings.setDynamicColor(v) }
    fun setRoundedCorners(v: Boolean) = viewModelScope.launch { settings.setRoundedCorners(v) }
    fun setGapless(v: Boolean) = viewModelScope.launch { settings.setGapless(v) }
    fun setWeightedShuffle(v: Boolean) = viewModelScope.launch { settings.setWeightedShuffle(v) }
    fun setExpandPlayer(v: Boolean) = viewModelScope.launch { settings.setExpandPlayer(v) }
    fun resync() = viewModelScope.launch { repository.syncAll() }
}
