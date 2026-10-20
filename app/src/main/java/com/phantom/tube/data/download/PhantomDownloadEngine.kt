package com.phantom.tube.data.download

import com.phantom.tube.PhantomApp
import com.phantom.tube.core.security.PhantomNative
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object PhantomDownloadEngine {

    private val client: OkHttpClient by lazy {
        (PhantomApp.sharedClientInstance?.newBuilder() ?: OkHttpClient.Builder())
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .build()
    }

    fun getAudioQualities(): List<AvailableQuality> = listOf(
        AvailableQuality("mp3_320", "320 kbps", "MP3 • Kualitas Studio Terbaik", "audio", "320"),
        AvailableQuality("mp3_256", "256 kbps", "MP3 • Kualitas Tinggi", "audio", "256"),
        AvailableQuality("mp3_128", "128 kbps", "MP3 • Standar Hemat Memori", "audio", "128")
    )

    fun getVideoQualities(): List<AvailableQuality> = listOf(
        AvailableQuality("mp4_1080", "1080p Full HD", "MP4 • Resolusi Tinggi 1080p", "video", "1080"),
        AvailableQuality("mp4_720", "720p HD", "MP4 • Resolusi Jernih 720p", "video", "720"),
        AvailableQuality("mp4_360", "360p", "MP4 • Standar Hemat Kuota", "video", "360"),
        AvailableQuality("mp4_240", "240p", "MP4 • Kualitas Rendah 240p", "video", "240"),
        AvailableQuality("mp4_144", "144p", "MP4 • Sangat Hemat Ruang 144p", "video", "144")
    )

    suspend fun convertMedia(
        videoId: String,
        quality: AvailableQuality
    ): Result<DownloadResult> = withContext(Dispatchers.IO) {
        var lastError: Exception? = null

        val referer = PhantomNative.getDownloadReferer()
        val origin = PhantomNative.getDownloadOrigin()
        val userAgent = PhantomNative.getUserAgent()

        // Auto-retry hingga 3 percobaan
        for (attempt in 1..3) {
            try {
                // Step 1: Minta sanity token key dari C++ native engine
                val keyUrl = PhantomNative.getDownloadKeyEndpoint(videoId)
                if (keyUrl.isBlank()) {
                    return@withContext Result.failure(Exception("Modul pengunduh terkunci atau tidak tersedia"))
                }
                val keyReq = Request.Builder()
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
                    return@withContext Result.failure(Exception("Gagal menginisialisasi konverter video"))
                }

                // Step 2: Kirim permintaan konversi dengan parameter format ke C++ endpoint
                val isAudio = quality.formatType == "audio"
                val formatParam = if (isAudio) "mp3" else "mp4"
                val audioBitrate = if (isAudio) quality.qualityValue else "128"
                val videoQuality = if (isAudio) "720" else quality.qualityValue

                val formBody = FormBody.Builder()
                    .add("link", "https://youtu.be/$videoId")
                    .add("format", formatParam)
                    .add("audioBitrate", audioBitrate)
                    .add("videoQuality", videoQuality)
                    .add("filenameStyle", "pretty")
                    .add("vCodec", "h264")
                    .build()

                val convUrl = PhantomNative.getDownloadConverterEndpoint()
                if (convUrl.isBlank()) {
                    return@withContext Result.failure(Exception("Modul pengunduh terkunci atau tidak tersedia"))
                }
                val convReq = Request.Builder()
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
                    lastError = Exception(errMsg.ifBlank { "Server sedang memproses konversi video, silakan coba sesaat lagi" })
                }
            } catch (e: Exception) {
                lastError = e
                if (attempt < 3) {
                    delay(1000)
                }
            }
        }

        Result.failure(lastError ?: Exception("Gagal mengonversi video ke format unduhan"))
    }
}
