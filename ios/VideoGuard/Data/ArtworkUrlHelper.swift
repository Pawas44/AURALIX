import Foundation

struct ArtworkUrlHelper {
    /**
     * Transforms any thumbnail / album art URL into its highest possible reliable resolution.
     * - YouTube Music (Google User Content / GGPHT): Uses =w544-h544-l90-rj (official standard high-res),
     *   stripping session query parameters (?sqp=...) that cause HTTP 403 / 404 rejections.
     * - YouTube Video Thumbnails: Returns clean https://i.ytimg.com/vi/<videoId>/hqdefault.jpg
     *   without expired/session-bound ?sqp= or &rs= query parameters, and avoids 404s from maxresdefault.
     * - Protocol-relative URL conversion (// -> https:)
     * - Fallback to hqdefault.jpg if URL is empty and videoId is available
     */
    static func getHighResArtworkUrl(_ url: String?, videoId: String = "") -> String {
        let raw = url?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        if raw.isEmpty || raw.lowercased() == "null" {
            return videoId.isEmpty ? "" : "https://i.ytimg.com/vi/\(videoId)/hqdefault.jpg"
        }

        var cleanUrl = raw.hasPrefix("//") ? "https:\(raw)" : raw

        // 1. Google User Content / YouTube Music Album Cover CDN
        if cleanUrl.contains("googleusercontent.com") || cleanUrl.contains("ggpht.com") {
            let base = cleanUrl.components(separatedBy: "?").first ?? cleanUrl
            if let regex = try? NSRegularExpression(pattern: "=w\\d+-h\\d+[^?#]*") {
                let range = NSRange(base.startIndex..., in: base)
                return regex.stringByReplacingMatches(in: base, options: [], range: range, withTemplate: "=w544-h544-l90-rj")
            }
            if let regex = try? NSRegularExpression(pattern: "=s\\d+[^?#]*") {
                let range = NSRange(base.startIndex..., in: base)
                return regex.stringByReplacingMatches(in: base, options: [], range: range, withTemplate: "=s544-c-k-no-rj")
            }
            if !base.contains("=") {
                return "\(base)=w544-h544-l90-rj"
            }
            return base
        }

        // 2. YouTube Thumbnail URLs
        if cleanUrl.contains("ytimg.com") || cleanUrl.contains("youtube.com") {
            var extractedId = videoId
            if extractedId.isEmpty {
                if let regex = try? NSRegularExpression(pattern: #"/vi(?:_webp)?/([a-zA-Z0-9_-]{11})/"#) {
                    let range = NSRange(cleanUrl.startIndex..., in: cleanUrl)
                    if let match = regex.firstMatch(in: cleanUrl, options: [], range: range),
                       let r = Range(match.range(at: 1), in: cleanUrl) {
                        extractedId = String(cleanUrl[r])
                    }
                }
            }
            if !extractedId.isEmpty {
                return "https://i.ytimg.com/vi/\(extractedId)/hqdefault.jpg"
            }
            return cleanUrl.components(separatedBy: "?").first ?? cleanUrl
        }

        return cleanUrl
    }
}
