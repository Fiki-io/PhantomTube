package com.phantom.tube.data.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.phantom.tube.PhantomApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import com.phantom.tube.R
import com.phantom.tube.core.security.PhantomNative
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.Locale
import java.util.concurrent.TimeUnit

object PhantomDownloader {

    private const val CHANNEL_ID = "phantom_downloads"

    private val downloadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _downloadStates = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val downloadStates: StateFlow<Map<String, DownloadState>> = _downloadStates.asStateFlow()

    fun getDownloadState(videoId: String): DownloadState {
        return _downloadStates.value[videoId] ?: DownloadState.Idle
    }

    fun resetDownloadState(videoId: String) {
        _downloadStates.value = _downloadStates.value - videoId
    }

    fun enqueueDownload(
        context: Context,
        video: com.phantom.tube.data.model.VideoItem,
        quality: AvailableQuality,
        repository: com.phantom.tube.data.repository.PhantomRepository
    ) {
        val videoId = video.id
        val appContext = context.applicationContext

        _downloadStates.value = _downloadStates.value + (videoId to DownloadState.Converting(quality.label))

        downloadScope.launch {
            withContext(Dispatchers.Main) {
                android.widget.Toast.makeText(
                    appContext,
                    appContext.getString(R.string.download_prep_toast, quality.label),
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }

            val convertResult = PhantomDownloadEngine.convertMedia(videoId, quality, appContext)
            convertResult.onSuccess { dlRes ->
                _downloadStates.value = _downloadStates.value + (videoId to DownloadState.Downloading(quality.label, 0, 0L, 0L))

                val downloadRes = downloadMedia(
                    context = appContext,
                    downloadUrl = dlRes.url,
                    suggestedFileName = dlRes.filename,
                    mimeType = dlRes.mimeType,
                    videoTitle = video.title.ifBlank { dlRes.filename },
                    onProgress = { percent, downloaded, total ->
                        _downloadStates.value = _downloadStates.value + (videoId to DownloadState.Downloading(quality.label, percent, downloaded, total))
                    }
                )

                downloadRes.onSuccess { fileInfo ->
                    _downloadStates.value = _downloadStates.value + (videoId to DownloadState.Success(
                        fileName = fileInfo.fileName,
                        filePath = fileInfo.filePath,
                        fileUri = fileInfo.fileUri
                    ))

                    val entity = com.phantom.tube.core.database.DownloadEntity(
                        id = "${videoId}_${quality.formatType}_${quality.qualityValue}",
                        videoId = videoId,
                        title = video.title.ifBlank { fileInfo.fileName },
                        channelTitle = video.channelTitle,
                        thumbnailUrl = video.thumbnailUrl,
                        format = if (quality.formatType == "audio") "MP3" else "MP4",
                        qualityLabel = quality.label,
                        fileName = fileInfo.fileName,
                        fileUri = fileInfo.fileUri,
                        filePath = fileInfo.filePath,
                        fileSize = fileInfo.fileSize
                    )
                    repository.saveDownload(entity)

                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(
                            appContext,
                            appContext.getString(R.string.download_saved_toast, fileInfo.filePath),
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                }.onFailure { dlErr ->
                    val msg = dlErr.message ?: appContext.getString(R.string.download_error_general)
                    _downloadStates.value = _downloadStates.value + (videoId to DownloadState.Error(msg))
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(appContext, msg, android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            }.onFailure { convErr ->
                val msg = convErr.message ?: appContext.getString(R.string.download_error_convert)
                _downloadStates.value = _downloadStates.value + (videoId to DownloadState.Error(msg))
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(appContext, msg, android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private val httpClient: OkHttpClient by lazy {
        (PhantomApp.sharedClientInstance?.newBuilder() ?: OkHttpClient.Builder())
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
    }

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.downloads_title),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.downloads_title)
                setShowBadge(false)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(channel)
        }
    }

    suspend fun downloadMedia(
        context: Context,
        downloadUrl: String,
        suggestedFileName: String,
        mimeType: String,
        videoTitle: String,
        notificationId: Int = (System.currentTimeMillis() % 100000).toInt(),
        onProgress: (percent: Int, downloadedBytes: Long, totalBytes: Long) -> Unit
    ): Result<DownloadedFileInfo> = withContext(Dispatchers.IO) {
        createNotificationChannel(context)
        val isAudio = mimeType.contains("audio", ignoreCase = true)
        val ext = if (isAudio) ".mp3" else ".mp4"

        var cleanName = suggestedFileName.trim()
            .replace("[\\\\/:*?\"<>|]".toRegex(), "_")
        if (!cleanName.endsWith(ext, ignoreCase = true)) {
            cleanName += ext
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

        fun updateNotification(percent: Int, downloaded: Long, total: Long) {
            if (notificationManager == null) return
            try {
                val downloadedMb = String.format(Locale.US, "%.1f", downloaded / (1024f * 1024f))
                val totalMb = if (total > 0) String.format(Locale.US, "%.1f MB", total / (1024f * 1024f)) else ""
                val progressText = if (percent >= 0) "$percent% ($downloadedMb MB / $totalMb)" else "$downloadedMb MB"

                val notif = Notification.Builder(context, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.stat_sys_download)
                    .setContentTitle(context.getString(R.string.download_progress_title, videoTitle.ifBlank { cleanName }))
                    .setContentText(progressText)
                    .setProgress(100, percent.coerceIn(0, 100), percent < 0)
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .build()
                notificationManager.notify(notificationId, notif)
            } catch (_: Exception) {}
        }

        fun showCompletedNotification(fileUri: Uri, displayPath: String) {
            if (notificationManager == null) return
            try {
                val openIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(fileUri, mimeType)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                val pendingIntent = PendingIntent.getActivity(
                    context,
                    notificationId,
                    openIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val notif = Notification.Builder(context, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.stat_sys_download_done)
                    .setContentTitle(context.getString(R.string.download_success_title))
                    .setContentText(cleanName)
                    .setSubText(displayPath)
                    .setOngoing(false)
                    .setAutoCancel(true)
                    .setContentIntent(pendingIntent)
                    .build()
                notificationManager.notify(notificationId, notif)
            } catch (_: Exception) {}
        }

        var lastError: Exception? = null

        val referer = PhantomNative.getDownloadReferer()
        val userAgent = PhantomNative.getUserAgent()

        val headerSets = listOf(
            mapOf("Referer" to referer, "User-Agent" to userAgent)
        )

        for (attempt in 1..3) {
            var response: okhttp3.Response? = null
            for (headers in headerSets) {
                try {
                    val req = Request.Builder().url(downloadUrl).apply {
                        headers.forEach { (k, v) -> header(k, v) }
                    }.build()
                    val res = httpClient.newCall(req).execute()
                    if (res.isSuccessful) {
                        response = res
                        break
                    } else {
                        res.close()
                    }
                } catch (_: Exception) {}
            }

            if (response == null || !response.isSuccessful) {
                lastError = Exception(context.getString(R.string.download_error_general))
                if (attempt < 3) {
                    delay(1200)
                    continue
                }
                return@withContext Result.failure(lastError)
            }

            val body = response.body
            if (body == null) {
                lastError = Exception(context.getString(R.string.download_error_general))
                if (attempt < 3) {
                    delay(1000)
                    continue
                }
                return@withContext Result.failure(lastError)
            }

            val totalBytes = body.contentLength()

            try {
                // MediaStore API untuk Android 10+ (API 29+) - 100% tanpa butuh izin storage
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val relativeSubfolder = if (isAudio) "Music/Phantom" else "Download/Phantom"
                    val contentValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, cleanName)
                        put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                        put(MediaStore.MediaColumns.RELATIVE_PATH, relativeSubfolder)
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                    }

                    val collection = if (isAudio) {
                        MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    } else {
                        MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    }

                    val uri = context.contentResolver.insert(collection, contentValues)
                        ?: throw Exception(context.getString(R.string.download_error_general))

                    var isSuccess = false
                    try {
                        val outputStream: OutputStream = context.contentResolver.openOutputStream(uri)
                            ?: throw Exception(context.getString(R.string.download_error_general))

                        var totalRead = 0L
                        var lastReport = 0L
                        val buffer = ByteArray(32 * 1024)
                        var bytesRead: Int

                        outputStream.use { out ->
                            body.byteStream().use { inStream ->
                                while (inStream.read(buffer).also { bytesRead = it } != -1) {
                                    out.write(buffer, 0, bytesRead)
                                    totalRead += bytesRead
                                    val now = System.currentTimeMillis()
                                    if (now - lastReport > 250 || totalRead == totalBytes) {
                                        lastReport = now
                                        val percent = if (totalBytes > 0) ((totalRead * 100) / totalBytes).toInt() else -1
                                        onProgress(percent, totalRead, totalBytes)
                                        updateNotification(percent, totalRead, totalBytes)
                                    }
                                }
                                out.flush()
                            }
                        }

                        // Lepaskan pending agar langsung terdeteksi semua aplikasi eksternal
                        contentValues.clear()
                        contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                        context.contentResolver.update(uri, contentValues, null, null)

                        val displayPath = "$relativeSubfolder/$cleanName"
                        showCompletedNotification(uri, displayPath)
                        isSuccess = true

                        return@withContext Result.success(
                            DownloadedFileInfo(
                                fileName = cleanName,
                                fileUri = uri.toString(),
                                filePath = displayPath,
                                fileSize = totalRead,
                                mimeType = mimeType
                            )
                        )
                    } finally {
                        if (!isSuccess) {
                            try {
                                context.contentResolver.delete(uri, null, null)
                            } catch (_: Exception) {}
                        }
                    }
                } else {
                    // Fallback untuk Android 8.0 - 9.0 (API 26 - 28)
                    val baseDir = Environment.getExternalStoragePublicDirectory(
                        if (isAudio) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_DOWNLOADS
                    )
                    val targetDir = File(baseDir, "Phantom")
                    if (!targetDir.exists()) targetDir.mkdirs()

                    val targetFile = File(targetDir, cleanName)
                    var isSuccess = false
                    try {
                        val outputStream = FileOutputStream(targetFile)

                        var totalRead = 0L
                        var lastReport = 0L
                        val buffer = ByteArray(32 * 1024)
                        var bytesRead: Int

                        outputStream.use { out ->
                            body.byteStream().use { inStream ->
                                while (inStream.read(buffer).also { bytesRead = it } != -1) {
                                    out.write(buffer, 0, bytesRead)
                                    totalRead += bytesRead
                                    val now = System.currentTimeMillis()
                                    if (now - lastReport > 250 || totalRead == totalBytes) {
                                        lastReport = now
                                        val percent = if (totalBytes > 0) ((totalRead * 100) / totalBytes).toInt() else -1
                                        onProgress(percent, totalRead, totalBytes)
                                        updateNotification(percent, totalRead, totalBytes)
                                    }
                                }
                                out.flush()
                            }
                        }

                        val fileUri = Uri.fromFile(targetFile)
                        MediaScannerConnection.scanFile(
                            context,
                            arrayOf(targetFile.absolutePath),
                            arrayOf(mimeType),
                            null
                        )

                        val displayPath = targetFile.absolutePath
                        showCompletedNotification(fileUri, displayPath)
                        isSuccess = true

                        return@withContext Result.success(
                            DownloadedFileInfo(
                                fileName = cleanName,
                                fileUri = fileUri.toString(),
                                filePath = displayPath,
                                fileSize = totalRead,
                                mimeType = mimeType
                            )
                        )
                    } finally {
                        if (!isSuccess && targetFile.exists()) {
                            try {
                                targetFile.delete()
                            } catch (_: Exception) {}
                        }
                    }
                }
            } catch (e: Exception) {
                lastError = e
                if (attempt < 3) {
                    delay(1000)
                    continue
                }
            } finally {
                try {
                    response.close()
                } catch (_: Exception) {}
            }
        }

        try {
            notificationManager?.cancel(notificationId)
        } catch (_: Exception) {}
        Result.failure(lastError ?: Exception(context.getString(R.string.download_error_general)))
    }

    fun openDownloadedFile(context: Context, fileUriString: String, mimeType: String) {
        try {
            val uri = Uri.parse(fileUriString)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(intent, context.getString(R.string.downloads_chooser_play)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            android.widget.Toast.makeText(
                context,
                context.getString(R.string.downloads_toast_no_external),
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }
}
