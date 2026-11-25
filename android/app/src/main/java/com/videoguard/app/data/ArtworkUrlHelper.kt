package com.videoguard.app.data

import coil.intercept.Interceptor
import coil.request.ErrorResult
import coil.request.ImageResult
import coil.request.SuccessResult

object ArtworkUrlHelper {

    /**
     * Transforms any thumbnail / album art URL into its highest possible reliable resolution.
     * - YouTube Music (Google User Content / GGPHT): Uses =w544-h544-l90-rj (official standard high-res),
     *   preventing HTTP 403 / 404 rejections caused by =w1200 overscaling or session query parameters.
     * - YouTube Video Thumbnails: Returns clean https://i.ytimg.com/vi/<videoId>/hqdefault.jpg
     *   without expired/session-bound ?sqp= or &rs= query parameters, and avoids 404s from maxresdefault.
     * - Protocol-relative URL conversion (// -> https:)
     * - Fallback to hqdefault.jpg if URL is empty and videoId is available
     */
    fun getHighResArtworkUrl(url: String?, videoId: String = ""): String {
        val raw = url?.trim().orEmpty()
        if (raw.isBlank() || raw.equals("null", ignoreCase = true)) {
            return if (videoId.isNotBlank()) "https://i.ytimg.com/vi/$videoId/hqdefault.jpg" else ""
        }

        var cleanUrl = if (raw.startsWith("//")) "https:$raw" else raw

        // 1. Google User Content / YouTube Music Album Cover CDN
        if (cleanUrl.contains("googleusercontent.com") || cleanUrl.contains("ggpht.com")) {
            val base = cleanUrl.substringBefore("?")
            if (base.contains(Regex("=w\\d+-h\\d+"))) {
                return base.replace(Regex("=w\\d+-h\\d+[^?#]*"), "=w544-h544-l90-rj")
            }
            if (base.contains(Regex("=s\\d+"))) {
                return base.replace(Regex("=s\\d+[^?#]*"), "=s544-c-k-no-rj")
            }
            if (!base.contains("=")) {
                return "$base=w544-h544-l90-rj"
            }
            return base
        }

        // 2. YouTube Thumbnail URLs
        if (cleanUrl.contains("ytimg.com") || cleanUrl.contains("youtube.com")) {
            val extractedId = if (videoId.isNotBlank()) {
                videoId
            } else {
                val match = Regex("""/vi(?:_webp)?/([a-zA-Z0-9_-]{11})/""").find(cleanUrl)
                match?.groupValues?.get(1).orEmpty()
            }

            if (extractedId.isNotBlank()) {
                return "https://i.ytimg.com/vi/$extractedId/hqdefault.jpg"
            }
            return cleanUrl.substringBefore("?")
        }

        return cleanUrl
    }
}

/**
 * Coil Interceptor that guarantees high-resolution artwork loading with automatic fallback
 * if YouTube or Google CDN fails for modified params.
 */
class HighQualityArtworkInterceptor : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val request = chain.request
        val data = request.data
        if (data is String && data.isNotBlank()) {
            val highResUrl = ArtworkUrlHelper.getHighResArtworkUrl(data)
            val modifiedRequest = if (highResUrl != data) {
                request.newBuilder().data(highResUrl).build()
            } else {
                request
            }

            val result = chain.proceed(modifiedRequest)

            // If loading the modified URL failed
            if (result is ErrorResult) {
                val currentData = modifiedRequest.data as? String ?: ""

                // 1. Fallback: Retry with clean hqdefault.jpg / mqdefault.jpg if it was ytimg
                val match = Regex("""/vi(?:_webp)?/([a-zA-Z0-9_-]{11})/""").find(currentData)
                val vid = match?.groupValues?.get(1)
                if (!vid.isNullOrBlank()) {
                    val fallbackHq = "https://i.ytimg.com/vi/$vid/hqdefault.jpg"
                    if (fallbackHq != currentData) {
                        val hqResult = chain.proceed(request.newBuilder().data(fallbackHq).build())
                        if (hqResult is SuccessResult) return hqResult
                    }
                    val fallbackMq = "https://i.ytimg.com/vi/$vid/mqdefault.jpg"
                    val mqResult = chain.proceed(request.newBuilder().data(fallbackMq).build())
                    if (mqResult is SuccessResult) return mqResult
                }

                // 2. Fallback: For Google User Content / GGPHT, try unscaled raw URL
                if (currentData.contains("googleusercontent.com") || currentData.contains("ggpht.com")) {
                    val rawGoogleUrl = currentData.substringBefore("=")
                    if (rawGoogleUrl.isNotBlank() && rawGoogleUrl != currentData) {
                        val rawResult = chain.proceed(request.newBuilder().data(rawGoogleUrl).build())
                        if (rawResult is SuccessResult) return rawResult
                    }
                }

                // 3. Fallback: Retry with original unmodified URL if different
                if (data != currentData) {
                    val originalResult = chain.proceed(request.newBuilder().data(data).build())
                    if (originalResult is SuccessResult) return originalResult
                }
            }
            return result
        }
        return chain.proceed(request)
    }
}
