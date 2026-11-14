package com.videoguard.app.service

import android.app.*
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.videoguard.app.MainActivity
import com.videoguard.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MediaPlaybackService : Service() {

    companion object {
        const val CHANNEL_ID = "videoguard_playback_v3"
        const val NOTIFICATION_ID = 1001

        const val ACTION_UPDATE = "com.videoguard.app.ACTION_UPDATE"
        const val ACTION_PLAY_PAUSE = "com.videoguard.app.ACTION_PLAY_PAUSE"
        const val ACTION_NEXT = "com.videoguard.app.ACTION_NEXT"
        const val ACTION_PREV = "com.videoguard.app.ACTION_PREV"
        const val ACTION_STOP = "com.videoguard.app.ACTION_STOP"

        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_ARTIST = "extra_artist"
        const val EXTRA_IS_PLAYING = "extra_is_playing"
        const val EXTRA_THUMBNAIL_URL = "extra_thumbnail_url"
        const val EXTRA_DURATION_SEC = "extra_duration_sec"
        const val EXTRA_POSITION_SEC = "extra_position_sec"
        const val EXTRA_PLAYBACK_SPEED = "extra_playback_speed"

        @Volatile
        private var activeInstance: MediaPlaybackService? = null
        private val mainHandler = Handler(Looper.getMainLooper())

        fun syncProgress(
            positionSec: Float,
            durationSec: Float,
            isPlaying: Boolean,
            playbackSpeed: Float
        ) {
            val instance = activeInstance ?: return
            mainHandler.post {
                if (activeInstance !== instance) return@post
                instance.positionSec = positionSec.coerceAtLeast(0f)
                if (durationSec > 0f) {
                    instance.durationSec = durationSec
                }
                instance.isPlaying = isPlaying
                instance.playbackSpeed = playbackSpeed
                instance.updateMediaSessionState(forceMetadata = false)
            }
        }
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private var mediaSession: MediaSessionCompat? = null

    private var currentTitle: String = "AURALIX Audio"
    private var currentArtist: String = "YouTube & YT Music"
    private var isPlaying: Boolean = false
    private var playbackSpeed: Float = 1.0f
    private var currentThumbnailUrl: String? = null
    private var durationSec: Float = 0f
    private var positionSec: Float = 0f
    private var cachedArtwork: Bitmap? = null

    private var lastMetaTitle: String? = null
    private var lastMetaArtist: String? = null
    private var lastMetaDurationMs: Long = -1L
    private var lastMetaArtwork: Bitmap? = null

    private val serviceScope = CoroutineScope(Dispatchers.Main)

    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var telephonyManager: android.telephony.TelephonyManager? = null

    @Suppress("DEPRECATION")
    private val phoneStateListener = object : android.telephony.PhoneStateListener() {
        override fun onCallStateChanged(state: Int, phoneNumber: String?) {
            if (state == android.telephony.TelephonyManager.CALL_STATE_RINGING ||
                state == android.telephony.TelephonyManager.CALL_STATE_OFFHOOK) {
                // Incoming phone call -> auto-pause playback
                PlaybackController.pause()
            }
        }
    }

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                // Another audio app requested permanent playback focus (e.g. Spotify, YouTube app)
                PlaybackController.pause()
            }
        }
    }

    private fun requestAudioFocus() {
        if (audioManager == null) {
            audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        }
        val am = audioManager ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (audioFocusRequest == null) {
                    val attrs = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                    audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                        .setAudioAttributes(attrs)
                        .setOnAudioFocusChangeListener(audioFocusChangeListener)
                        .build()
                }
                audioFocusRequest?.let { am.requestAudioFocus(it) }
            } else {
                @Suppress("DEPRECATION")
                am.requestAudioFocus(
                    audioFocusChangeListener,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun abandonAudioFocus() {
        val am = audioManager ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                am.abandonAudioFocus(audioFocusChangeListener)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private var isNoisyReceiverRegistered = false

    private val becomingNoisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            if (action == AudioManager.ACTION_AUDIO_BECOMING_NOISY ||
                action == BluetoothDevice.ACTION_ACL_DISCONNECTED) {
                PlaybackController.pause()
            }
        }
    }

    private fun registerNoisyReceiver() {
        if (!isNoisyReceiverRegistered) {
            try {
                val filter = IntentFilter().apply {
                    addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
                    addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
                }
                ContextCompat.registerReceiver(
                    this,
                    becomingNoisyReceiver,
                    filter,
                    ContextCompat.RECEIVER_EXPORTED
                )
                isNoisyReceiverRegistered = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun unregisterNoisyReceiver() {
        if (isNoisyReceiverRegistered) {
            try {
                unregisterReceiver(becomingNoisyReceiver)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isNoisyReceiverRegistered = false
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        activeInstance = this
        createNotificationChannel()
        registerNoisyReceiver()

        try {
            telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as? android.telephony.TelephonyManager
            @Suppress("DEPRECATION")
            telephonyManager?.listen(phoneStateListener, android.telephony.PhoneStateListener.LISTEN_CALL_STATE)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "VideoGuard:MediaPlaybackWakeLock"
        ).apply {
            setReferenceCounted(false)
        }

        initMediaSession()
    }

    private fun initMediaSession() {
        mediaSession = MediaSessionCompat(this, "VideoGuardMediaSession").apply {
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                        MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )

            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    PlaybackController.play()
                }

                override fun onPause() {
                    PlaybackController.pause()
                }

                override fun onSkipToNext() {
                    PlaybackController.nextTrack()
                }

                override fun onSkipToPrevious() {
                    PlaybackController.prevTrack()
                }

                override fun onSeekTo(pos: Long) {
                    PlaybackController.seekTo(pos / 1000f)
                }

                override fun onStop() {
                    PlaybackController.stop()
                }
            })

            isActive = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_UPDATE -> {
                val newTitle = intent.getStringExtra(EXTRA_TITLE) ?: currentTitle
                val newArtist = intent.getStringExtra(EXTRA_ARTIST) ?: currentArtist
                val newIsPlaying = intent.getBooleanExtra(EXTRA_IS_PLAYING, true)
                val newThumbUrl = intent.getStringExtra(EXTRA_THUMBNAIL_URL)
                durationSec = intent.getFloatExtra(EXTRA_DURATION_SEC, durationSec).coerceAtLeast(0f)
                positionSec = intent.getFloatExtra(EXTRA_POSITION_SEC, positionSec).coerceAtLeast(0f)
                playbackSpeed = intent.getFloatExtra(EXTRA_PLAYBACK_SPEED, playbackSpeed).coerceAtLeast(0.25f)

                val thumbChanged = newThumbUrl != currentThumbnailUrl
                currentTitle = newTitle
                currentArtist = newArtist
                isPlaying = newIsPlaying

                if (thumbChanged) {
                    cachedArtwork = null
                    currentThumbnailUrl = newThumbUrl
                    if (!newThumbUrl.isNullOrBlank()) {
                        loadArtwork(newThumbUrl)
                    }
                }

                if (isPlaying) {
                    acquireWakeLock()
                    requestAudioFocus()
                } else {
                    releaseWakeLock()
                    abandonAudioFocus()
                }

                updateMediaSessionState(forceMetadata = true)
                showNotification()
            }
            ACTION_PLAY_PAUSE -> {
                PlaybackController.togglePlayPause()
            }
            ACTION_NEXT -> {
                PlaybackController.nextTrack()
            }
            ACTION_PREV -> {
                PlaybackController.prevTrack()
            }
            ACTION_STOP -> {
                releaseWakeLock()
                abandonAudioFocus()
                mediaSession?.isActive = false
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun updateMediaSessionState(forceMetadata: Boolean = false) {
        val session = mediaSession ?: return

        val state = if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        val actions = PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_PLAY_PAUSE or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                PlaybackStateCompat.ACTION_SEEK_TO or
                PlaybackStateCompat.ACTION_STOP

        val posMs = (positionSec * 1000).toLong().coerceAtLeast(0L)
        val durMs = (durationSec * 1000).toLong().coerceAtLeast(0L)
        val speed = if (isPlaying) playbackSpeed else 0.0f

        session.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(actions)
                .setState(state, posMs, speed, SystemClock.elapsedRealtime())
                .build()
        )

        val metadataChanged = forceMetadata ||
                lastMetaTitle != currentTitle ||
                lastMetaArtist != currentArtist ||
                lastMetaDurationMs != durMs ||
                lastMetaArtwork !== cachedArtwork

        if (metadataChanged) {
            lastMetaTitle = currentTitle
            lastMetaArtist = currentArtist
            lastMetaDurationMs = durMs
            lastMetaArtwork = cachedArtwork

            val metadataBuilder = MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, currentTitle)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, currentArtist)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, "AURALIX")
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST, currentArtist)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, durMs)

            cachedArtwork?.let {
                metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, it)
                metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, it)
            }

            session.setMetadata(metadataBuilder.build())
        }
        session.isActive = true
    }

    private fun loadArtwork(url: String) {
        serviceScope.launch(Dispatchers.IO) {
            try {
                val loader = ImageLoader(this@MediaPlaybackService)
                val request = ImageRequest.Builder(this@MediaPlaybackService)
                    .data(url)
                    .allowHardware(false)
                    .build()
                val result = loader.execute(request)
                if (result is SuccessResult) {
                    val bmp = (result.drawable as? BitmapDrawable)?.bitmap
                    if (bmp != null) {
                        withContext(Dispatchers.Main) {
                            cachedArtwork = bmp
                            updateMediaSessionState()
                            showNotification()
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun acquireWakeLock() {
        try {
            if (wakeLock?.isHeld == false) {
                wakeLock?.acquire(3 * 60 * 60 * 1000L /* 3 hours max */)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Media Playback Controls",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background and Lock Screen playback controls for AURALIX"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setSound(null, null)
                enableVibration(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun showNotification() {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Previous Action
        val prevIntent = Intent(this, MediaPlaybackService::class.java).apply { action = ACTION_PREV }
        val prevPending = PendingIntent.getService(
            this, 1, prevIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Play / Pause Action
        val playPauseIntent = Intent(this, MediaPlaybackService::class.java).apply { action = ACTION_PLAY_PAUSE }
        val playPausePending = PendingIntent.getService(
            this, 2, playPauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Next Action
        val nextIntent = Intent(this, MediaPlaybackService::class.java).apply { action = ACTION_NEXT }
        val nextPending = PendingIntent.getService(
            this, 3, nextIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Stop Action
        val stopIntent = Intent(this, MediaPlaybackService::class.java).apply { action = ACTION_STOP }
        val stopPending = PendingIntent.getService(
            this, 4, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseIcon = if (isPlaying) {
            android.R.drawable.ic_media_pause
        } else {
            android.R.drawable.ic_media_play
        }
        val playPauseTitle = if (isPlaying) "Pause" else "Play"

        val mediaStyle = androidx.media.app.NotificationCompat.MediaStyle()
            .setMediaSession(mediaSession?.sessionToken)
            .setShowActionsInCompactView(0, 1, 2)
            .setShowCancelButton(true)
            .setCancelButtonIntent(stopPending)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(currentTitle)
            .setContentText(currentArtist)
            .setSubText("AURALIX")
            .setContentIntent(openAppPendingIntent)
            .setOngoing(isPlaying)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(android.R.drawable.ic_media_previous, "Previous", prevPending)
            .addAction(playPauseIcon, playPauseTitle, playPausePending)
            .addAction(android.R.drawable.ic_media_next, "Next", nextPending)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Close", stopPending)
            .setStyle(mediaStyle)

        cachedArtwork?.let {
            builder.setLargeIcon(it)
        }

        val notification = builder.build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onDestroy() {
        if (activeInstance === this) {
            activeInstance = null
        }
        unregisterNoisyReceiver()
        try {
            mediaSession?.apply {
                isActive = false
                release()
            }
            mediaSession = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            @Suppress("DEPRECATION")
            telephonyManager?.listen(phoneStateListener, android.telephony.PhoneStateListener.LISTEN_NONE)
        } catch (e: Exception) {}
        abandonAudioFocus()
        releaseWakeLock()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
