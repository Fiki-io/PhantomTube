package com.phantom.tube.data.innertube.parser

import org.json.JSONObject

object InnerTubeHelpers {

    fun parseRunsText(obj: JSONObject?): String {
        if (obj == null) return ""
        val content = obj.optString("content")
        if (content.isNotBlank()) return content
        val simple = obj.optString("simpleText")
        if (simple.isNotBlank()) return simple
        val runs = obj.optJSONArray("runs") ?: return ""
        val sb = StringBuilder()
        for (i in 0 until runs.length()) {
            val r = runs.optJSONObject(i) ?: continue
            sb.append(r.optString("text", ""))
        }
        return sb.toString()
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

    fun extractThumbnail(obj: JSONObject?, videoId: String): String {
        if (obj == null) return if (videoId.isNotBlank()) "https://i.ytimg.com/vi/$videoId/hqdefault.jpg" else ""
        val thumbnails = obj.optJSONArray("thumbnails")
        if (thumbnails != null && thumbnails.length() > 0) {
            val last = thumbnails.optJSONObject(thumbnails.length() - 1)
            val url = last?.optString("url") ?: ""
            if (url.isNotBlank()) {
                return normalizeUrl(url)
            }
        }
        return if (videoId.isNotBlank()) "https://i.ytimg.com/vi/$videoId/hqdefault.jpg" else ""
    }

    private val yt3AvatarRegex = Regex("""(?:https?:)?//yt3\.(?:ggpht\.com|googleusercontent\.com)/[^\s",]+""")

    fun extractAvatar(json: JSONObject?): String {
        if (json == null) return ""

        // 1. channelThumbnailSupportedRenderers -> channelThumbnailWithLinkRenderer -> thumbnail -> thumbnails
        val ctsr = json.optJSONObject("channelThumbnailSupportedRenderers")
            ?.optJSONObject("channelThumbnailWithLinkRenderer")
            ?.optJSONObject("thumbnail")
        val url1 = extractThumbnail(ctsr, "")
        if (url1.isNotBlank()) return normalizeUrl(url1)

        // 2. avatar -> decoratedAvatarViewModel -> avatar -> avatarViewModel -> image -> sources
        val avatarSources = json.optJSONObject("avatar")
            ?.optJSONObject("decoratedAvatarViewModel")
            ?.optJSONObject("avatar")
            ?.optJSONObject("avatarViewModel")
            ?.optJSONObject("image")
            ?.optJSONArray("sources")
        if (avatarSources != null && avatarSources.length() > 0) {
            val last = avatarSources.optJSONObject(avatarSources.length() - 1)
            val url2 = last?.optString("url") ?: ""
            if (url2.isNotBlank()) {
                return normalizeUrl(url2)
            }
        }

        // 3. avatar -> avatarViewModel -> image -> sources (direct avatarViewModel)
        val directAvatarSources = json.optJSONObject("avatar")
            ?.optJSONObject("avatarViewModel")
            ?.optJSONObject("image")
            ?.optJSONArray("sources")
        if (directAvatarSources != null && directAvatarSources.length() > 0) {
            val last = directAvatarSources.optJSONObject(directAvatarSources.length() - 1)
            val url3 = last?.optString("url") ?: ""
            if (url3.isNotBlank()) {
                return normalizeUrl(url3)
            }
        }

        // 4. channelThumbnail -> thumbnails
        val ct = json.optJSONObject("channelThumbnail")
        val url4 = extractThumbnail(ct, "")
        if (url4.isNotBlank()) return normalizeUrl(url4)

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
            if (decSources != null && decSources.length() > 0) {
                val last = decSources.optJSONObject(decSources.length() - 1)
                val url5a = last?.optString("url") ?: ""
                if (url5a.isNotBlank()) return normalizeUrl(url5a)
            }

            // 5b. avatarStackViewModel (collaborative/featured videos!)
            val stackAvatars = metaImg.optJSONObject("avatarStackViewModel")?.optJSONArray("avatars")
            if (stackAvatars != null && stackAvatars.length() > 0) {
                val firstAvSources = stackAvatars.optJSONObject(0)?.optJSONObject("avatarViewModel")
                    ?.optJSONObject("image")?.optJSONArray("sources")
                if (firstAvSources != null && firstAvSources.length() > 0) {
                    val last = firstAvSources.optJSONObject(firstAvSources.length() - 1)
                    val url5b = last?.optString("url") ?: ""
                    if (url5b.isNotBlank()) return normalizeUrl(url5b)
                }
            }

            // 5c. direct avatarViewModel in image
            val directAvSources = metaImg.optJSONObject("avatarViewModel")
                ?.optJSONObject("image")?.optJSONArray("sources")
            if (directAvSources != null && directAvSources.length() > 0) {
                val last = directAvSources.optJSONObject(directAvSources.length() - 1)
                val url5c = last?.optString("url") ?: ""
                if (url5c.isNotBlank()) return normalizeUrl(url5c)
            }

            // 5d. contentPreviewImageViewModel
            val prevSources = metaImg.optJSONObject("contentPreviewImageViewModel")
                ?.optJSONObject("image")?.optJSONArray("sources")
            if (prevSources != null && prevSources.length() > 0) {
                val last = prevSources.optJSONObject(prevSources.length() - 1)
                val url5d = last?.optString("url") ?: ""
                if (url5d.isNotBlank()) return normalizeUrl(url5d)
            }
        }

        // 6. Direct thumbnail (when passed a channelRenderer or videoOwnerRenderer directly)
        val directThumb = json.optJSONObject("thumbnail")
        val url6 = extractThumbnail(directThumb, "")
        if (url6.isNotBlank()) return normalizeUrl(url6)

        // 7. authorThumbnail (comments)
        val authThumb = json.optJSONObject("authorThumbnail")
        val url7 = extractThumbnail(authThumb, "")
        if (url7.isNotBlank()) return normalizeUrl(url7)

        // 8. Deep regex scan for any yt3 channel avatar URL in the JSON snippet
        val match = yt3AvatarRegex.find(json.toString())
        if (match != null) {
            return normalizeUrl(match.value)
        }

        return ""
    }
}
