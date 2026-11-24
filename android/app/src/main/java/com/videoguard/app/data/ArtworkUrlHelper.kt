package com.videoguard.app.data

import coil.intercept.Interceptor
import coil.request.ErrorResult
import coil.request.ImageResult
import coil.request.SuccessResult

object ArtworkUrlHelper {

    /**
     * Transforms any thumbnail / album art URL into its highest possible resolution master.
     * - YouTube Music (Google User Content / GGPHT): Replaces size params (=w120-h120, =s120, etc.) with =w1200-h1200-l90-rj
     * - YouTube Video Thumbnails: Upgrades hqdefault.jpg / mqdefault.jpg / sddefault.jpg to maxresdefault.jpg
     * - Fallback to maxresdefault.jpg if URL is empty and videoId is available
     */
    fun getHighResArtworkUrl(url: String?, videoId: String = ""): String {
        val raw = url?.trim().orEmpty()
        if (raw.isBlank()) {
            return if (videoId.isNotBlank()) "https://i.ytimg.com/vi/$videoId/maxresdefault.jpg" else ""
        }

        var cleanUrl = if (raw.startsWith("//")) "https:$raw" else raw

        // 1. Google User Content / YouTube Music Album Cover CDN
        if (cleanUrl.contains("googleusercontent.com") || cleanUrl.contains("ggpht.com")) {
            // Replace =w...-h... or =s... with ultra-HD =w1200-h1200-l90-rj
            if (cleanUrl.contains(Regex("=w\\d+-h\\d+"))) {
                return cleanUrl.replace(Regex("=w\\d+-h\\d+[^?#]*"), "=w1200-h1200-l90-rj")
            }
            if (cleanUrl.contains(Regex("=s\\d+"))) {
                return cleanUrl.replace(Regex("=s\\d+[^?#]*"), "=s1200-c-k-no-rj")
            }
            return cleanUrl
        }

        // 2. YouTube Thumbnail URLs
        if (cleanUrl.contains("ytimg.com") || cleanUrl.contains("youtube.com")) {
            if (cleanUrl.contains("hqdefault") || cleanUrl.contains("mqdefault") || cleanUrl.contains("sddefault") || cleanUrl.contains("default.jpg")) {
                return cleanUrl
                    .replace("hqdefault.jpg", "maxresdefault.jpg")
                    .replace("mqdefault.jpg", "maxresdefault.jpg")
                    .replace("sddefault.jpg", "maxresdefault.jpg")
                    .replace("default.jpg", "maxresdefault.jpg")
            }
        }

        return cleanUrl
    }
}

/**
 * Coil Interceptor that guarantees high-resolution artwork loading with automatic fallback
 * if YouTube returns HTTP 404 for maxresdefault.jpg.
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
            // If maxresdefault failed with 404 (e.g. very old 360p-only YouTube videos)
            if (result is ErrorResult) {
                val currentData = modifiedRequest.data as? String ?: ""
                if (currentData.contains("maxresdefault.jpg")) {
                    // Try sddefault.jpg (640x480)
                    val sdUrl = currentData.replace("maxresdefault.jpg", "sddefault.jpg")
                    val sdResult = chain.proceed(request.newBuilder().data(sdUrl).build())
                    if (sdResult is SuccessResult) {
                        return sdResult
                    }
                    // Final fallback to hqdefault.jpg (480x360)
                    val hqUrl = currentData.replace("maxresdefault.jpg", "hqdefault.jpg")
                    return chain.proceed(request.newBuilder().data(hqUrl).build())
                }
            }
            return result
        }
        return chain.proceed(request)
    }
}
