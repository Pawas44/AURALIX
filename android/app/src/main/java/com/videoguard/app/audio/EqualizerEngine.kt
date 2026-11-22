package com.videoguard.app.audio

import android.content.Context
import android.content.SharedPreferences
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tunex-inspired Equalizer & Audio FX Engine.
 * Modifies ONLY this application's sound by binding to the app's specific audioSessionId.
 * Never affects other applications or system audio globally.
 */
object EqualizerEngine {
    private const val TAG = "EqualizerEngine"
    private const val PREFS_NAME = "auralix_equalizer_prefs"
    private const val KEY_ENABLED = "eq_enabled"
    private const val KEY_PRESET = "eq_preset"
    private const val KEY_BASS = "eq_bass"
    private const val KEY_VIRTUALIZER = "eq_virtualizer"
    private const val KEY_BAND_PREFIX = "eq_band_"

    // Preset definitions: 5 bands in dB (-12 dB to +12 dB)
    val PRESETS = linkedMapOf(
        "Flat" to listOf(0, 0, 0, 0, 0),
        "Bass Boost" to listOf(7, 5, 1, 0, -1),
        "Rock" to listOf(5, 3, -1, 3, 5),
        "Pop" to listOf(-1, 2, 5, 2, -1),
        "Hip Hop" to listOf(6, 4, 0, 2, 3),
        "Electronic" to listOf(5, 4, -1, 2, 4),
        "Jazz" to listOf(4, 2, -2, 2, 4),
        "Classical" to listOf(5, 3, -2, 3, 4),
        "Vocal Boost" to listOf(-2, -1, 5, 3, 0),
        "Custom" to listOf(0, 0, 0, 0, 0)
    )

    val FREQUENCY_LABELS = listOf("60 Hz", "230 Hz", "910 Hz", "3.6 kHz", "14 kHz")

    private val _isEnabled = MutableStateFlow(true)
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    private val _currentPreset = MutableStateFlow("Flat")
    val currentPreset: StateFlow<String> = _currentPreset.asStateFlow()

    private val _bandLevels = MutableStateFlow(listOf(0, 0, 0, 0, 0))
    val bandLevels: StateFlow<List<Int>> = _bandLevels.asStateFlow()

    private val _bassBoostPercent = MutableStateFlow(25)
    val bassBoostPercent: StateFlow<Int> = _bassBoostPercent.asStateFlow()

    private val _virtualizerPercent = MutableStateFlow(15)
    val virtualizerPercent: StateFlow<Int> = _virtualizerPercent.asStateFlow()

    private var activeAudioSessionId: Int = 0
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var prefs: SharedPreferences? = null
    private var isInitialized = false

    fun init(context: Context) {
        if (isInitialized) return
        isInitialized = true

        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = sp

        val savedEnabled = sp.getBoolean(KEY_ENABLED, true)
        val savedPreset = sp.getString(KEY_PRESET, "Flat") ?: "Flat"
        val savedBass = sp.getInt(KEY_BASS, 25)
        val savedVirt = sp.getInt(KEY_VIRTUALIZER, 15)

        val savedBands = mutableListOf<Int>()
        for (i in 0 until 5) {
            val defaultVal = PRESETS[savedPreset]?.getOrNull(i) ?: 0
            savedBands.add(sp.getInt("$KEY_BAND_PREFIX$i", defaultVal))
        }

        _isEnabled.value = savedEnabled
        _currentPreset.value = savedPreset
        _bassBoostPercent.value = savedBass.coerceIn(0, 100)
        _virtualizerPercent.value = savedVirt.coerceIn(0, 100)
        _bandLevels.value = savedBands
    }

