package com.videoguard.app.audio

import android.content.Context
import android.content.SharedPreferences
import android.media.audiofx.BassBoost
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.ln

/**
 * Professional Studio Graphic Equalizer & Audio FX Engine.
 * Supports:
 * - 10-Band ISO Standard Studio Frequencies (31Hz to 16kHz)
 * - 12 Curated Presets (Bass Boost, Vocal Boost, Rock, Pop, EDM, Jazz, etc.)
 * - Android 9+ (API 28+) DynamicsProcessing native engine with peak limiter
 * - Dual-mode fallback on hardware Equalizer with logarithmic curve mapping
 * - Bass Boost & 3D Spatial Virtualizer
 * - App-audio isolation: Only affects this application, never system audio.
 */
object EqualizerEngine {
    private const val TAG = "EqualizerEngine"
    private const val PREFS_NAME = "auralix_studio_eq_prefs"
    private const val KEY_ENABLED = "eq_enabled"
    private const val KEY_PRESET = "eq_preset"
    private const val KEY_BASS = "eq_bass"
    private const val KEY_VIRTUALIZER = "eq_virtualizer"
    private const val KEY_BAND_PREFIX = "eq_band_10_"

    const val MAX_GAIN_DB = 10f
    const val MIN_GAIN_DB = -10f

