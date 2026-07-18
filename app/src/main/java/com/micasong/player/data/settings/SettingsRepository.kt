package com.micasong.player.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.micasong.player.data.audio.EqPresets
import com.micasong.player.data.audio.EqProfile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** Theme modes (spec §25). */
@Serializable
enum class ThemeMode { SYSTEM, LIGHT, DARK, BLACK }

/** Screen-orientation preference (spec §44 › Interfaz › Advanced). */
@Serializable
enum class ScreenOrientation { SYSTEM, PORTRAIT, LANDSCAPE }

/** A snapshot of the user-configurable preferences the UI reacts to. */
@Serializable
data class UserSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val seedColorArgb: Int = 0xFF6B4FA0.toInt(),
    val roundedCorners: Boolean = true,
    val gaplessPlayback: Boolean = true,
    val weightedShuffle: Boolean = true,
    val crossfadeMs: Int = 0,
    val expandPlayerAutomatically: Boolean = false,
    val minPlayedPercentToScrobble: Int = 95,
    val personalMixSize: Int = 200,
    // Offline / cache (spec §35)
    val downloadsWifiOnly: Boolean = false,
    val autoCacheFavorites: Boolean = false,
    val rollingCacheMb: Int = 1024,
    val replayGainMode: com.micasong.player.data.audio.ReplayGainMode = com.micasong.player.data.audio.ReplayGainMode.OFF,
    // Custom theme (spec §25): Material-Theme-Builder JSON, null = built-in/dynamic palette.
    val customThemeJson: String? = null,
    // Interfaz › Advanced (spec §44)
    val keepScreenOn: Boolean = false,
    val hideStatusBar: Boolean = false,
    val screenOrientation: ScreenOrientation = ScreenOrientation.SYSTEM,
    val showTrackNumber: Boolean = true,
    // Database (spec §44)
    val halfStars: Boolean = true,
    val ignoreArticlesOnSort: Boolean = true,
    // Now Playing subtitle string template (spec §27)
    val nowPlayingTemplate: String = "%artist%{ · %album%}",
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val SEED_COLOR = intPreferencesKey("seed_color")
        val ROUNDED = booleanPreferencesKey("rounded_corners")
        val GAPLESS = booleanPreferencesKey("gapless")
        val WEIGHTED_SHUFFLE = booleanPreferencesKey("weighted_shuffle")
        val CROSSFADE = intPreferencesKey("crossfade_ms")
        val EXPAND_PLAYER = booleanPreferencesKey("expand_player")
        val MIN_PLAYED_PCT = intPreferencesKey("min_played_pct")
        val MIX_SIZE = intPreferencesKey("mix_size")
        val EQ_PROFILE = stringPreferencesKey("eq_profile")
        val DOWNLOADS_WIFI_ONLY = booleanPreferencesKey("downloads_wifi_only")
        val AUTO_CACHE_FAVORITES = booleanPreferencesKey("auto_cache_favorites")
        val ROLLING_CACHE_MB = intPreferencesKey("rolling_cache_mb")
        val CUSTOM_THEME = stringPreferencesKey("custom_theme_json")
        val REPLAY_GAIN = stringPreferencesKey("replay_gain_mode")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val HIDE_STATUS_BAR = booleanPreferencesKey("hide_status_bar")
        val ORIENTATION = stringPreferencesKey("screen_orientation")
        val SHOW_TRACK_NUMBER = booleanPreferencesKey("show_track_number")
        val HALF_STARS = booleanPreferencesKey("half_stars")
        val IGNORE_ARTICLES = booleanPreferencesKey("ignore_articles")
        val NOW_PLAYING_TEMPLATE = stringPreferencesKey("now_playing_template")
    }

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** The active equalizer profile (spec §14). Persisted as JSON. */
    val eqProfile: Flow<EqProfile> = context.dataStore.data.map { p ->
        p[Keys.EQ_PROFILE]?.let { runCatching { json.decodeFromString(EqProfile.serializer(), it) }.getOrNull() }
            ?: EqProfile(id = "main", name = "Principal", enabled = false, bands = EqPresets.flat(10))
    }

    suspend fun setEqProfile(profile: EqProfile) = edit {
        it[Keys.EQ_PROFILE] = json.encodeToString(EqProfile.serializer(), profile)
    }

    val settings: Flow<UserSettings> = context.dataStore.data.map { p ->
        UserSettings(
            themeMode = p[Keys.THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            dynamicColor = p[Keys.DYNAMIC_COLOR] ?: true,
            seedColorArgb = p[Keys.SEED_COLOR] ?: 0xFF6B4FA0.toInt(),
            roundedCorners = p[Keys.ROUNDED] ?: true,
            gaplessPlayback = p[Keys.GAPLESS] ?: true,
            weightedShuffle = p[Keys.WEIGHTED_SHUFFLE] ?: true,
            crossfadeMs = p[Keys.CROSSFADE] ?: 0,
            expandPlayerAutomatically = p[Keys.EXPAND_PLAYER] ?: false,
            minPlayedPercentToScrobble = p[Keys.MIN_PLAYED_PCT] ?: 95,
            personalMixSize = p[Keys.MIX_SIZE] ?: 200,
            downloadsWifiOnly = p[Keys.DOWNLOADS_WIFI_ONLY] ?: false,
            autoCacheFavorites = p[Keys.AUTO_CACHE_FAVORITES] ?: false,
            rollingCacheMb = p[Keys.ROLLING_CACHE_MB] ?: 1024,
            customThemeJson = p[Keys.CUSTOM_THEME],
            replayGainMode = p[Keys.REPLAY_GAIN]?.let { runCatching { com.micasong.player.data.audio.ReplayGainMode.valueOf(it) }.getOrNull() }
                ?: com.micasong.player.data.audio.ReplayGainMode.OFF,
            keepScreenOn = p[Keys.KEEP_SCREEN_ON] ?: false,
            hideStatusBar = p[Keys.HIDE_STATUS_BAR] ?: false,
            screenOrientation = p[Keys.ORIENTATION]?.let { runCatching { ScreenOrientation.valueOf(it) }.getOrNull() } ?: ScreenOrientation.SYSTEM,
            showTrackNumber = p[Keys.SHOW_TRACK_NUMBER] ?: true,
            halfStars = p[Keys.HALF_STARS] ?: true,
            ignoreArticlesOnSort = p[Keys.IGNORE_ARTICLES] ?: true,
            nowPlayingTemplate = p[Keys.NOW_PLAYING_TEMPLATE] ?: "%artist%{ · %album%}",
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) = edit { it[Keys.THEME_MODE] = mode.name }
    suspend fun setDynamicColor(enabled: Boolean) = edit { it[Keys.DYNAMIC_COLOR] = enabled }
    suspend fun setSeedColor(argb: Int) = edit { it[Keys.SEED_COLOR] = argb }
    suspend fun setRoundedCorners(enabled: Boolean) = edit { it[Keys.ROUNDED] = enabled }
    suspend fun setGapless(enabled: Boolean) = edit { it[Keys.GAPLESS] = enabled }
    suspend fun setWeightedShuffle(enabled: Boolean) = edit { it[Keys.WEIGHTED_SHUFFLE] = enabled }
    suspend fun setCrossfade(ms: Int) = edit { it[Keys.CROSSFADE] = ms }
    suspend fun setExpandPlayer(enabled: Boolean) = edit { it[Keys.EXPAND_PLAYER] = enabled }
    suspend fun setDownloadsWifiOnly(enabled: Boolean) = edit { it[Keys.DOWNLOADS_WIFI_ONLY] = enabled }
    suspend fun setAutoCacheFavorites(enabled: Boolean) = edit { it[Keys.AUTO_CACHE_FAVORITES] = enabled }
    suspend fun setRollingCacheMb(mb: Int) = edit { it[Keys.ROLLING_CACHE_MB] = mb.coerceAtLeast(0) }
    suspend fun setCustomTheme(json: String?) = edit {
        if (json.isNullOrBlank()) it.remove(Keys.CUSTOM_THEME) else it[Keys.CUSTOM_THEME] = json
    }
    suspend fun setReplayGainMode(mode: com.micasong.player.data.audio.ReplayGainMode) = edit { it[Keys.REPLAY_GAIN] = mode.name }
    suspend fun setKeepScreenOn(v: Boolean) = edit { it[Keys.KEEP_SCREEN_ON] = v }
    suspend fun setHideStatusBar(v: Boolean) = edit { it[Keys.HIDE_STATUS_BAR] = v }
    suspend fun setScreenOrientation(o: ScreenOrientation) = edit { it[Keys.ORIENTATION] = o.name }
    suspend fun setShowTrackNumber(v: Boolean) = edit { it[Keys.SHOW_TRACK_NUMBER] = v }
    suspend fun setHalfStars(v: Boolean) = edit { it[Keys.HALF_STARS] = v }
    suspend fun setIgnoreArticles(v: Boolean) = edit { it[Keys.IGNORE_ARTICLES] = v }
    suspend fun setNowPlayingTemplate(t: String) = edit { it[Keys.NOW_PLAYING_TEMPLATE] = t }

    /** Reset every preference to its default (spec §44 "Restaurar valores predeterminados"). */
    suspend fun resetToDefaults() {
        applySettings(UserSettings())
        setCustomTheme(null)
    }

    /** Overwrite every preference at once, used when restoring a backup (spec §43). */
    suspend fun applySettings(s: UserSettings) = edit {
        it[Keys.THEME_MODE] = s.themeMode.name
        it[Keys.DYNAMIC_COLOR] = s.dynamicColor
        it[Keys.SEED_COLOR] = s.seedColorArgb
        it[Keys.ROUNDED] = s.roundedCorners
        it[Keys.GAPLESS] = s.gaplessPlayback
        it[Keys.WEIGHTED_SHUFFLE] = s.weightedShuffle
        it[Keys.CROSSFADE] = s.crossfadeMs
        it[Keys.EXPAND_PLAYER] = s.expandPlayerAutomatically
        it[Keys.MIN_PLAYED_PCT] = s.minPlayedPercentToScrobble
        it[Keys.MIX_SIZE] = s.personalMixSize
        it[Keys.DOWNLOADS_WIFI_ONLY] = s.downloadsWifiOnly
        it[Keys.AUTO_CACHE_FAVORITES] = s.autoCacheFavorites
        it[Keys.ROLLING_CACHE_MB] = s.rollingCacheMb
        if (s.customThemeJson.isNullOrBlank()) it.remove(Keys.CUSTOM_THEME) else it[Keys.CUSTOM_THEME] = s.customThemeJson
        it[Keys.REPLAY_GAIN] = s.replayGainMode.name
        it[Keys.KEEP_SCREEN_ON] = s.keepScreenOn
        it[Keys.HIDE_STATUS_BAR] = s.hideStatusBar
        it[Keys.ORIENTATION] = s.screenOrientation.name
        it[Keys.SHOW_TRACK_NUMBER] = s.showTrackNumber
        it[Keys.HALF_STARS] = s.halfStars
        it[Keys.IGNORE_ARTICLES] = s.ignoreArticlesOnSort
        it[Keys.NOW_PLAYING_TEMPLATE] = s.nowPlayingTemplate
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }
}
