package com.phantom.tube.data.innertube

import com.phantom.tube.data.model.ChannelProfile
import com.phantom.tube.data.model.CommentsResult
import com.phantom.tube.data.model.FeedResult
import com.phantom.tube.data.model.NextQueue
import com.phantom.tube.data.model.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class InnerTubeClient(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val userAgent = com.phantom.tube.core.security.PhantomNative.getDesktopUserAgent()

    var regionCodeProvider: (() -> String)? = null
    var languageCodeProvider: (() -> String)? = null

    private fun createClientContext(): JSONObject {
        val region = regionCodeProvider?.invoke() ?: "GLOBAL"
        val lang = languageCodeProvider?.invoke() ?: "en"

        val gl = when (region.uppercase()) {
            "GLOBAL" -> "US"
            "US" -> "US"
            "JP" -> "JP"
            "KR" -> "KR"
            "GB" -> "GB"
            "ID" -> "ID"
            "ES" -> "ES"
            "RU" -> "RU"
            else -> region.uppercase()
        }

        val hl = when (lang.lowercase()) {
            "en" -> "en"
            "ja" -> "ja"
            "es" -> "es"
            "ru" -> "ru"
            "id" -> "id"
            else -> lang.lowercase()
        }

        val client = JSONObject().apply {
            put("clientName", "WEB")
            put("clientVersion", "2.20240901.00.00")
            put("hl", hl)
            put("gl", gl)
        }
        return JSONObject().apply {
            put("client", client)
        }
    }

    suspend fun fetchFeedPage(
        query: String? = null,
        params: String? = null,
        continuation: String? = null
    ): FeedResult = withContext(Dispatchers.IO) {
        try {
            val bodyJson = JSONObject().apply {
                put("context", createClientContext())
                if (!continuation.isNullOrBlank()) {
                    put("continuation", continuation)
                } else {
                    put("query", query ?: "trending indonesia")
                    if (!params.isNullOrBlank()) {
                        put("params", params)
                    }
                }
            }
            val request = Request.Builder()
                .url(com.phantom.tube.core.security.PhantomNative.getSearchEndpoint())
                .header("Content-Type", "application/json")
                .header("User-Agent", userAgent)
                .post(bodyJson.toString().toRequestBody(jsonMediaType))
                .build()

            val responseBody = httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext FeedResult()
                response.body?.string() ?: return@withContext FeedResult()
            }
            return@withContext InnerTubeParser.parseFeedWithContinuation(responseBody)
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext FeedResult()
        }
    }

    suspend fun fetchFeed(query: String = "trending"): List<VideoItem> = withContext(Dispatchers.IO) {
        return@withContext fetchFeedPage(query = query).videos
    }

    suspend fun search(query: String): List<VideoItem> = withContext(Dispatchers.IO) {
        return@withContext fetchFeedPage(query = query).videos
    }

    suspend fun searchPage(
        query: String? = null,
        params: String? = null,
        continuation: String? = null
    ): FeedResult = withContext(Dispatchers.IO) {
        return@withContext fetchFeedPage(query = query, params = params, continuation = continuation)
    }

    suspend fun fetchWatchNext(videoId: String, playlistId: String? = null): NextQueue? = withContext(Dispatchers.IO) {
        try {
            val bodyJson = JSONObject().apply {
                put("context", createClientContext())
                put("videoId", videoId)
                val targetPlaylistId = if (!playlistId.isNullOrBlank()) playlistId else "RD$videoId"
                put("playlistId", targetPlaylistId)
            }
            val request = Request.Builder()
                .url(com.phantom.tube.core.security.PhantomNative.getNextEndpoint())
                .header("Content-Type", "application/json")
                .header("User-Agent", userAgent)
                .post(bodyJson.toString().toRequestBody(jsonMediaType))
                .build()

            val responseBody = httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                response.body?.string() ?: return@withContext null
            }
            return@withContext InnerTubeParser.parseWatchNext(responseBody, videoId)
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }

    suspend fun fetchWatchNextContinuation(
        continuation: String,
        currentVideoId: String = ""
    ): FeedResult = withContext(Dispatchers.IO) {
        try {
            val bodyJson = JSONObject().apply {
                put("context", createClientContext())
                put("continuation", continuation)
            }
            val request = Request.Builder()
                .url(com.phantom.tube.core.security.PhantomNative.getNextEndpoint())
                .header("Content-Type", "application/json")
                .header("User-Agent", userAgent)
                .post(bodyJson.toString().toRequestBody(jsonMediaType))
                .build()

            val responseBody = httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext FeedResult()
                response.body?.string() ?: return@withContext FeedResult()
            }
            return@withContext InnerTubeParser.parseWatchNextContinuation(responseBody, currentVideoId)
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext FeedResult()
        }
    }

    suspend fun fetchComments(continuationToken: String): CommentsResult = withContext(Dispatchers.IO) {
        try {
            val bodyJson = JSONObject().apply {
                put("context", createClientContext())
                put("continuation", continuationToken)
            }
            val request = Request.Builder()
                .url(com.phantom.tube.core.security.PhantomNative.getNextEndpoint())
                .header("Content-Type", "application/json")
                .header("User-Agent", userAgent)
                .post(bodyJson.toString().toRequestBody(jsonMediaType))
                .build()

            val responseBody = httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext CommentsResult()
                response.body?.string() ?: return@withContext CommentsResult()
            }
            return@withContext InnerTubeParser.parseComments(responseBody)
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext CommentsResult()
        }
    }

    private val STREAM_AVATAR_URL_REGEX =
        Regex("""https://yt3\.(?:ggpht|googleusercontent)\.com/[a-zA-Z0-9_=-]+(?:/[a-zA-Z0-9_=-]+)*=s\d+[^"\\]*""")

    /**
     * Zero-DOM streaming avatar extractor.
     * Reads only the leading header bytes (max 96 KB) from the browse stream to locate the channel avatar
     * without allocating a multi-megabyte JSON String or JSONObject DOM tree.
     */
    suspend fun fetchChannelAvatarFast(channelId: String): String? = withContext(Dispatchers.IO) {
        val targetChannelId = channelId.trim()
        if (!targetChannelId.startsWith("UC")) return@withContext null
        try {
            val bodyJson = JSONObject().apply {
                put("context", createClientContext())
                put("browseId", targetChannelId)
            }
            val request = Request.Builder()
                .url(com.phantom.tube.core.security.PhantomNative.getBrowseEndpoint())
                .header("Content-Type", "application/json")
                .header("User-Agent", userAgent)
                .post(bodyJson.toString().toRequestBody(jsonMediaType))
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val stream = response.body?.byteStream() ?: return@withContext null
                val buffer = ByteArray(8192)
                val out = java.io.ByteArrayOutputStream(32768)
                var totalRead = 0
                val maxHeaderBytes = 96 * 1024
                while (totalRead < maxHeaderBytes) {
                    val read = stream.read(buffer)
                    if (read <= 0) break
                    out.write(buffer, 0, read)
                    totalRead += read
                    if (totalRead >= 16384) {
                        val partial = out.toString("UTF-8")
                        val match = STREAM_AVATAR_URL_REGEX.find(partial)
                        if (match != null) {
                            return@withContext com.phantom.tube.data.innertube.parser.InnerTubeHelpers.optimizeAvatarUrl(
                                match.value
                            )
                        }
                    }
                }
                val fullSlice = out.toString("UTF-8")
                val match = STREAM_AVATAR_URL_REGEX.find(fullSlice)
                if (match != null) {
                    return@withContext com.phantom.tube.data.innertube.parser.InnerTubeHelpers.optimizeAvatarUrl(
                        match.value
                    )
                }
            }
        } catch (_: Exception) {
        }
        return@withContext null
    }

    suspend fun fetchChannel(
        channelId: String,
        params: String? = null,
        continuation: String? = null
    ): ChannelProfile? = withContext(Dispatchers.IO) {
        try {
            var targetChannelId = channelId.trim()
            // If channelId is not a standard UC id and not a continuation, resolve by search
            if (continuation.isNullOrBlank() && !targetChannelId.startsWith("UC") && targetChannelId.isNotBlank()) {
                val searchRes = fetchFeedPage(query = targetChannelId)
                val foundId = searchRes.videos.firstOrNull { it.channelId.startsWith("UC") }?.channelId
                if (!foundId.isNullOrBlank()) {
                    targetChannelId = foundId
                }
            }

            val bodyJson = JSONObject().apply {
                put("context", createClientContext())
                if (!continuation.isNullOrBlank()) {
                    put("continuation", continuation)
                } else {
                    put("browseId", targetChannelId)
                    if (!params.isNullOrBlank()) {
                        put("params", params)
                    }
                }
            }

            val request = Request.Builder()
                .url(com.phantom.tube.core.security.PhantomNative.getBrowseEndpoint())
                .header("Content-Type", "application/json")
                .header("User-Agent", userAgent)
                .post(bodyJson.toString().toRequestBody(jsonMediaType))
                .build()

            val responseBody = httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                response.body?.string() ?: return@withContext null
            }
            return@withContext InnerTubeParser.parseChannelPage(responseBody, targetChannelId)
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }

    suspend fun fetchChannelVideosContinuation(
        continuation: String
    ): FeedResult = withContext(Dispatchers.IO) {
        try {
            val bodyJson = JSONObject().apply {
                put("context", createClientContext())
                put("continuation", continuation)
            }

            val request = Request.Builder()
                .url(com.phantom.tube.core.security.PhantomNative.getBrowseEndpoint())
                .header("Content-Type", "application/json")
                .header("User-Agent", userAgent)
                .post(bodyJson.toString().toRequestBody(jsonMediaType))
                .build()

            val responseBody = httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext FeedResult()
                response.body?.string() ?: return@withContext FeedResult()
            }
            return@withContext InnerTubeParser.parseChannelContinuation(responseBody)
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext FeedResult()
        }
    }

    suspend fun fetchDetailedSuggestions(query: String): List<com.phantom.tube.data.model.SuggestionItem> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@withContext emptyList()
        try {
            val region = regionCodeProvider?.invoke() ?: "GLOBAL"
            val lang = languageCodeProvider?.invoke() ?: "en"
            val gl = when (region.uppercase()) {
                "GLOBAL" -> "US"
                "US" -> "US"
                "JP" -> "JP"
                "KR" -> "KR"
                "GB" -> "GB"
                "ID" -> "ID"
                "ES" -> "ES"
                "RU" -> "RU"
                else -> region.uppercase()
            }
            val hl = when (lang.lowercase()) {
                "en" -> "en"
                "ja" -> "ja"
                "es" -> "es"
                "ru" -> "ru"
                "id" -> "id"
                else -> lang.lowercase()
            }
            val encoded = java.net.URLEncoder.encode(trimmed, "UTF-8")
            val endpoints = com.phantom.tube.core.security.PhantomNative.getSuggestEndpoints(encoded, hl, gl)

            for (url in endpoints) {
                try {
                    val request = Request.Builder()
                        .url(url)
                        .header("User-Agent", userAgent)
                        .header("Accept", "*/*")
                        .get()
                        .build()

                    val response = httpClient.newCall(request).execute()
                    response.use { resp ->
                        if (resp.isSuccessful) {
                            val bodyBytes = resp.body?.bytes()
                            if (bodyBytes != null && bodyBytes.isNotEmpty()) {
                                val parsed = InnerTubeParser.parseDetailedSuggestions(String(bodyBytes, Charsets.UTF_8))
                                if (parsed.isNotEmpty()) {
                                    return@withContext parsed
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Try next fallback endpoint
                }
            }

            emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun fetchSuggestions(query: String): List<String> = withContext(Dispatchers.IO) {
        return@withContext fetchDetailedSuggestions(query).map { it.query }
    }
}