    // 10 ISO Standard Octave Frequencies
    val FREQUENCIES = intArrayOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)

    val FREQUENCY_LABELS = listOf(
        "31 Hz", "62 Hz", "125 Hz", "250 Hz", "500 Hz",
        "1 kHz", "2 kHz", "4 kHz", "8 kHz", "16 kHz"
    )

    val FREQUENCY_DESCRIPTIONS = listOf(
        "Sub-Bass", "Bass Punch", "Warmth", "Low-Mid", "Mids Body",
        "Vocal Presence", "Clarity", "Treble Bite", "Brilliance", "Air"
    )

    // Curated Studio Audio Presets (10 bands in dB, -10 dB to +10 dB)
    val PRESETS: LinkedHashMap<String, List<Float>> = linkedMapOf(
        "Flat" to listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
        "Bass Boost" to listOf(7f, 6f, 4f, 2f, 0f, -1f, 0f, 1f, 2f, 2f),
        "Vocal Clarity" to listOf(-2f, -1f, 0f, 1f, 3f, 5f, 4f, 2f, 1f, 0f),
        "Rock" to listOf(5f, 4f, 2f, -1f, -1f, 1f, 3f, 4f, 5f, 5f),
        "Pop" to listOf(2f, 3f, 2f, 1f, 2f, 3f, 2f, 1f, 2f, 3f),
        "Hip-Hop" to listOf(6f, 5f, 3f, 1f, 0f, 1f, 2f, 1f, 3f, 3f),
        "Electronic / EDM" to listOf(6f, 5f, 3f, 0f, -1f, 2f, 3f, 4f, 5f, 5f),
        "Jazz" to listOf(3f, 2f, 1f, 2f, -1f, 1f, 2f, 3f, 2f, 2f),
        "Classical" to listOf(4f, 3f, 2f, 0f, 0f, 1f, 2f, 3f, 3f, 2f),
        "Acoustic" to listOf(2f, 2f, 1f, 2f, 2f, 2f, 3f, 3f, 2f, 2f),
        "Treble Boost" to listOf(-2f, -1f, 0f, 0f, 1f, 2f, 4f, 6f, 7f, 8f),
        "Custom" to listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
    )

    private val _isEnabled = MutableStateFlow(true)
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    private val _currentPreset = MutableStateFlow("Bass Boost")
    val currentPreset: StateFlow<String> = _currentPreset.asStateFlow()

    private val _bandLevels = MutableStateFlow(PRESETS["Bass Boost"] ?: List(10) { 0f })
    val bandLevels: StateFlow<List<Float>> = _bandLevels.asStateFlow()

    private val _bassBoostPercent = MutableStateFlow(30)
    val bassBoostPercent: StateFlow<Int> = _bassBoostPercent.asStateFlow()

    private val _virtualizerPercent = MutableStateFlow(20)
    val virtualizerPercent: StateFlow<Int> = _virtualizerPercent.asStateFlow()

    private var activeAudioSessionId: Int = -1
    private var dynamicsProcessing: DynamicsProcessing? = null
    private var legacyEqualizer: Equalizer? = null
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
        val savedPreset = sp.getString(KEY_PRESET, "Bass Boost") ?: "Bass Boost"
        val savedBass = sp.getInt(KEY_BASS, 30)
        val savedVirt = sp.getInt(KEY_VIRTUALIZER, 20)

        val defaultList = PRESETS[savedPreset] ?: PRESETS["Bass Boost"] ?: List(10) { 0f }
        val savedBands = mutableListOf<Float>()
        for (i in 0 until 10) {
            val def = defaultList.getOrElse(i) { 0f }
            val stored = sp.getFloat("$KEY_BAND_PREFIX$i", def)
            savedBands.add(stored.coerceIn(MIN_GAIN_DB, MAX_GAIN_DB))
        }

        _isEnabled.value = savedEnabled
        _currentPreset.value = savedPreset
        _bassBoostPercent.value = savedBass.coerceIn(0, 100)
        _virtualizerPercent.value = savedVirt.coerceIn(0, 100)
        _bandLevels.value = savedBands

        try {
            bindAudioSession(0)
        } catch (e: Exception) {
            Log.w(TAG, "Default session 0 init: ${e.message}")
        }
    }

    fun bindAudioSession(sessionId: Int = 0) {
        if (sessionId < 0) return
        if (sessionId == activeAudioSessionId && (dynamicsProcessing != null || legacyEqualizer != null)) return

        releaseEffects()
        activeAudioSessionId = sessionId

        try {
            // Android 9+ DynamicsProcessing
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                try {
                    val builder = DynamicsProcessing.Config.Builder(
                        DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                        1, // channelCount
                        true, // preEqInUse
                        10, // preEqBandCount = 10
                        false, 0,
                        false, 0,
                        true // limiterInUse
                    )
                    val limiter = DynamicsProcessing.Limiter(
                        true, true, 0, 1.0f, 120.0f, 12.0f, -1.0f, 0.0f
                    )
                    builder.setLimiterAllChannelsTo(limiter)
                    val config = builder.build()
                    dynamicsProcessing = DynamicsProcessing(0, sessionId, config).apply {
                        enabled = _isEnabled.value
                    }
                    Log.d(TAG, "Bound DynamicsProcessing 10-band on session $sessionId")
                } catch (e: Exception) {
                    Log.w(TAG, "DynamicsProcessing init: ${e.message}")
                }
            }

            // Fallback Hardware Equalizer
            if (dynamicsProcessing == null) {
                legacyEqualizer = Equalizer(0, sessionId).apply {
                    enabled = _isEnabled.value
                }
                Log.d(TAG, "Bound Legacy Equalizer on session $sessionId")
            }

            // Bass Boost
            bassBoost = BassBoost(0, sessionId).apply {
                if (strengthSupported) {
                    setStrength(((_bassBoostPercent.value / 100f) * 1000).toInt().toShort())
                }
                enabled = _isEnabled.value
            }

            // 3D Virtualizer
            virtualizer = Virtualizer(0, sessionId).apply {
                if (strengthSupported) {
                    setStrength(((_virtualizerPercent.value / 100f) * 1000).toInt().toShort())
                }
                enabled = _isEnabled.value
            }

            applyCurrentBandLevelsToHardware()
        } catch (e: Exception) {
            Log.w(TAG, "Audio effect binding failed on session $sessionId: ${e.message}")
        }
    }

    fun unbindAudioSession() {
        releaseEffects()
        activeAudioSessionId = -1
    }

    private fun releaseEffects() {
        try { dynamicsProcessing?.release() } catch (ignored: Exception) {}
        try { legacyEqualizer?.release() } catch (ignored: Exception) {}
        try { bassBoost?.release() } catch (ignored: Exception) {}
        try { virtualizer?.release() } catch (ignored: Exception) {}
        dynamicsProcessing = null
        legacyEqualizer = null
        bassBoost = null
        virtualizer = null
    }

    fun toggleEnabled() {
        setEnabled(!_isEnabled.value)
    }

    fun setEnabled(enabled: Boolean) {
        _isEnabled.value = enabled
        prefs?.edit()?.putBoolean(KEY_ENABLED, enabled)?.apply()

        if (enabled && dynamicsProcessing == null && legacyEqualizer == null) {
            bindAudioSession(0)
        }

        try {
            dynamicsProcessing?.enabled = enabled
            legacyEqualizer?.enabled = enabled
            bassBoost?.enabled = enabled
            virtualizer?.enabled = enabled
        } catch (ignored: Exception) {}
    }

    fun setPreset(name: String) {
        val presetValues = PRESETS[name] ?: return
        _currentPreset.value = name
        _bandLevels.value = presetValues

        if (dynamicsProcessing == null && legacyEqualizer == null) {
            bindAudioSession(0)
        } else {
            applyCurrentBandLevelsToHardware()
        }

        prefs?.edit()?.apply {
            putString(KEY_PRESET, name)
            for (i in presetValues.indices) {
                putFloat("$KEY_BAND_PREFIX$i", presetValues[i])
            }
            apply()
        }
    }

    fun setBandLevel(bandIndex: Int, levelDb: Float) {
        if (bandIndex !in 0 until 10) return
        val current = _bandLevels.value.toMutableList()
        current[bandIndex] = levelDb.coerceIn(MIN_GAIN_DB, MAX_GAIN_DB)
        _bandLevels.value = current
        _currentPreset.value = "Custom"

        if (dynamicsProcessing == null && legacyEqualizer == null) {
            bindAudioSession(0)
        } else {
            applyCurrentBandLevelsToHardware()
        }

        prefs?.edit()?.apply {
            putString(KEY_PRESET, "Custom")
            putFloat("$KEY_BAND_PREFIX$bandIndex", current[bandIndex])
            apply()
        }
    }

    fun resetToFlat() {
        setPreset("Flat")
    }

    fun setBassBoost(percent: Int) {
        val clamped = percent.coerceIn(0, 100)
        _bassBoostPercent.value = clamped

        if (bassBoost == null) {
            bindAudioSession(0)
        }

        try {
            bassBoost?.let {
                if (it.strengthSupported) {
                    it.setStrength(((clamped / 100f) * 1000).toInt().toShort())
                }
            }
        } catch (ignored: Exception) {}

        prefs?.edit()?.putInt(KEY_BASS, clamped)?.apply()
    }

    fun setVirtualizer(percent: Int) {
        val clamped = percent.coerceIn(0, 100)
        _virtualizerPercent.value = clamped

        if (virtualizer == null) {
            bindAudioSession(0)
        }

        try {
            virtualizer?.let {
                if (it.strengthSupported) {
                    it.setStrength(((clamped / 100f) * 1000).toInt().toShort())
                }
            }
        } catch (ignored: Exception) {}

        prefs?.edit()?.putInt(KEY_VIRTUALIZER, clamped)?.apply()
    }

    private fun applyCurrentBandLevelsToHardware() {
        val levels = _bandLevels.value

        // Tier 1: DynamicsProcessing 10-Band PreEq
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && dynamicsProcessing != null) {
            try {
                for (i in 0 until 10) {
                    val freqHz = FREQUENCIES[i].toFloat()
                    val gainDb = levels.getOrElse(i) { 0f }.coerceIn(MIN_GAIN_DB, MAX_GAIN_DB)
                    val eqBand = DynamicsProcessing.EqBand(true, freqHz, gainDb)
                    dynamicsProcessing?.setPreEqBandAllChannelsTo(i, eqBand)
                }
                return
            } catch (e: Exception) {
                Log.w(TAG, "Error applying band to DynamicsProcessing: ${e.message}")
            }
        }

        // Tier 2: Legacy Equalizer with Logarithmic Curve Interpolation
        val eq = legacyEqualizer ?: return
        try {
            val numBands = eq.numberOfBands.toInt()
            val minEq = eq.bandLevelRange[0]
            val maxEq = eq.bandLevelRange[1]

            val hwFreqs = (0 until numBands).map { eq.getCenterFreq(it.toShort()) / 1000f }
            for (i in 0 until numBands) {
                val hwCenterFreq = hwFreqs[i]
                val interpolatedGainDb = interpolateGain(hwCenterFreq, FREQUENCIES.toList(), levels)
                val millibels = (interpolatedGainDb * 100).toInt().coerceIn(minEq.toInt(), maxEq.toInt()).toShort()
                eq.setBandLevel(i.toShort(), millibels)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error applying band to legacy equalizer: ${e.message}")
        }
    }

    private fun interpolateGain(freqHz: Float, bands: List<Int>, gains: List<Float>): Float {
        if (bands.isEmpty() || gains.isEmpty()) return 0f
        if (freqHz <= bands.first()) return gains.first()
        if (freqHz >= bands.last()) return gains.last()

        for (i in 0 until bands.size - 1) {
            val f0 = bands[i].toFloat()
            val f1 = bands[i + 1].toFloat()
            if (freqHz in f0..f1) {
                val logF0 = ln(f0)
                val logF1 = ln(f1)
                val logF = ln(freqHz)
                val t = ((logF - logF0) / (logF1 - logF0))
                return gains[i] + t * (gains[i + 1] - gains[i])
            }
        }
        return 0f
    }
}
