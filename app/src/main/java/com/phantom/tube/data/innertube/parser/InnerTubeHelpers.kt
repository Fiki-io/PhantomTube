package com.phantom.tube.data.innertube.parser

import com.phantom.tube.core.util.unwrapYouTubeRedirectUrl
import org.json.JSONObject

object InnerTubeHelpers {

    fun parseRunsText(obj: JSONObject?): String {
        if (obj == null) return ""
        val content = obj.optString("content")
        if (content.isNotBlank()) {
            val commandRuns = obj.optJSONArray("commandRuns")
            if (commandRuns != null && commandRuns.length() > 0) {
                return resolveAttributedContentUrls(content, commandRuns)
            }
            return content
        }
        val simple = obj.optString("simpleText")
        if (simple.isNotBlank()) return simple
        val runs = obj.optJSONArray("runs") ?: return ""
        val sb = StringBuilder()
        for (i in 0 until runs.length()) {
            val r = runs.optJSONObject(i) ?: continue
            val rawText = r.optString("text", "")
            val navEndpoint = r.optJSONObject("navigationEndpoint")
            val rawUrl = navEndpoint?.optJSONObject("urlEndpoint")?.optString("url")
                ?.takeIf { it.isNotBlank() }
                ?: navEndpoint?.optJSONObject("commandMetadata")
                    ?.optJSONObject("webCommandMetadata")
                    ?.optString("url")
                    ?.takeIf { it.startsWith("http") }

            if (!rawUrl.isNullOrBlank()) {
                val unwrapped = unwrapYouTubeRedirectUrl(rawUrl)
                val looksLikeTruncatedOrUrl = rawText.contains("...") ||
                    rawText.contains("…") ||
                    rawText.startsWith("http://", ignoreCase = true) ||
                    rawText.startsWith("https://", ignoreCase = true) ||
                    rawText.startsWith("www.", ignoreCase = true)
                if (unwrapped.isNotBlank() && looksLikeTruncatedOrUrl) {
                    sb.append(unwrapped)
                } else {
                    sb.append(rawText)
                }
            } else {
                sb.append(rawText)
            }
        }
        return sb.toString()
    }

    private fun resolveAttributedContentUrls(content: String, commandRuns: org.json.JSONArray): String {
        return try {
            data class SpanReplacement(val start: Int, val end: Int, val replacement: String)
            val replacements = mutableListOf<SpanReplacement>()
            for (i in 0 until commandRuns.length()) {
                val cmdRun = commandRuns.optJSONObject(i) ?: continue
                val start = cmdRun.optInt("startIndex", -1)
                val length = cmdRun.optInt("length", -1)
                if (start < 0 || length <= 0 || start + length > content.length) continue

                val innerCmd = cmdRun.optJSONObject("onTap")?.optJSONObject("innertubeCommand") ?: continue
                val url = innerCmd.optJSONObject("urlEndpoint")?.optString("url")
                    ?.takeIf { it.isNotBlank() }
                    ?: innerCmd.optJSONObject("commandMetadata")
                        ?.optJSONObject("webCommandMetadata")
                        ?.optString("url")
                        ?.takeIf { it.startsWith("http") }
                    ?: continue

                val unwrapped = unwrapYouTubeRedirectUrl(url)
                if (unwrapped.isBlank()) continue

                val spanText = content.substring(start, start + length)
                val isTruncatedOrUrl = spanText.contains("...") ||
                    spanText.contains("…") ||
                    spanText.startsWith("http://", ignoreCase = true) ||
                    spanText.startsWith("https://", ignoreCase = true) ||
                    spanText.startsWith("www.", ignoreCase = true)
                if (isTruncatedOrUrl && spanText != unwrapped) {
                    replacements.add(SpanReplacement(start, start + length, unwrapped))
                }
            }
            if (replacements.isEmpty()) return content
            val sb = StringBuilder(content)
            replacements.sortedByDescending { it.start }.forEach { rep ->
                if (rep.start >= 0 && rep.end <= sb.length && rep.start < rep.end) {
                    sb.replace(rep.start, rep.end, rep.replacement)
                }
            }
            sb.toString()
        } catch (_: Exception) {
            content
        }
    }

