package com.videoguard.app.recommendation

import com.videoguard.app.model.VideoItem

data class DailyMix(
    val id: String,
    val title: String,
    val subtitle: String,
    val description: String,
    val tracks: List<VideoItem>,
    val coverUrl: String? = null
)

object DailyMixGenerator {

    /**
     * Generates up to 5 personalized Daily Mixes based on the user's actual listening history.
     */
    fun generateDailyMixes(candidatePool: List<VideoItem>): List<DailyMix> {
        val validPool = candidatePool.filter {
            (it.id.length == 11 || it.id.startsWith("spotify_")) && !it.id.startsWith("search_")
        }
        val profile = UserTasteProfile.get()
        val topArtists = profile.getTopArtists(limit = 8).map { it.artistName }
        val topGenres = profile.getTopGenres(limit = 4).map { it.genre }

        val mixes = mutableListOf<DailyMix>()

        // MIX 1: Your Heavy Rotation / Favorite Artists
        val mix1Artists = topArtists.take(4)
        val mix1Tracks = validPool.filter { track ->
            val art = profile.extractArtist(track)
            mix1Artists.any { it.equals(art, ignoreCase = true) }
        }.distinctBy { it.id }.take(20)

        if (mix1Tracks.isNotEmpty() || topArtists.isNotEmpty()) {
            val artistSummary = if (mix1Artists.isNotEmpty()) mix1Artists.joinToString(", ") else "Your favorites"
            mixes.add(
                DailyMix(
                    id = "daily_mix_1",
                    title = "Daily Mix 1",
                    subtitle = artistSummary,
                    description = "Songs from $artistSummary and similar artists you love.",
                    tracks = if (mix1Tracks.size >= 8) mix1Tracks else (mix1Tracks + validPool.take(15)).distinctBy { it.id }.take(20),
                    coverUrl = mix1Tracks.firstOrNull()?.thumbnailUrl
                )
            )
        }

        // MIX 2: Top Genre #1
        val genre1 = topGenres.getOrNull(0) ?: "Pop"
        val mix2Tracks = validPool.filter {
            profile.extractGenre(it).contains(genre1, ignoreCase = true)
        }.distinctBy { it.id }.take(20)

        if (mix2Tracks.isNotEmpty()) {
            val keyArtists = mix2Tracks.map { profile.extractArtist(it) }.filter { it.isNotBlank() }.distinct().take(3).joinToString(", ")
            mixes.add(
                DailyMix(
                    id = "daily_mix_2",
                    title = "Daily Mix 2",
                    subtitle = "$genre1 Mix",
                    description = "The best of $genre1 featuring $keyArtists.",
                    tracks = mix2Tracks,
                    coverUrl = mix2Tracks.firstOrNull()?.thumbnailUrl
                )
            )
        }

        // MIX 3: Top Genre #2
        val genre2 = topGenres.getOrNull(1) ?: "Electronic / Dance"
        val mix3Tracks = validPool.filter {
            profile.extractGenre(it).contains(genre2, ignoreCase = true)
        }.distinctBy { it.id }.take(20)

        if (mix3Tracks.isNotEmpty()) {
            val keyArtists = mix3Tracks.map { profile.extractArtist(it) }.filter { it.isNotBlank() }.distinct().take(3).joinToString(", ")
            mixes.add(
                DailyMix(
                    id = "daily_mix_3",
                    title = "Daily Mix 3",
                    subtitle = "$genre2 Mix",
                    description = "Energizing $genre2 tracks with $keyArtists.",
                    tracks = mix3Tracks,
                    coverUrl = mix3Tracks.firstOrNull()?.thumbnailUrl
                )
            )
        }

        // MIX 4: Chill / Relax / Focus (Mood-based)
        val mix4Tracks = validPool.filter {
            it.mood.equals("Relax", ignoreCase = true) ||
            it.mood.equals("Focus", ignoreCase = true) ||
            profile.extractGenre(it).contains("Lo-Fi", ignoreCase = true) ||
            profile.extractGenre(it).contains("Acoustic", ignoreCase = true)
        }.distinctBy { it.id }.take(20)

        if (mix4Tracks.isNotEmpty()) {
            mixes.add(
                DailyMix(
                    id = "daily_mix_4",
                    title = "Daily Mix 4",
                    subtitle = "Chill & Relax",
                    description = "Mellow beats, acoustics, and relaxing sounds to unwind.",
                    tracks = mix4Tracks,
                    coverUrl = mix4Tracks.firstOrNull()?.thumbnailUrl
                )
            )
        }

        // MIX 5: Fresh Finds / Discoveries
        val familiarArtistSet = topArtists.map { it.lowercase() }.toSet()
        val mix5Tracks = validPool.filter {
            val art = profile.extractArtist(it).lowercase()
            art !in familiarArtistSet && !profile.isTrackHidden(it.id) && !profile.isArtistHidden(art)
        }.distinctBy { it.id }.take(20)

        if (mix5Tracks.isNotEmpty()) {
            mixes.add(
                DailyMix(
                    id = "daily_mix_5",
                    title = "Daily Mix 5",
                    subtitle = "New Discoveries",
                    description = "Fresh sounds and new artists matched to your music taste.",
                    tracks = mix5Tracks,
                    coverUrl = mix5Tracks.firstOrNull()?.thumbnailUrl
                )
            )
        }

        return mixes
    }
}
