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
    fun resync() = repository.triggerSync()

    // ---- Custom theme (spec §25) ----
    /** Import a Material-Theme-Builder JSON; returns false (via callback) if it isn't a valid theme. */
    fun importCustomTheme(rawJson: String, onResult: (Boolean) -> Unit) {
        val theme = com.micasong.player.data.theme.ThemeJson.parse(rawJson)
        if (theme == null) { onResult(false); return }
        viewModelScope.launch {
            settings.setCustomTheme(com.micasong.player.data.theme.ThemeJson.toJson(theme))
            onResult(true)
        }
    }

    fun clearCustomTheme() = viewModelScope.launch { settings.setCustomTheme(null) }

    // ---- Audio (spec §13, §15) ----
    fun setReplayGainMode(mode: com.micasong.player.data.audio.ReplayGainMode) =
        viewModelScope.launch { settings.setReplayGainMode(mode) }

    fun setCrossfade(ms: Int) = viewModelScope.launch { settings.setCrossfade(ms) }

    // ---- Interfaz › Advanced + Database (spec §44) ----
    fun setKeepScreenOn(v: Boolean) = viewModelScope.launch { settings.setKeepScreenOn(v) }
    fun setHideStatusBar(v: Boolean) = viewModelScope.launch { settings.setHideStatusBar(v) }
    fun setScreenOrientation(o: com.micasong.player.data.settings.ScreenOrientation) = viewModelScope.launch { settings.setScreenOrientation(o) }
    fun setShowTrackNumber(v: Boolean) = viewModelScope.launch { settings.setShowTrackNumber(v) }
    fun setHalfStars(v: Boolean) = viewModelScope.launch { settings.setHalfStars(v) }
    fun setIgnoreArticles(v: Boolean) = viewModelScope.launch { settings.setIgnoreArticles(v) }
    fun resetToDefaults() = viewModelScope.launch { settings.resetToDefaults() }
    fun setNowPlayingTemplate(t: String) = viewModelScope.launch { settings.setNowPlayingTemplate(t) }
    fun setAutoTab(tab: String, enabled: Boolean) = viewModelScope.launch { settings.setAutoTab(tab, enabled) }

    // ---- Offline / cache (spec §35) ----
    fun setDownloadsWifiOnly(v: Boolean) = viewModelScope.launch { settings.setDownloadsWifiOnly(v) }
    fun setRollingCacheMb(mb: Int) = viewModelScope.launch { settings.setRollingCacheMb(mb) }

    fun setAutoCacheFavorites(v: Boolean) = viewModelScope.launch {
        settings.setAutoCacheFavorites(v)
        if (v) repository.runAutoCacheReconcile(com.micasong.player.data.cache.AutoCacheRules(cacheFavorites = true))
    }

    /** Re-run automatic caching now against the current rules. */
    fun refreshAutoCache() = viewModelScope.launch {
        val rules = com.micasong.player.data.cache.AutoCacheRules(cacheFavorites = state.value.autoCacheFavorites)
        repository.runAutoCacheReconcile(rules)
    }

    /** Trim the rolling cache down to the configured size. */
    fun freeRollingCache() = viewModelScope.launch {
        repository.evictRollingCache(state.value.rollingCacheMb * 1024L * 1024L)
    }
}
