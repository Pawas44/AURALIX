package com.videoguard.app.data

import android.content.Context
import android.content.SharedPreferences
import com.videoguard.app.service.PlaybackController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AppSettingsRepository {
    private const val PREFS_NAME = "auralix_app_settings"
    private const val KEY_AUTOPLAY = "autoplay"
    private const val KEY_DONT_REPEAT = "dont_repeat_suggestions"
    private const val KEY_STREAMING_QUALITY = "streaming_quality"
    private const val KEY_DOWNLOAD_QUALITY = "download_quality"
    private const val KEY_VOLUME_LEVELING = "volume_leveling"

    private var prefs: SharedPreferences? = null

    val isAutoplay: StateFlow<Boolean> get() = PlaybackController.isAutoplayEnabled

    private val _dontRepeatSuggestions = MutableStateFlow(true)
    val dontRepeatSuggestions: StateFlow<Boolean> = _dontRepeatSuggestions.asStateFlow()

    private val _streamingQuality = MutableStateFlow("Lossless")
    val streamingQuality: StateFlow<String> = _streamingQuality.asStateFlow()

    private val _downloadQuality = MutableStateFlow("High")
    val downloadQuality: StateFlow<String> = _downloadQuality.asStateFlow()

    private val _volumeLeveling = MutableStateFlow("Normal")
    val volumeLeveling: StateFlow<String> = _volumeLeveling.asStateFlow()

    fun init(context: Context) {
        val sp = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = sp
        val ap = sp.getBoolean(KEY_AUTOPLAY, true)
        PlaybackController.setAutoplay(ap)

        _dontRepeatSuggestions.value = sp.getBoolean(KEY_DONT_REPEAT, true)
        _streamingQuality.value = sp.getString(KEY_STREAMING_QUALITY, "Lossless") ?: "Lossless"
        _downloadQuality.value = sp.getString(KEY_DOWNLOAD_QUALITY, "High") ?: "High"
        _volumeLeveling.value = sp.getString(KEY_VOLUME_LEVELING, "Normal") ?: "Normal"
        applyStreamingQuality(_streamingQuality.value)
        applyVolumeLeveling(_volumeLeveling.value)
    }

    fun setAutoplay(enabled: Boolean) {
        PlaybackController.setAutoplay(enabled)
        prefs?.edit()?.putBoolean(KEY_AUTOPLAY, enabled)?.apply()
    }

    fun setDontRepeatSuggestions(enabled: Boolean) {
        _dontRepeatSuggestions.value = enabled
        prefs?.edit()?.putBoolean(KEY_DONT_REPEAT, enabled)?.apply()
    }

    fun setStreamingQuality(quality: String) {
        _streamingQuality.value = quality
        prefs?.edit()?.putString(KEY_STREAMING_QUALITY, quality)?.apply()
        applyStreamingQuality(quality)
    }

    private fun applyStreamingQuality(quality: String) {
        when (quality.lowercase()) {
            "low" -> PlaybackController.setPlaybackQuality("360p")
            "medium" -> PlaybackController.setPlaybackQuality("480p")
            "high" -> PlaybackController.setPlaybackQuality("720p")
            else -> PlaybackController.setPlaybackQuality("Auto (1080p)")
        }
    }

    fun setDownloadQuality(quality: String) {
        _downloadQuality.value = quality
        prefs?.edit()?.putString(KEY_DOWNLOAD_QUALITY, quality)?.apply()
    }

    fun setVolumeLeveling(level: String) {
        _volumeLeveling.value = level
        prefs?.edit()?.putString(KEY_VOLUME_LEVELING, level)?.apply()
        applyVolumeLeveling(level)
    }

    private fun applyVolumeLeveling(level: String) {
        when (level.lowercase()) {
            "off" -> PlaybackController.setVolumeLevel(1.0f)
            "quiet" -> PlaybackController.setVolumeLevel(0.72f)
            "normal" -> PlaybackController.setVolumeLevel(0.88f)
            "loud" -> PlaybackController.setVolumeLevel(1.0f)
        }
    }
}
