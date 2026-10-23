package com.phantom.tube.data.sponsorblock

import com.phantom.tube.data.model.SponsorSegment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.util.concurrent.TimeUnit

class SponsorBlockClient(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()
) {
    suspend fun getSkipSegments(videoId: String): List<SponsorSegment> = withContext(Dispatchers.IO) {
        if (videoId.isBlank()) return@withContext emptyList()
        try {
            val url = com.phantom.tube.core.security.PhantomNative.getSponsorBlockRequestUrl(videoId)

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Phantom-Android/2.0")
                .get()
                .build()

            val body = httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext emptyList()
                }
                response.body?.string() ?: return@withContext emptyList()
            }
            val array = JSONArray(body)
            val segments = mutableListOf<SponsorSegment>()

            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val category = item.optString("category", "sponsor")
                val segmentArray = item.optJSONArray("segment") ?: continue
                val uuid = item.optString("UUID", "")

                if (segmentArray.length() >= 2) {
                    val start = segmentArray.optDouble(0, 0.0).toFloat()
                    val end = segmentArray.optDouble(1, 0.0).toFloat()
                    if (end > start) {
                        segments.add(SponsorSegment(category = category, startSecond = start, endSecond = end, uuid = uuid))
                    }
                }
            }
            return@withContext segments
        } catch (e: Exception) {
            // Ignore 404 or connection issues gracefully
            return@withContext emptyList()
        }
    }
}
