package com.videoguard.app.model

data class VideoItem(
    val id: String,
    val title: String,
    val channelName: String,
    val channelAvatarUrl: String,
    val subscriberCount: String = "5.2M subscribers",
    val thumbnailUrl: String,
    val views: String,
    val uploadTime: String,
    val duration: String,
    val description: String = "",
    val likes: String = "45K",
    val isMusicTrack: Boolean = false,
    val artist: String = "",
    val album: String = "",
    val lyrics: String = "",
    val category: String = "All",
    val mood: String = "Energize",
    val channelId: String = "",
    val originalTitle: String = "",
    val originalArtist: String = "",
    val originalArtworkUrl: String = "",
    val spotifyTrackId: String = "",
    val matchedYouTubeId: String = "",
    val matchedYouTubeTitle: String = ""
)
