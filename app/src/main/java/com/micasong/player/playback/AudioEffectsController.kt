package com.micasong.player.playback

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import android.util.Log
import com.micasong.player.data.audio.EqProfile

/**
 * Attaches the equalizer chain to ExoPlayer's audio session (spec §14). Wraps the platform
 * `audiofx` effects that MiCaSong exposes to the user:
 *  - **Preamp** (via [LoudnessEnhancer]) — pre-EQ gain to avoid clipping,
 *  - **Equalizer** (GEQ) — per-band levels with presets,
 *  - **Virtualizer** — spatialization,
 *  - **Bass boost**.
 *
 * The equalizer applies only to **local playback** — never when casting (spec §14). The richer
 * PEQ / 256-band expert mode and AutoEQ import (§14, §11-bis) are later phases; this controller
 * covers the GEQ chain end-to-end on real hardware.
 */
class AudioEffectsController {

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var preamp: LoudnessEnhancer? = null

    private var enabled = false
    var bandCount: Int = 0
        private set

    /** (Re)attach every effect to a new audio session id. Safe to call repeatedly. */
    fun attach(audioSessionId: Int) {
        release()
        if (audioSessionId == 0) return
        runCatching {
            equalizer = Equalizer(PRIORITY, audioSessionId).also { it.enabled = enabled }
            bandCount = equalizer?.numberOfBands?.toInt() ?: 0
            bassBoost = BassBoost(PRIORITY, audioSessionId).also { it.enabled = enabled }
            virtualizer = Virtualizer(PRIORITY, audioSessionId).also { it.enabled = enabled }
            preamp = LoudnessEnhancer(audioSessionId).also { it.enabled = enabled }
        }.onFailure { Log.w(TAG, "attach failed: ${it.message}") }
    }

    fun setEnabled(value: Boolean) {
        enabled = value
        runCatching {
            equalizer?.enabled = value
            bassBoost?.enabled = value
            virtualizer?.enabled = value
            preamp?.enabled = value
        }.onFailure { Log.w(TAG, "setEnabled failed: ${it.message}") }
    }

    /** Center frequencies (Hz) of each GEQ band, for drawing the UI (spec §14 waveform graphs). */
    fun bandFrequencies(): List<Int> {
        val eq = equalizer ?: return emptyList()
        return (0 until bandCount).map { (eq.getCenterFreq(it.toShort()) / 1000) }
    }

    /** Allowed band level range in millibels (e.g. -1500..1500). */
    fun bandLevelRange(): IntRange {
        val range = equalizer?.bandLevelRange ?: return 0..0
        return range[0].toInt()..range[1].toInt()
    }

    fun setBandLevel(band: Int, millibel: Int) {
        runCatching { equalizer?.setBandLevel(band.toShort(), millibel.toShort()) }
            .onFailure { Log.w(TAG, "setBandLevel failed: ${it.message}") }
    }

    fun getBandLevel(band: Int): Int = equalizer?.getBandLevel(band.toShort())?.toInt() ?: 0

    fun presetNames(): List<String> {
        val eq = equalizer ?: return emptyList()
        return (0 until eq.numberOfPresets).map { eq.getPresetName(it.toShort()) }
    }

    fun usePreset(index: Int) {
        runCatching { equalizer?.usePreset(index.toShort()) }
            .onFailure { Log.w(TAG, "usePreset failed: ${it.message}") }
    }

    /** Bass boost strength, 0..1000 (spec §14 stage 8). */
    fun setBassBoost(strength: Int) {
        runCatching { bassBoost?.setStrength(strength.coerceIn(0, 1000).toShort()) }
            .onFailure { Log.w(TAG, "bassBoost failed: ${it.message}") }
    }

    /** Virtualizer strength, 0..1000 (spec §14 stage 1). */
    fun setVirtualizer(strength: Int) {
        runCatching { virtualizer?.setStrength(strength.coerceIn(0, 1000).toShort()) }
            .onFailure { Log.w(TAG, "virtualizer failed: ${it.message}") }
    }

    /** Preamp gain in millibels via loudness enhancer (spec §14 stage 2). */
    fun setPreampGain(millibel: Int) {
        runCatching { preamp?.setTargetGain(millibel) }
            .onFailure { Log.w(TAG, "preamp failed: ${it.message}") }
    }

    /**
     * Apply a full [EqProfile] (spec §14): enable state, preamp, per-band gains (the profile's
     * curve is resampled onto the device's fixed bands), bass boost and virtualizer.
     */
    fun applyProfile(profile: EqProfile) {
        setEnabled(profile.enabled)
        if (!profile.enabled) return
        setPreampGain((profile.preampDb * 100).toInt())      // dB → millibel
        val curve = profile.asGraphicCurve()
        val range = bandLevelRange()
        bandFrequencies().forEachIndexed { i, freqHz ->
            val gainMb = (curve.gainAt(freqHz.toDouble()) * 100).toInt().coerceIn(range.first, range.last)
            setBandLevel(i, gainMb)
        }
        setBassBoost(profile.bassBoost)
        setVirtualizer(profile.virtualizer)
    }

    fun release() {
        equalizer?.release(); equalizer = null
        bassBoost?.release(); bassBoost = null
        virtualizer?.release(); virtualizer = null
        preamp?.release(); preamp = null
        bandCount = 0
    }

    companion object {
        private const val TAG = "AudioEffects"
        private const val PRIORITY = 100
    }
}
