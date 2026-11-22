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
 * LastWave-Native Audiophile 15-Band ISO Equalizer & Audio FX Engine.
 * 
 * Features:
 * - 15 ISO 2/3-octave center bands (25 Hz to 16 kHz)
 * - Gain range: -8.0 dB to +8.0 dB (0.1 dB resolution) to preserve headroom
 * - Category classifications: SUB, BASS, LOW-MID, MID, HIGH-MID, PRES, AIR
 * - Dual-tier DSP: Native DynamicsProcessing (15 bands + -1 dBFS peak limiter) on Android 9+ (API 28+),
 *   with logarithmic curve interpolation fallback on legacy Equalizer hardware.
 * - Bound exclusively to the app's audio session (0 for online streaming mix, >0 for offline MediaPlayer).
 * - Real-time zero-latency response.
 */
object EqualizerEngine {
    private const val TAG = "EqualizerEngine"
    private const val PREFS_NAME = "auralix_equalizer_prefs"
    private const val KEY_ENABLED = "eq_enabled"
    private const val KEY_PRESET = "eq_preset"
    private const val KEY_BASS = "eq_bass"
    private const val KEY_VIRTUALIZER = "eq_virtualizer"
    private const val KEY_BAND_PREFIX = "eq_band_15_"

    const val EQ_MAX_GAIN_DB = 8.0f

    // 15 ISO 2/3-octave center frequencies (Hz)
    val EQ_BAND_FREQS_HZ = intArrayOf(25, 40, 63, 100, 160, 250, 400, 630, 1000, 1600, 2500, 4000, 6300, 10000, 16000)

    val FREQUENCY_LABELS = listOf(
        "25 Hz", "40 Hz", "63 Hz", "100 Hz", "160 Hz",
        "250 Hz", "400 Hz", "630 Hz", "1 kHz", "1.6 kHz",
        "2.5 kHz", "4 kHz", "6.3 kHz", "10 kHz", "16 kHz"
    )

    // Curated Audiophile Presets inspired by LastWave-Native
    val PRESETS: LinkedHashMap<String, List<Float>> = linkedMapOf(
        "Studio Master" to listOf(3.0f, 2.5f, 1.5f, 0.5f, -0.5f, -1.0f, -0.5f, 0.0f, 0.5f, 1.0f, 1.5f, 2.0f, 2.5f, 3.0f, 3.5f),
        "Acoustic" to listOf(1.0f, 1.5f, 1.0f, 0.5f, 0.5f, 1.0f, 1.5f, 1.5f, 1.0f, 1.5f, 2.0f, 2.5f, 2.0f, 1.5f, 1.0f),
        "Vocal Clarity" to listOf(-1.5f, -1.0f, -0.5f, 0.0f, 0.5f, 1.0f, 2.0f, 3.0f, 3.5f, 3.0f, 2.0f, 1.5f, 1.0f, 0.5f, 0.0f),
        "Classical" to listOf(2.5f, 2.0f, 1.5f, 1.0f, 0.0f, -0.5f, 0.0f, 0.5f, 1.0f, 1.5f, 2.0f, 2.5f, 2.5f, 2.0f, 1.5f),
        "Jazz" to listOf(1.5f, 2.0f, 1.5f, 1.0f, 0.5f, 0.5f, 0.5f, 1.0f, 1.5f, 1.5f, 1.0f, 1.0f, 1.5f, 2.0f, 2.0f),
        "Rock" to listOf(3.5f, 3.0f, 2.0f, 1.0f, -0.5f, -1.0f, 0.0f, 1.0f, 1.5f, 2.0f, 2.5f, 3.0f, 3.5f, 3.5f, 3.0f),
        "Pop" to listOf(2.0f, 2.5f, 2.0f, 1.0f, 0.0f, 0.5f, 1.5f, 2.0f, 2.0f, 1.5f, 1.0f, 1.5f, 2.0f, 2.5f, 2.0f),
        "Treble Air" to listOf(-1.0f, -1.0f, -0.5f, 0.0f, 0.0f, 0.5f, 0.5f, 1.0f, 1.5f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 6.5f),
        "Deep Bass Clean" to listOf(6.0f, 5.5f, 4.0f, 2.5f, 0.5f, -1.0f, -1.5f, -1.0f, 0.0f, 0.5f, 0.5f, 1.0f, 1.0f, 1.5f, 1.5f),
        "Electronic" to listOf(4.5f, 4.0f, 2.5f, 1.0f, -0.5f, 0.0f, 0.5f, 1.0f, 1.5f, 2.0f, 2.5f, 3.0f, 3.5f, 4.0f, 4.0f),
        "R&B" to listOf(4.0f, 3.5f, 2.5f, 1.5f, 0.5f, -0.5f, 0.0f, 0.5f, 1.0f, 1.5f, 2.0f, 2.5f, 2.5f, 3.0f, 3.0f),
        "Default (Flat)" to List(15) { 0.0f },
        "Custom" to List(15) { 0.0f }
    )

    fun getCategoryForBand(freqHz: Int): String = when {
        freqHz <= 40 -> "SUB"
        freqHz <= 100 -> "BASS"
        freqHz <= 250 -> "LOW-MID"
        freqHz <= 1000 -> "MID"
        freqHz <= 2500 -> "HIGH-MID"
        freqHz <= 6300 -> "PRES"
        else -> "AIR"
    }

    private val _isEnabled = MutableStateFlow(true)
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    private val _currentPreset = MutableStateFlow("Studio Master")
    val currentPreset: StateFlow<String> = _currentPreset.asStateFlow()

