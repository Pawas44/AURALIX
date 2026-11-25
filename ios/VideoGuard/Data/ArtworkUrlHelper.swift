import Foundation

struct ArtworkUrlHelper {
    /**
     * Transforms any thumbnail / album art URL into its highest possible reliable resolution.
     * - YouTube Music (Google User Content / GGPHT): Uses =w544-h544-l90-rj (official standard high-res),
     *   preventing HTTP 403 / 404 rejections caused by =w1200 overscaling.
     * - YouTube Video Thumbnails: Upgrades hqdefault.jpg / mqdefault.jpg / sddefault.jpg to maxresdefault.jpg
     * - Protocol-relative URL conversion (// -> https:)
     * - Fallback to maxresdefault.jpg if URL is empty and videoId is available
     */
    static func getHighResArtworkUrl(_ url: String?, videoId: String = "") -> String {
        let raw = url?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        if raw.isEmpty || raw.lowercased() == "null" {
            return videoId.isEmpty ? "" : "https://i.ytimg.com/vi/\(videoId)/maxresdefault.jpg"
        }

        var cleanUrl = raw.hasPrefix("//") ? "https:\(raw)" : raw

        // 1. Google User Content / YouTube Music Album Cover CDN
        if cleanUrl.contains("googleusercontent.com") || cleanUrl.contains("ggpht.com") {
            if let regex = try? NSRegularExpression(pattern: "=w\\d+-h\\d+[^?#]*") {
                let range = NSRange(cleanUrl.startIndex..., in: cleanUrl)
                cleanUrl = regex.stringByReplacingMatches(in: cleanUrl, options: [], range: range, withTemplate: "=w544-h544-l90-rj")
            }
            if let regex = try? NSRegularExpression(pattern: "=s\\d+[^?#]*") {
                let range = NSRange(cleanUrl.startIndex..., in: cleanUrl)
                cleanUrl = regex.stringByReplacingMatches(in: cleanUrl, options: [], range: range, withTemplate: "=s544-c-k-no-rj")
            }
            return cleanUrl
        }

        // 2. YouTube Thumbnail URLs
        if cleanUrl.contains("ytimg.com") || cleanUrl.contains("youtube.com") {
            if cleanUrl.contains("hqdefault") || cleanUrl.contains("mqdefault") || cleanUrl.contains("sddefault") || cleanUrl.contains("default.jpg") {
                return cleanUrl
                    .replacingOccurrences(of: "hqdefault.jpg", with: "maxresdefault.jpg")
                    .replacingOccurrences(of: "mqdefault.jpg", with: "maxresdefault.jpg")
                    .replacingOccurrences(of: "sddefault.jpg", with: "maxresdefault.jpg")
                    .replacingOccurrences(of: "default.jpg", with: "maxresdefault.jpg")
            }
        }

        return cleanUrl
    }
}