    /**
     * Binds audio effects specifically to the application's audio session ID.
     * Ensures ONLY this app's sound is modified.
     */
    fun bindAudioSession(sessionId: Int) {
        if (sessionId <= 0) return
        if (sessionId == activeAudioSessionId && equalizer != null) return

        releaseEffects()
        activeAudioSessionId = sessionId

        try {
            // Equalizer attached strictly to this app's session ID (priority 0)
            equalizer = Equalizer(0, sessionId).apply {
                enabled = _isEnabled.value
            }

            // Bass Boost attached strictly to this app's session ID
            bassBoost = BassBoost(0, sessionId).apply {
                if (strengthSupported) {
                    setStrength(((_bassBoostPercent.value / 100f) * 1000).toInt().toShort())
                }
                enabled = _isEnabled.value
            }

            // Virtualizer (3D Surround) attached strictly to this app's session ID
            virtualizer = Virtualizer(0, sessionId).apply {
                if (strengthSupported) {
                    setStrength(((_virtualizerPercent.value / 100f) * 1000).toInt().toShort())
                }
                enabled = _isEnabled.value
            }

            applyCurrentBandLevelsToHardware()
            Log.d(TAG, "Successfully bound Equalizer & FX to app session ID $sessionId")
        } catch (e: Exception) {
            Log.w(TAG, "Audio effect initialization on session $sessionId: ${e.message}")
        }
    }

    fun unbindAudioSession() {
        releaseEffects()
        activeAudioSessionId = 0
    }

    private fun releaseEffects() {
        try { equalizer?.release() } catch (ignored: Exception) {}
        try { bassBoost?.release() } catch (ignored: Exception) {}
        try { virtualizer?.release() } catch (ignored: Exception) {}
        equalizer = null
        bassBoost = null
        virtualizer = null
    }

    fun toggleEnabled() {
        setEnabled(!_isEnabled.value)
    }

    fun setEnabled(enabled: Boolean) {
        _isEnabled.value = enabled
        prefs?.edit()?.putBoolean(KEY_ENABLED, enabled)?.apply()

        try {
            equalizer?.enabled = enabled
            bassBoost?.enabled = enabled
            virtualizer?.enabled = enabled
        } catch (e: Exception) {}
    }

    fun setPreset(name: String) {
        val presetValues = PRESETS[name] ?: return
        _currentPreset.value = name
        _bandLevels.value = presetValues
        applyCurrentBandLevelsToHardware()

        prefs?.edit()?.apply {
            putString(KEY_PRESET, name)
            for (i in presetValues.indices) {
                putInt("$KEY_BAND_PREFIX$i", presetValues[i])
            }
            apply()
        }
    }

    fun setBandLevel(bandIndex: Int, levelDb: Int) {
        if (bandIndex !in 0 until 5) return
        val current = _bandLevels.value.toMutableList()
        current[bandIndex] = levelDb.coerceIn(-12, 12)
        _bandLevels.value = current
        _currentPreset.value = "Custom"

        applyCurrentBandLevelsToHardware()

        prefs?.edit()?.apply {
            putString(KEY_PRESET, "Custom")
            putInt("$KEY_BAND_PREFIX$bandIndex", current[bandIndex])
            apply()
        }
    }

    fun setBassBoost(percent: Int) {
        val clamped = percent.coerceIn(0, 100)
        _bassBoostPercent.value = clamped
        try {
            bassBoost?.let {
                if (it.strengthSupported) {
                    it.setStrength(((clamped / 100f) * 1000).toInt().toShort())
                }
            }
        } catch (e: Exception) {}

        prefs?.edit()?.putInt(KEY_BASS, clamped)?.apply()
    }

    fun setVirtualizer(percent: Int) {
        val clamped = percent.coerceIn(0, 100)
        _virtualizerPercent.value = clamped
        try {
            virtualizer?.let {
                if (it.strengthSupported) {
                    it.setStrength(((clamped / 100f) * 1000).toInt().toShort())
                }
            }
        } catch (e: Exception) {}

        prefs?.edit()?.putInt(KEY_VIRTUALIZER, clamped)?.apply()
    }

    private fun applyCurrentBandLevelsToHardware() {
        val eq = equalizer ?: return
        try {
            val numBands = eq.numberOfBands.toInt()
            val minEq = eq.bandLevelRange[0] // e.g. -1500 or -1000 mB
            val maxEq = eq.bandLevelRange[1] // e.g. +1500 or +1000 mB

            val levels = _bandLevels.value
            for (i in 0 until minOf(5, numBands)) {
                val db = levels.getOrElse(i) { 0 }
                val millibels = (db * 100).coerceIn(minEq.toInt(), maxEq.toInt()).toShort()
                eq.setBandLevel(i.toShort(), millibels)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error applying band levels to hardware: ${e.message}")
        }
    }
}
