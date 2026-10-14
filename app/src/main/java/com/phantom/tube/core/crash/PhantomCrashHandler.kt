package com.phantom.tube.core.crash

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.phantom.tube.ui.screens.crash.CrashActivity
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.system.exitProcess

class PhantomCrashHandler private constructor(private val context: Context) : Thread.UncaughtExceptionHandler {

    private val defaultHandler: Thread.UncaughtExceptionHandler? = Thread.getDefaultUncaughtExceptionHandler()

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            val stringWriter = StringWriter()
            throwable.printStackTrace(PrintWriter(stringWriter))
            val stackTrace = stringWriter.toString()

            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

            val packageInfo = try {
                context.packageManager.getPackageInfo(context.packageName, 0)
            } catch (e: Exception) {
                null
            }
            val versionName = packageInfo?.versionName ?: "2.0.0"
            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo?.longVersionCode ?: 2L
            } else {
                @Suppress("DEPRECATION")
                packageInfo?.versionCode?.toLong() ?: 2L
            }

            val crashReport = buildString {
                appendLine("=== PHANTOM CRASH LOG ===")
                appendLine("Timestamp   : $timestamp")
                appendLine("Device      : ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
                appendLine("Android OS  : ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                appendLine("App Version : $versionName ($versionCode)")
                appendLine("Thread      : ${thread.name} (ID: ${thread.id})")
                appendLine("Exception   : ${throwable.javaClass.name}")
                appendLine("Error Msg   : ${throwable.message ?: "No message details"}")
                appendLine("=========================")
                appendLine()
                appendLine("STACK TRACE:")
                appendLine(stackTrace)
            }

            Log.e("PhantomCrashHandler", crashReport)

            val intent = Intent(context, CrashActivity::class.java).apply {
                putExtra(CrashActivity.EXTRA_CRASH_REPORT, crashReport)
                putExtra(CrashActivity.EXTRA_ERROR_MESSAGE, throwable.message ?: throwable.javaClass.simpleName)
                putExtra(CrashActivity.EXTRA_STACK_TRACE, stackTrace)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }

            context.startActivity(intent)

            // Terminate the crashed process cleanly
            android.os.Process.killProcess(android.os.Process.myPid())
            exitProcess(10)
        } catch (e: Exception) {
            e.printStackTrace()
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    companion object {
        fun install(application: Application) {
            val handler = PhantomCrashHandler(application.applicationContext)
            Thread.setDefaultUncaughtExceptionHandler(handler)
        }
    }
}
