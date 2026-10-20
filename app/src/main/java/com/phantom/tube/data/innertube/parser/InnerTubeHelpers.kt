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
            // Prefer medium-high resolution (width 320..720) to save up to 75% bitmap RAM over 1080p maxresdefault
            var chosenUrl = ""
            for (i in 0 until thumbnails.length()) {
                val item = thumbnails.optJSONObject(i) ?: continue
                val url = item.optString("url")
                val w = item.optInt("width", 0)
                if (url.isNotBlank()) {
                    chosenUrl = url
                    if (w in 320..720) {
                        break
                    }
                }
            }
            if (chosenUrl.isNotBlank()) {
                return normalizeUrl(chosenUrl)
            }
        }
        return if (videoId.isNotBlank()) "https://i.ytimg.com/vi/$videoId/hqdefault.jpg" else ""
    }

    private fun pickAvatarSourceUrl(sources: org.json.JSONArray?): String {
        if (sources == null || sources.length() == 0) return ""
        // Avatars are rendered at 36dp; pick compact resolution (<= 120px or first item) instead of 800x800
        for (i in 0 until sources.length()) {
            val item = sources.optJSONObject(i) ?: continue
            val url = item.optString("url")
            val w = item.optInt("width", 0)
            if (url.isNotBlank() && (w in 48..140 || i == 0)) {
                return normalizeUrl(url)
            }
        }
        val first = sources.optJSONObject(0)?.optString("url") ?: ""
        return normalizeUrl(first)
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
