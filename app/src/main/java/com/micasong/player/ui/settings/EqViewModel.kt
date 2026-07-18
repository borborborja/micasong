package com.micasong.player.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.micasong.player.data.audio.EqPresets
import com.micasong.player.data.audio.EqProfile
import com.micasong.player.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EqViewModel @Inject constructor(
    private val settings: SettingsRepository,
) : ViewModel() {

    val profile = settings.eqProfile.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        EqProfile(id = "main", name = "Principal", bands = EqPresets.flat(10)),
    )

    val presetNames = EqPresets.NAMES
    val bandCounts = EqPresets.BAND_COUNTS

    fun setEnabled(enabled: Boolean) = update { it.copy(enabled = enabled) }
    fun setPreamp(db: Float) = update { it.copy(preampDb = db) }
    fun setBassBoost(value: Int) = update { it.copy(bassBoost = value) }
    fun setVirtualizer(value: Int) = update { it.copy(virtualizer = value) }
    fun applyPreset(name: String) = update { it.copy(bands = EqPresets.preset(name, it.bands.size)) }
    fun setBandCount(count: Int) = update { it.copy(bands = EqPresets.flat(count)) }

    fun setBand(index: Int, gainDb: Float) = update { profile ->
        profile.copy(bands = profile.bands.mapIndexed { i, b -> if (i == index) b.copy(gainDb = gainDb) else b })
    }

    private fun update(transform: (EqProfile) -> EqProfile) {
        viewModelScope.launch { settings.setEqProfile(transform(profile.value)) }
    }
}
