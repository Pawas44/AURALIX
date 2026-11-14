package com.videoguard.app.recommendation

import com.videoguard.app.model.VideoItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

data class ActiveSession(
    val track: VideoItem,
    val startTimeMs: Long,
    var lastProgressSec: Float = 0f,
    var durationSec: Float = 0f,
    var hasCompleted: Boolean = false
)

object ListeningHistoryManager {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var activeSession: ActiveSession? = null
    private var lastPlayedTrackId: String? = null

    /**
     * Dispatched when a song starts playing.
     */
    fun onTrackStarted(track: VideoItem, initialDuration: Float = 0f) {
        scope.launch {
            // Check if previous session ended without explicit skip/completion
            activeSession?.let { prev ->
                if (!prev.hasCompleted && prev.track.id != track.id) {
                    evaluateSessionEnd(prev, isNaturalEnd = false)
                }
            }

            val isReplay = lastPlayedTrackId == track.id
            lastPlayedTrackId = track.id

            activeSession = ActiveSession(
                track = track,
                startTimeMs = System.currentTimeMillis(),
                durationSec = initialDuration
            )

            UserTasteProfile.get().recordTrackStarted(track)
            if (isReplay) {
                UserTasteProfile.get().recordTrackCompleted(track, isReplay = true)
            }
        }
    }

    /**
     * Periodic progress updates from PlaybackController.
     */
    fun onProgressUpdate(currentSec: Float, durationSec: Float) {
        val session = activeSession ?: return
        session.lastProgressSec = currentSec
        if (durationSec > 0f) {
            session.durationSec = durationSec
        }

        // Automatic completion detection: listened to >= 85% of duration
        if (!session.hasCompleted && session.durationSec > 10f) {
            val ratio = session.lastProgressSec / session.durationSec
            if (ratio >= 0.85f || (session.durationSec - session.lastProgressSec <= 4.0f)) {
                session.hasCompleted = true
                scope.launch {
                    UserTasteProfile.get().recordTrackCompleted(session.track, isReplay = false)
                }
            }
        }
    }

    /**
     * Dispatched when song finishes naturally.
     */
    fun onTrackCompleted(track: VideoItem) {
        scope.launch {
            val session = activeSession
            if (session != null && session.track.id == track.id) {
                if (!session.hasCompleted) {
                    session.hasCompleted = true
                    UserTasteProfile.get().recordTrackCompleted(track, isReplay = false)
                }
            } else {
                UserTasteProfile.get().recordTrackCompleted(track, isReplay = false)
            }
        }
    }

    /**
     * Dispatched when user skips to next/prev track.
     */
    fun onTrackSkipped(track: VideoItem) {
        scope.launch {
            val session = activeSession
            if (session != null && session.track.id == track.id) {
                if (!session.hasCompleted) {
                    UserTasteProfile.get().recordTrackSkipped(
                        track = track,
                        listenedSec = session.lastProgressSec,
                        totalSec = session.durationSec
                    )
                }
            } else {
                UserTasteProfile.get().recordTrackSkipped(track, listenedSec = 10f, totalSec = 180f)
            }
            activeSession = null
        }
    }

    /**
     * Dispatched when user likes a track.
     */
    fun onTrackLiked(track: VideoItem) {
        scope.launch {
            UserTasteProfile.get().recordTrackLiked(track)
        }
    }

    /**
     * Dispatched when user clicks "Not interested" or dislikes.
     */
    fun onTrackNotInterested(track: VideoItem) {
        scope.launch {
            UserTasteProfile.get().recordNotInterested(track)
        }
    }

    /**
     * Dispatched when user adds track to playlist.
     */
    fun onTrackAddedToPlaylist(track: VideoItem) {
        scope.launch {
            UserTasteProfile.get().recordTrackAddedToPlaylist(track)
        }
    }

    private fun evaluateSessionEnd(session: ActiveSession, isNaturalEnd: Boolean) {
        if (session.hasCompleted) return
        val ratio = if (session.durationSec > 0f) session.lastProgressSec / session.durationSec else 0f
        if (isNaturalEnd || ratio >= 0.85f) {
            session.hasCompleted = true
            UserTasteProfile.get().recordTrackCompleted(session.track)
        } else {
            UserTasteProfile.get().recordTrackSkipped(session.track, session.lastProgressSec, session.durationSec)
        }
    }
}
