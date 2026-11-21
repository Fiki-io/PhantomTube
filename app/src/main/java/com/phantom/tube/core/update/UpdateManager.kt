package com.phantom.tube.core.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.phantom.tube.core.config.PhantomConfig
import com.phantom.tube.core.network.HttpRequest
import com.phantom.tube.core.network.PhantomHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

data class AppUpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val downloadUrl: String,
    val changelog: String,
    val minVersionCode: Int = 0,
    val isForceUpdate: Boolean = false,
    val sha256: String = ""
)

sealed class UpdateCheckResult {
    data class UpdateAvailable(val info: AppUpdateInfo) : UpdateCheckResult()
    object UpToDate : UpdateCheckResult()
    data class Error(val message: String) : UpdateCheckResult()
}

class UpdateManager(
    private val context: Context,
    private val httpClient: PhantomHttpClient
) {

    fun getCurrentVersionName(): String {
        return try {
            val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            pInfo.versionName ?: "2.0.0"
        } catch (e: Exception) {
            "2.0.0"
        }
    }

    fun getCurrentVersionCode(): Long {
        return try {
            val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pInfo.versionCode.toLong()
            }
        } catch (e: Exception) {
            2L
        }
    }

    suspend fun checkForUpdate(): UpdateCheckResult = withContext(Dispatchers.IO) {
        try {
            val updateUrl = PhantomConfig.getUpdateEndpoint()
            val request = HttpRequest.Builder()
                .url(updateUrl)
                .header("User-Agent", "Phantom-Updater/${getCurrentVersionName()}")
                .build()

            val body = httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext UpdateCheckResult.Error("HTTP ${response.code}")
                }
                response.body?.string() ?: return@withContext UpdateCheckResult.Error("Empty response")
            }
            val json = JSONObject(body)

            val serverVersionCode = json.optInt("versionCode", 0)
            val serverVersionName = json.optString("versionName", "")
            val downloadUrl = json.optString("downloadUrl", "")
            val changelog = json.optString("changelog", "")
            val minVersionCode = json.optInt("minVersionCode", 0)
            val forceUpdate = json.optBoolean("forceUpdate", false)
            val sha256 = json.optString("sha256", "").trim()

            val currentCode = getCurrentVersionCode().toInt()

            // Apply remote version lock policy
            PhantomConfig.applyVersionPolicy(currentCode, minVersionCode, forceUpdate)

            val isForce = forceUpdate || (minVersionCode > 0 && currentCode < minVersionCode)
            val isUpdateAvailable = (serverVersionCode > currentCode) || isForce

            if (isUpdateAvailable && downloadUrl.isNotBlank()) {
                UpdateCheckResult.UpdateAvailable(
                    AppUpdateInfo(
                        versionCode = serverVersionCode,
                        versionName = serverVersionName,
                        downloadUrl = downloadUrl,
                        changelog = changelog,
                        minVersionCode = minVersionCode,
                        isForceUpdate = isForce,
                        sha256 = sha256
                    )
                )
            } else {
                UpdateCheckResult.UpToDate
            }
        } catch (e: Exception) {
            UpdateCheckResult.Error(e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName)
        }
    }

    suspend fun downloadApk(
        downloadUrl: String,
        expectedSha256: String = "",
        onProgress: (Float) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        try {
            val parsedUri = Uri.parse(downloadUrl)
            if (!parsedUri.scheme.equals("https", ignoreCase = true)) {
                return@withContext null
            }

            val request = HttpRequest.Builder()
                .url(downloadUrl)
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null

                val body = response.body ?: return@withContext null
                val contentLength = body.contentLength()

                val targetDir = context.getExternalFilesDir(null) ?: context.cacheDir
                val targetFile = File(targetDir, "Phantom-Update.apk")
                val tempFile = File(targetDir, "Phantom-Update.apk.tmp")
                if (tempFile.exists()) {
                    tempFile.delete()
                }

                var completed = false
                try {
                    val normalizedExpectedHash = expectedSha256.trim()
                    val digest = if (normalizedExpectedHash.isNotBlank()) {
                        java.security.MessageDigest.getInstance("SHA-256")
                    } else {
                        null
                    }
                    body.byteStream().use { input ->
                        FileOutputStream(tempFile).use { output ->
                            val buffer = ByteArray(16 * 1024)
                            var bytesRead: Int
                            var totalRead = 0L
                            var lastProgress = -1f
                            var lastEmitMs = 0L

                            if (contentLength <= 0L) {
                                withContext(Dispatchers.Main) {
                                    onProgress(-1f)
                                }
                            }

                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                output.write(buffer, 0, bytesRead)
                                digest?.update(buffer, 0, bytesRead)
                                totalRead += bytesRead
                                if (contentLength > 0L) {
                                    val progress = (totalRead.toFloat() / contentLength.toFloat()).coerceIn(0f, 1f)
                                    val now = System.currentTimeMillis()
                                    if (progress - lastProgress >= 0.02f || now - lastEmitMs >= 100L || progress >= 1f) {
                                        lastProgress = progress
                                        lastEmitMs = now
                                        withContext(Dispatchers.Main) {
                                            onProgress(progress)
                                        }
                                    }
                                }
                            }
                            output.flush()
                            if (totalRead <= 0L || (contentLength > 0L && totalRead < contentLength)) {
                                return@withContext null
                            }
                        }
                    }

                    if (digest != null) {
                        val actualHex = digest.digest().joinToString("") { byte ->
                            "%02x".format(byte)
                        }
                        if (!actualHex.equals(normalizedExpectedHash, ignoreCase = true)) {
                            return@withContext null
                        }
                    }

                    if (targetFile.exists()) {
                        targetFile.delete()
                    }
                    if (!tempFile.renameTo(targetFile)) {
                        tempFile.copyTo(targetFile, overwrite = true)
                        tempFile.delete()
                    }
                    completed = targetFile.exists() && targetFile.length() > 0L
                    if (completed) targetFile else null
                } finally {
                    if (!completed && tempFile.exists()) {
                        tempFile.delete()
                    }
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    fun canInstallApks(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun openInstallPermissionSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (_: Exception) {
                val fallbackIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                runCatching { context.startActivity(fallbackIntent) }
            }
        }
    }

    fun promptInstall(apkFile: File): Boolean {
        return try {
            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                apkFile
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }
}
