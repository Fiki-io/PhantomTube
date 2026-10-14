package com.phantom.tube.core.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.phantom.tube.R
import com.phantom.tube.core.security.PhantomNative
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

data class AppUpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val downloadUrl: String,
    val changelog: String,
    val minVersionCode: Int = 0,
    val isForceUpdate: Boolean = false
)

sealed class UpdateCheckResult {
    data class UpdateAvailable(val info: AppUpdateInfo) : UpdateCheckResult()
    object UpToDate : UpdateCheckResult()
    data class Error(val message: String) : UpdateCheckResult()
}

class UpdateManager(
    private val context: Context,
    private val httpClient: OkHttpClient
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
            val updateUrl = PhantomNative.getUpdateEndpoint()
            val request = Request.Builder()
                .url(updateUrl)
                .header("User-Agent", "Phantom-Updater/${getCurrentVersionName()}")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext UpdateCheckResult.Error("HTTP ${response.code}")
            }

            val body = response.body?.string() ?: return@withContext UpdateCheckResult.Error("Empty response")
            val json = JSONObject(body)

            val serverVersionCode = json.optInt("versionCode", 0)
            val serverVersionName = json.optString("versionName", "")
            val downloadUrl = json.optString("downloadUrl", "")
            val changelog = json.optString("changelog", "")
            val minVersionCode = json.optInt("minVersionCode", 0)
            val forceUpdate = json.optBoolean("forceUpdate", false)

            val currentCode = getCurrentVersionCode().toInt()

            // Apply policy to C++ engine
            PhantomNative.applyVersionPolicy(currentCode, minVersionCode, forceUpdate)

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
                        isForceUpdate = isForce
                    )
                )
            } else {
                UpdateCheckResult.UpToDate
            }
        } catch (e: Exception) {
            UpdateCheckResult.Error(e.message ?: context.getString(R.string.settings_update_check_failed_toast))
        }
    }

    suspend fun downloadApk(
        downloadUrl: String,
        onProgress: (Float) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(downloadUrl)
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null

            val body = response.body ?: return@withContext null
            val contentLength = body.contentLength()

            val targetDir = context.getExternalFilesDir(null) ?: context.cacheDir
            val targetFile = File(targetDir, "Phantom-Update.apk")
            if (targetFile.exists()) {
                targetFile.delete()
            }

            body.byteStream().use { input ->
                FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(8 * 1024)
                    var bytesRead: Int
                    var totalRead = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        if (contentLength > 0) {
                            val progress = totalRead.toFloat() / contentLength.toFloat()
                            withContext(Dispatchers.Main) {
                                onProgress(progress.coerceIn(0f, 1f))
                            }
                        }
                    }
                    output.flush()
                }
            }

            targetFile
        } catch (e: Exception) {
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
            } catch (e: Exception) {
                // Fallback to app details settings
                val fallbackIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallbackIntent)
            }
        }
    }

    fun promptInstall(apkFile: File) {
        try {
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
        } catch (e: Exception) {
            // Log or ignore
        }
    }
}