    private val _bandLevels = MutableStateFlow(PRESETS["Studio Master"] ?: List(15) { 0.0f })
    val bandLevels: StateFlow<List<Float>> = _bandLevels.asStateFlow()

    private val _bassBoostPercent = MutableStateFlow(25)
    val bassBoostPercent: StateFlow<Int> = _bassBoostPercent.asStateFlow()

    private val _virtualizerPercent = MutableStateFlow(15)
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
        val savedPreset = sp.getString(KEY_PRESET, "Studio Master") ?: "Studio Master"
        val savedBass = sp.getInt(KEY_BASS, 25)
        val savedVirt = sp.getInt(KEY_VIRTUALIZER, 15)

        val defaultPresetList = PRESETS[savedPreset] ?: PRESETS["Studio Master"] ?: List(15) { 0.0f }
        val savedBands = mutableListOf<Float>()
        for (i in 0 until 15) {
            val defVal = defaultPresetList.getOrElse(i) { 0.0f }
            val stored = sp.getFloat("$KEY_BAND_PREFIX$i", defVal)
            savedBands.add(stored.coerceIn(-EQ_MAX_GAIN_DB, EQ_MAX_GAIN_DB))
        }

        _isEnabled.value = savedEnabled
        _currentPreset.value = savedPreset
        _bassBoostPercent.value = savedBass.coerceIn(0, 100)
        _virtualizerPercent.value = savedVirt.coerceIn(0, 100)
        _bandLevels.value = savedBands

        try {
            bindAudioSession(0)
        } catch (e: Exception) {
            Log.w(TAG, "Audio session 0 init: ${e.message}")
        }
    }

    fun bindAudioSession(sessionId: Int = 0) {
        if (sessionId < 0) return
        if (sessionId == activeAudioSessionId && (dynamicsProcessing != null || legacyEqualizer != null)) return

        releaseEffects()
        activeAudioSessionId = sessionId

        try {
            // Priority 1: Modern DynamicsProcessing (Android 9+ / API 28+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                try {
                    val builder = DynamicsProcessing.Config.Builder(
                        DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                        1, // channelCount
                        true, // preEqInUse
                        15, // preEqBandCount
                        false, // mbcInUse
                        0,
                        false, // postEqInUse
                        0,
                        true // limiterInUse
                    )
                    // Audiophile peak limiter: -1.0 dBFS threshold prevents digital distortion
                    val limiter = DynamicsProcessing.Limiter(
                        true, true, 0, 1.0f, 120.0f, 12.0f, -1.0f, 0.0f
                    )
                    builder.setLimiterAllChannelsTo(limiter)
                    val config = builder.build()
                    dynamicsProcessing = DynamicsProcessing(0, sessionId, config).apply {
                        enabled = _isEnabled.value
                    }
                    Log.d(TAG, "Initialized DynamicsProcessing 15-band engine on session $sessionId")
                } catch (e: Exception) {
                    Log.w(TAG, "DynamicsProcessing initialization fallback: ${e.message}")
                }
            }

            // Priority 2: Fallback Legacy Equalizer
            if (dynamicsProcessing == null) {
                legacyEqualizer = Equalizer(0, sessionId).apply {
                    enabled = _isEnabled.value
                }
                Log.d(TAG, "Initialized Legacy Equalizer fallback on session $sessionId")
            }

            // Bass Boost
            bassBoost = BassBoost(0, sessionId).apply {
                if (strengthSupported) {
                    setStrength(((_bassBoostPercent.value / 100f) * 1000).toInt().toShort())
                }
                enabled = _isEnabled.value
            }

            // Virtualizer (3D Surround)
            virtualizer = Virtualizer(0, sessionId).apply {
                if (strengthSupported) {
                    setStrength(((_virtualizerPercent.value / 100f) * 1000).toInt().toShort())
                }
                enabled = _isEnabled.value
            }

            applyCurrentBandLevelsToHardware()
        } catch (e: Exception) {
            Log.w(TAG, "Audio effect binding on session $sessionId: ${e.message}")
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
        if (bandIndex !in 0 until 15) return
        val current = _bandLevels.value.toMutableList()
        current[bandIndex] = levelDb.coerceIn(-EQ_MAX_GAIN_DB, EQ_MAX_GAIN_DB)
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
        setPreset("Default (Flat)")
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

        // Tier 1: DynamicsProcessing 15-band PreEq
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && dynamicsProcessing != null) {
            try {
                for (i in 0 until 15) {
                    val freqHz = EQ_BAND_FREQS_HZ[i].toFloat()
                    val gainDb = levels.getOrElse(i) { 0.0f }.coerceIn(-EQ_MAX_GAIN_DB, EQ_MAX_GAIN_DB)
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
                val interpolatedGainDb = interpolateGain(hwCenterFreq, EQ_BAND_FREQS_HZ.toList(), levels)
                val millibels = (interpolatedGainDb * 100).toInt().coerceIn(minEq.toInt(), maxEq.toInt()).toShort()
                eq.setBandLevel(i.toShort(), millibels)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error applying band to legacy equalizer: ${e.message}")
        }
    }

    /**
     * Logarithmic curve interpolation to project 15-band curve to hardware bands accurately.
     */
    private fun interpolateGain(freqHz: Float, bands: List<Int>, gains: List<Float>): Float {
        if (bands.isEmpty() || gains.isEmpty()) return 0.0f
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
        return 0.0f
    }
}