    /**
     * Standardizes raw YouTube view count strings and preserves localized formats
     * returned directly by YouTube InnerTube based on the user's selected language.
     */
    fun normalizeViewCount(raw: String): String {
        return raw.trim()
    }

    /**
     * Standardizes raw YouTube relative time strings and preserves localized formats
     * returned directly by YouTube InnerTube based on the user's selected language.
     */
    fun normalizePublishedTime(raw: String): String {
        return raw.trim()
    }

    /**
     * Centralized metadata line builder for video cards and details across the entire app.
     * Guarantees consistent styling: "Channel • 98 jt x ditonton • 4 tahun lalu"
     */
    fun formatVideoMeta(channelTitle: String, viewCountText: String, publishedTimeText: String): String {
        return buildString {
            if (channelTitle.isNotBlank()) {
                append(channelTitle)
            }
            val normViews = normalizeViewCount(viewCountText)
            if (normViews.isNotBlank()) {
                if (isNotEmpty()) append(" • ")
                append(normViews)
            }
            val normDate = normalizePublishedTime(publishedTimeText)
            if (normDate.isNotBlank()) {
                if (isNotEmpty()) append(" • ")
                append(normDate)
            }
        }
    }

    fun normalizeUrl(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val trimmed = raw.trim()
        if (trimmed.startsWith("//")) {
            return "https:$trimmed"
        }
        if (trimmed.startsWith("http://")) {
            return "https://" + trimmed.removePrefix("http://")
        }
        return trimmed
    }

    private val AVATAR_SIZE_REGEX = Regex("""=s\d+(-[a-zA-Z0-9_-]+)?""")

    fun optimizeAvatarUrl(raw: String?): String {
        val norm = normalizeUrl(raw)
        if (norm.isBlank()) return ""
        if (norm.contains("ggpht.com") || norm.contains("googleusercontent.com")) {
            if (AVATAR_SIZE_REGEX.containsMatchIn(norm)) {
                return AVATAR_SIZE_REGEX.replace(norm, "=s88-c-k-c0x00ffffff-no-rj")
            }
        }
        return norm
    }

    fun extractThumbnail(obj: JSONObject?, videoId: String): String {
        if (obj == null) return if (videoId.isNotBlank()) "https://i.ytimg.com/vi/$videoId/mqdefault.jpg" else ""
        val thumbnails = obj.optJSONArray("thumbnails")
        if (thumbnails != null && thumbnails.length() > 0) {
            // Prefer compact 16:9 resolution (width 240..380) to minimize bitmap RAM while staying sharp
            var chosenUrl = ""
            for (i in 0 until thumbnails.length()) {
                val item = thumbnails.optJSONObject(i) ?: continue
                val url = item.optString("url")
                val w = item.optInt("width", 0)
                if (url.isNotBlank()) {
                    chosenUrl = url
                    if (w in 240..380) {
                        break
                    }
                }
            }
            if (chosenUrl.isNotBlank()) {
                return normalizeUrl(chosenUrl)
            }
        }
        return if (videoId.isNotBlank()) "https://i.ytimg.com/vi/$videoId/mqdefault.jpg" else ""
    }

    private fun pickAvatarSourceUrl(sources: org.json.JSONArray?): String {
        if (sources == null || sources.length() == 0) return ""
        // Avatars are rendered at 36dp; pick compact resolution (<= 120px or first item) and clamp CDN size param
        for (i in 0 until sources.length()) {
            val item = sources.optJSONObject(i) ?: continue
            val url = item.optString("url")
            val w = item.optInt("width", 0)
            if (url.isNotBlank() && (w in 48..120 || i == 0)) {
                return optimizeAvatarUrl(url)
            }
        }
        val first = sources.optJSONObject(0)?.optString("url") ?: ""
        return optimizeAvatarUrl(first)
    }

