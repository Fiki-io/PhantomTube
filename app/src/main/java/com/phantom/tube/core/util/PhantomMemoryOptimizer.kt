package com.phantom.tube.core.util

import android.content.ComponentCallbacks2
import android.content.Context
import android.webkit.WebView
import coil.imageLoader
import com.phantom.tube.data.innertube.cache.ChannelAvatarCache
import com.phantom.tube.player.PhantomRenderEngine
import com.phantom.tube.player.StoryboardHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicLong

object PhantomMemoryOptimizer {

    private const val MIN_TRIM_INTERVAL_MS = 1500L
    private const val TRIM_LEVEL_COMPLETE = 80
    private const val TRIM_LEVEL_UI_HIDDEN = ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN

    private val optimizerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lastTrimTimestampMs = AtomicLong(0L)

    private val windowManagerInstance: Any? by lazy {
        runCatching {
            val clazz = Class.forName("android.view.WindowManagerGlobal")
            val getInstance = clazz.getDeclaredMethod("getInstance")
            getInstance.isAccessible = true
            getInstance.invoke(null)
        }.getOrNull()
    }

    private val windowManagerTrimMethod: Method? by lazy {
        runCatching {
            windowManagerInstance?.javaClass?.getDeclaredMethod(
                "trimMemory",
                Int::class.javaPrimitiveType
            )?.apply {
                isAccessible = true
            }
        }.getOrNull()
    }

    private val vmRuntimeInstance: Any? by lazy {
        runCatching {
            val clazz = Class.forName("dalvik.system.VMRuntime")
            val getRuntime = clazz.getDeclaredMethod("getRuntime")
            getRuntime.isAccessible = true
            getRuntime.invoke(null)
        }.getOrNull()
    }

    private val vmRequestGcMethod: Method? by lazy {
        runCatching {
            vmRuntimeInstance?.javaClass?.getDeclaredMethod("requestConcurrentGC")?.apply {
                isAccessible = true
            }
        }.getOrNull()
    }

    fun trimNow(
        context: Context,
        webView: WebView? = null,
        aggressiveGraphicsPurge: Boolean = false
    ) {
        val now = System.currentTimeMillis()
        val previous = lastTrimTimestampMs.get()
        if (!aggressiveGraphicsPurge && now - previous < MIN_TRIM_INTERVAL_MS) {
            return
        }
        lastTrimTimestampMs.set(now)

        val appContext = context.applicationContext

        if (webView != null) {
            webView.post {
                runCatching {
                    webView.clearCache(false)
                    webView.clearFormData()
                    webView.clearMatches()
                }
            }
        }

        optimizerScope.launch {
            runCatching {
                PhantomRenderEngine.clearCache()
                StoryboardHelper.clearCache()
                ChannelAvatarCache.trim()
                appContext.imageLoader.memoryCache?.let { cache ->
                    if (aggressiveGraphicsPurge) {
                        cache.clear()
                    } else {
                        appContext.imageLoader.trimMemory( ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW)
                    }
                }
            }

            runCatching {
                val trimLevel = if (aggressiveGraphicsPurge) TRIM_LEVEL_COMPLETE else TRIM_LEVEL_UI_HIDDEN
                windowManagerTrimMethod?.invoke(windowManagerInstance, trimLevel)
            }

            runCatching {
                if (vmRequestGcMethod != null) {
                    vmRequestGcMethod?.invoke(vmRuntimeInstance)
                } else {
                    System.runFinalization()
                    Runtime.getRuntime().gc()
                }
            }
        }
    }
}
