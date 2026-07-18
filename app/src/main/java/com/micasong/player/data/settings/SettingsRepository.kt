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
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }
}