    fun extractAvatar(json: JSONObject?): String {
        if (json == null) return ""

        // 1. channelThumbnailSupportedRenderers -> channelThumbnailWithLinkRenderer -> thumbnail -> thumbnails
        val ctsr = json.optJSONObject("channelThumbnailSupportedRenderers")
            ?.optJSONObject("channelThumbnailWithLinkRenderer")
            ?.optJSONObject("thumbnail")
            ?.optJSONArray("thumbnails")
        val url1 = pickAvatarSourceUrl(ctsr)
        if (url1.isNotBlank()) return url1

        // 2. avatar -> decoratedAvatarViewModel -> avatar -> avatarViewModel -> image -> sources
        val avatarSources = json.optJSONObject("avatar")
            ?.optJSONObject("decoratedAvatarViewModel")
            ?.optJSONObject("avatar")
            ?.optJSONObject("avatarViewModel")
            ?.optJSONObject("image")
            ?.optJSONArray("sources")
        val url2 = pickAvatarSourceUrl(avatarSources)
        if (url2.isNotBlank()) return url2

        // 3. avatar -> avatarViewModel -> image -> sources (direct avatarViewModel)
        val directAvatarSources = json.optJSONObject("avatar")
            ?.optJSONObject("avatarViewModel")
            ?.optJSONObject("image")
            ?.optJSONArray("sources")
        val url3 = pickAvatarSourceUrl(directAvatarSources)
        if (url3.isNotBlank()) return url3

        // 4. channelThumbnail -> thumbnails
        val ct = json.optJSONObject("channelThumbnail")?.optJSONArray("thumbnails")
        val url4 = pickAvatarSourceUrl(ct)
        if (url4.isNotBlank()) return url4

        // 5. metadata -> lockupMetadataViewModel -> image
        val metaImg = json.optJSONObject("metadata")
            ?.optJSONObject("lockupMetadataViewModel")
            ?.optJSONObject("image")
        if (metaImg != null) {
            // 5a. decoratedAvatarViewModel
            val decSources = metaImg.optJSONObject("decoratedAvatarViewModel")
                ?.optJSONObject("avatar")
                ?.optJSONObject("avatarViewModel")
                ?.optJSONObject("image")
                ?.optJSONArray("sources")
            val url5a = pickAvatarSourceUrl(decSources)
            if (url5a.isNotBlank()) return url5a

            // 5b. avatarStackViewModel (collaborative/featured videos!)
            val stackAvatars = metaImg.optJSONObject("avatarStackViewModel")?.optJSONArray("avatars")
            if (stackAvatars != null && stackAvatars.length() > 0) {
                val firstAvSources = stackAvatars.optJSONObject(0)?.optJSONObject("avatarViewModel")
                    ?.optJSONObject("image")?.optJSONArray("sources")
                val url5b = pickAvatarSourceUrl(firstAvSources)
                if (url5b.isNotBlank()) return url5b
            }

            // 5c. direct avatarViewModel in image
            val directAvSources = metaImg.optJSONObject("avatarViewModel")
                ?.optJSONObject("image")?.optJSONArray("sources")
            val url5c = pickAvatarSourceUrl(directAvSources)
            if (url5c.isNotBlank()) return url5c

            // 5d. contentPreviewImageViewModel
            val prevSources = metaImg.optJSONObject("contentPreviewImageViewModel")
                ?.optJSONObject("image")?.optJSONArray("sources")
            val url5d = pickAvatarSourceUrl(prevSources)
            if (url5d.isNotBlank()) return url5d
        }

        // 6. Direct thumbnail (when passed a channelRenderer or videoOwnerRenderer directly)
        val directThumb = json.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
        val url6 = pickAvatarSourceUrl(directThumb)
        if (url6.isNotBlank()) return url6

        // 7. authorThumbnail (comments)
        val authThumb = json.optJSONObject("authorThumbnail")?.optJSONArray("thumbnails")
        val url7 = pickAvatarSourceUrl(authThumb)
        if (url7.isNotBlank()) return url7

        return ""
    }
}
