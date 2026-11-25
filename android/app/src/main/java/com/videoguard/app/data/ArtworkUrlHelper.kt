package com.videoguard.app.data

import coil.intercept.Interceptor
import coil.request.ErrorResult
import coil.request.ImageResult
import coil.request.SuccessResult

object ArtworkUrlHelper {

    /**
     * Transforms any thumbnail / album art URL into its highest possible reliable resolution.
     * - YouTube Music (Google User Content / GGPHT): Uses =w544-h544-l90-rj (official standard high-res),
     *   preventing HTTP 403 / 404 rejections caused by =w1200 overscaling.
     * - YouTube Video Thumbnails: Upgrades hqdefault.jpg / mqdefault.jpg / sddefault.jpg to maxresdefault.jpg
     * - Protocol-relative URL conversion (// -> https:)
     * - Fallback to maxresdefault.jpg if URL is empty and videoId is available
     */
    fun getHighResArtworkUrl(url: String?, videoId: String = ""): String {
        val raw = url?.trim().orEmpty()
        if (raw.isBlank() || raw.equals("null", ignoreCase = true)) {
            return if (videoId.isNotBlank()) "https://i.ytimg.com/vi/$videoId/maxresdefault.jpg" else ""
        }

        var cleanUrl = if (raw.startsWith("//")) "https:$raw" else raw

        // 1. Google User Content / YouTube Music Album Cover CDN
        if (cleanUrl.contains("googleusercontent.com") || cleanUrl.contains("ggpht.com")) {
            // Replace =w...-h... with official standard high-res =w544-h544-l90-rj
            if (cleanUrl.contains(Regex("=w\\d+-h\\d+"))) {
                return cleanUrl.replace(Regex("=w\\d+-h\\d+[^?#]*"), "=w544-h544-l90-rj")
            }
            if (cleanUrl.contains(Regex("=s\\d+"))) {
                return cleanUrl.replace(Regex("=s\\d+[^?#]*"), "=s544-c-k-no-rj")
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
 * if YouTube returns HTTP 404 for maxresdefault.jpg or Google CDN fails for modified params.
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

            // If loading the high-res modified URL failed
            if (result is ErrorResult) {
                val currentData = modifiedRequest.data as? String ?: ""

                // 1. Fallback: Retry with original unmodified URL if we modified it
                if (data != currentData) {
                    val originalResult = chain.proceed(request.newBuilder().data(data).build())
                    if (originalResult is SuccessResult) {
                        return originalResult
                    }
                }

                // 2. Fallback: For Google User Content / GGPHT, try unscaled raw URL (without '=' sizing params)
                if (currentData.contains("googleusercontent.com") || currentData.contains("ggpht.com")) {
                    val rawGoogleUrl = currentData.substringBefore("=")
                    if (rawGoogleUrl.isNotBlank() && rawGoogleUrl != currentData) {
                        val rawResult = chain.proceed(request.newBuilder().data(rawGoogleUrl).build())
                        if (rawResult is SuccessResult) {
                            return rawResult
                        }
                    }
                }

                // 3. Fallback: If maxresdefault failed with 404 (e.g. 360p/480p-only YouTube videos)
                if (currentData.contains("maxresdefault.jpg")) {
                    // Try sddefault.jpg (640x480)
                    val sdUrl = currentData.replace("maxresdefault.jpg", "sddefault.jpg")
                    val sdResult = chain.proceed(request.newBuilder().data(sdUrl).build())
                    if (sdResult is SuccessResult) {
                        return sdResult
                    }
                    // Final fallback to hqdefault.jpg (480x360)
                    val hqUrl = currentData.replace("maxresdefault.jpg", "hqdefault.jpg")
                    val hqResult = chain.proceed(request.newBuilder().data(hqUrl).build())
                    if (hqResult is SuccessResult) {
                        return hqResult
                    }
                }
            }
            return result
        }
        return chain.proceed(request)
    }
}
