package com.phantom.tube.core.config

/**
 * Centralized endpoint configuration, WebView URL request classifier,
 * and remote version-lock state for Phantom Tube.
 */
object PhantomConfig {

    @Volatile
    private var engineLocked: Boolean = false

    private const val VIRTUAL_ORIGIN = "https://appassets.androidplatform.net"

    private const val WEB_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36"

    private const val DESKTOP_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    private const val YT_INNERTUBE_BASE = "https://www.youtube.com/youtubei/v1/"
    private const val YT_SUGGEST_PRIMARY = "https://suggestqueries-clients6.youtube.com/complete/search"
    private const val YT_SUGGEST_FALLBACK = "https://suggestqueries.google.com/complete/search"
    private const val UPDATE_VERSION_URL =
        "https://raw.githubusercontent.com/Fiki-io/phantom-version/main/version.json"

    private const val DOWNLOAD_KEY_BASE_URL = "https://cnv.cx/v2/sanity/key?id="
    private const val DOWNLOAD_CONVERTER_URL = "https://cnv.cx/v2/converter"
    private const val DOWNLOAD_REFERER_URL = "https://iframe.y2meta-uk.com/"
    private const val DOWNLOAD_ORIGIN_URL = "https://iframe.y2meta-uk.com"

    fun applyVersionPolicy(currentCode: Int, minCode: Int, forceUpdate: Boolean): Boolean {
        if (engineLocked) return false
        if (forceUpdate || (minCode > 0 && currentCode < minCode)) {
            engineLocked = true
            return false
        }
        return true
    }

    fun isLocked(): Boolean = engineLocked

    fun getOrigin(): String = VIRTUAL_ORIGIN

    fun getUserAgent(): String = WEB_USER_AGENT

    fun getDesktopUserAgent(): String = DESKTOP_USER_AGENT

    fun getSponsorBlockRequestUrl(videoId: String): String {
        return "https://sponsor.ajay.app/api/skipSegments?videoID=$videoId&categories=%5B%22sponsor%22%2C%22selfpromo%22%2C%22interaction%22%2C%22intro%22%2C%22outro%22%5D"
    }

    fun getSearchEndpoint(): String {
        return if (engineLocked) "" else "${YT_INNERTUBE_BASE}search?prettyPrint=false"
    }

    fun getNextEndpoint(): String {
        return if (engineLocked) "" else "${YT_INNERTUBE_BASE}next?prettyPrint=false"
    }

    fun getBrowseEndpoint(): String {
        return if (engineLocked) "" else "${YT_INNERTUBE_BASE}browse?prettyPrint=false"
    }

    fun getSuggestEndpoints(encodedQuery: String, hl: String = "id", gl: String = "ID"): List<String> {
        if (engineLocked) return emptyList()
        val safeHl = hl.ifBlank { "id" }
        val safeGl = gl.ifBlank { "ID" }
        return listOf(
            "$YT_SUGGEST_PRIMARY?client=youtube&ds=yt&hl=$safeHl&gl=$safeGl&q=$encodedQuery",
            "$YT_SUGGEST_PRIMARY?client=firefox&ds=yt&hl=$safeHl&gl=$safeGl&q=$encodedQuery",
            "$YT_SUGGEST_FALLBACK?client=firefox&ds=yt&hl=$safeHl&gl=$safeGl&q=$encodedQuery"
        )
    }

    /**
     * Classifies the incoming WebView request URL.
     * Returns:
     *   1 -> In-memory virtual engine HTML route ("/engine/render")
     *   2 -> Embed HTML target to inject CSS/cleaner
     *   3 -> Video player CSS target to patch
     *   4 -> Telemetry / Cast / Ad / Logging bloat to short-circuit with 204
     *   0 -> Pass-through / no modification
     */
    fun classifyRequestUrl(url: String, isBackgroundMode: Boolean = false): Int {
        if (url.contains("/engine/render")) return 1
        val lower = url.lowercase()
        if (lower.contains("doubleclick.net") ||
            lower.contains("googlesyndication.com") ||
            lower.contains("googleadservices.com") ||
            lower.contains("google-analytics.com") ||
            lower.contains("play.google.com/log") ||
            lower.contains("/youtubei/v1/log_event") ||
            lower.contains("/api/stats/ads") ||
            lower.contains("/api/stats/atr") ||
            lower.contains("/ptracking") ||
            lower.contains("/error_204") ||
            lower.contains("/generate_204") ||
            lower.contains("cast_sender.js") ||
            lower.contains("ad_status.js")
        ) {
            return 4
        }
        val host = lower.substringAfter("://", "").substringBefore('/').substringBefore('?')
        if (isBackgroundMode &&
            !host.contains("googlevideo.com") &&
            !host.contains("appassets.androidplatform.net")
        ) {
            if (host == "i.ytimg.com" ||
                host == "i9.ytimg.com" ||
                host == "yt3.ggpht.com" ||
                host == "yt3.googleusercontent.com" ||
                host == "lh3.googleusercontent.com" ||
                host == "fonts.gstatic.com" ||
                host == "fonts.googleapis.com"
            ) {
                return 4
            }
            val pathOnly = lower.substringBefore('?')
            if (pathOnly.endsWith(".jpg") ||
                pathOnly.endsWith(".jpeg") ||
                pathOnly.endsWith(".png") ||
                pathOnly.endsWith(".webp") ||
                pathOnly.endsWith(".gif") ||
                pathOnly.endsWith(".avif") ||
                pathOnly.endsWith(".woff") ||
                pathOnly.endsWith(".woff2") ||
                pathOnly.endsWith(".ttf") ||
                pathOnly.endsWith(".otf")
            ) {
                return 4
            }
        }
        val isYtHost = lower.contains("youtube.com") || lower.contains("youtube-nocookie.com")
        if (isYtHost && (lower.contains("/embed/") || lower.contains("/embed?"))) return 2
        val isMediaHost = isYtHost || lower.contains("googlevideo.com")
        if (isMediaHost && (lower.contains(".css") || lower.contains("/ss/"))) return 3
        return 0
    }

    fun getUpdateEndpoint(): String = UPDATE_VERSION_URL

    fun getDownloadKeyEndpoint(videoId: String): String {
        return if (engineLocked) "" else "$DOWNLOAD_KEY_BASE_URL$videoId"
    }

    fun getDownloadConverterEndpoint(): String {
        return if (engineLocked) "" else DOWNLOAD_CONVERTER_URL
    }

    fun getDownloadReferer(): String {
        return if (engineLocked) "" else DOWNLOAD_REFERER_URL
    }

    fun getDownloadOrigin(): String {
        return if (engineLocked) "" else DOWNLOAD_ORIGIN_URL
    }
}
