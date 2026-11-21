package com.phantom.tube.data.download

import android.content.Context
import com.phantom.tube.PhantomApp
import com.phantom.tube.R
import com.phantom.tube.core.config.PhantomConfig
import com.phantom.tube.core.network.HttpFormBody
import com.phantom.tube.core.network.HttpRequest
import com.phantom.tube.core.network.PhantomHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object PhantomDownloadEngine {

    private val client: PhantomHttpClient by lazy {
        (PhantomApp.sharedClientInstance?.newBuilder() ?: PhantomHttpClient.Builder())
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .build()
    }

    fun getAudioQualities(context: Context? = null): List<AvailableQuality> = listOf(
        AvailableQuality(
            "mp3_320",
            "320 kbps",
            context?.getString(R.string.download_desc_mp3_320) ?: "MP3 • 320 kbps",
            "audio",
            "320"
        ),
        AvailableQuality(
            "mp3_256",
            "256 kbps",
            context?.getString(R.string.download_desc_mp3_256) ?: "MP3 • 256 kbps",
            "audio",
            "256"
        ),
        AvailableQuality(
            "mp3_128",
            "128 kbps",
            context?.getString(R.string.download_desc_mp3_128) ?: "MP3 • 128 kbps",
            "audio",
            "128"
        )
    )

    fun getVideoQualities(context: Context? = null): List<AvailableQuality> = listOf(
        AvailableQuality(
            "mp4_1080",
            "1080p Full HD",
            context?.getString(R.string.download_desc_mp4_1080) ?: "MP4 • 1080p",
            "video",
            "1080"
        ),
        AvailableQuality(
            "mp4_720",
            "720p HD",
            context?.getString(R.string.download_desc_mp4_720) ?: "MP4 • 720p",
            "video",
            "720"
        ),
        AvailableQuality(
            "mp4_360",
            "360p",
            context?.getString(R.string.download_desc_mp4_360) ?: "MP4 • 360p",
            "video",
            "360"
        ),
        AvailableQuality(
            "mp4_240",
            "240p",
            context?.getString(R.string.download_desc_mp4_240) ?: "MP4 • 240p",
            "video",
            "240"
        ),
        AvailableQuality(
            "mp4_144",
            "144p",
            context?.getString(R.string.download_desc_mp4_144) ?: "MP4 • 144p",
            "video",
            "144"
        )
    )

    suspend fun convertMedia(
        videoId: String,
        quality: AvailableQuality,
        context: Context? = null
    ): Result<DownloadResult> = withContext(Dispatchers.IO) {
        var lastError: Exception? = null
        val defaultConvertError = context?.getString(R.string.download_error_convert)
            ?: "Failed to process video conversion"

        val referer = PhantomConfig.getDownloadReferer()
        val origin = PhantomConfig.getDownloadOrigin()
        val userAgent = PhantomConfig.getUserAgent()

        // Auto-retry hingga 3 percobaan
        for (attempt in 1..3) {
            try {
                // Step 1: Request conversion token key
                val keyUrl = PhantomConfig.getDownloadKeyEndpoint(videoId)
                if (keyUrl.isBlank()) {
                    return@withContext Result.failure(Exception(defaultConvertError))
                }
                val keyReq = HttpRequest.Builder()
                    .url(keyUrl)
                    .header("Referer", referer)
                    .header("Origin", origin)
                    .header("User-Agent", userAgent)
                    .build()

                val tokenKey = client.newCall(keyReq).execute().use { resp ->
                    val body = resp.body?.string() ?: ""
                    val json = JSONObject(body)
                    json.optString("key", "")
                }

                if (tokenKey.isBlank()) {
                    if (attempt < 3) {
                        delay(1000)
                        continue
                    }
                    return@withContext Result.failure(Exception(defaultConvertError))
                }

                // Step 2: Submit conversion request with target format parameters
                val isAudio = quality.formatType == "audio"
                val formatParam = if (isAudio) "mp3" else "mp4"
                val audioBitrate = if (isAudio) quality.qualityValue else "128"
                val videoQuality = if (isAudio) "720" else quality.qualityValue

                val formBody = HttpFormBody.Builder()
                    .add("link", "https://youtu.be/$videoId")
                    .add("format", formatParam)
                    .add("audioBitrate", audioBitrate)
                    .add("videoQuality", videoQuality)
                    .add("filenameStyle", "pretty")
                    .add("vCodec", "h264")
                    .build()

                val convUrl = PhantomConfig.getDownloadConverterEndpoint()
                if (convUrl.isBlank()) {
                    return@withContext Result.failure(Exception(defaultConvertError))
                }
                val convReq = HttpRequest.Builder()
                    .url(convUrl)
                    .header("Referer", referer)
                    .header("Origin", origin)
                    .header("key", tokenKey)
                    .header("User-Agent", userAgent)
                    .post(formBody)
                    .build()

                val convJson = client.newCall(convReq).execute().use { resp ->
                    val body = resp.body?.string() ?: ""
                    JSONObject(body)
                }

                val downloadUrl = convJson.optString("url", "")
                val filename = convJson.optString("filename", "Phantom_${quality.qualityValue}.$formatParam")

                if (downloadUrl.isNotBlank()) {
                    return@withContext Result.success(
                        DownloadResult(
                            url = downloadUrl,
                            filename = filename,
                            mimeType = if (isAudio) "audio/mpeg" else "video/mp4"
                        )
                    )
                } else {
                    val errMsg = convJson.optString("errorMsg", "")
                    if (errMsg.contains("progress", ignoreCase = true) && attempt < 3) {
                        delay(1500)
                        continue
                    }
                    lastError = Exception(errMsg.ifBlank { defaultConvertError })
                }
            } catch (e: Exception) {
                lastError = e
                if (attempt < 3) {
                    delay(1000)
                }
            }
        }

        Result.failure(lastError ?: Exception(defaultConvertError))
    }
}
