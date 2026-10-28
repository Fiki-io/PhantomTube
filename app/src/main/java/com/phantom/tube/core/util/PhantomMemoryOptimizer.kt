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
import java.util.concurrent.atomic.AtomicLong

object PhantomMemoryOptimizer {

    private const val MIN_TRIM_INTERVAL_MS = 1500L

    private val optimizerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lastTrimTimestampMs = AtomicLong(0L)

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
                ChannelAvatarCache.trim()
                if (aggressiveGraphicsPurge) {
                    PhantomRenderEngine.clearCache()
                    StoryboardHelper.clearCache()
                    appContext.imageLoader.trimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW)
                }
            }

            if (aggressiveGraphicsPurge) {
                runCatching {
                    System.gc()
                    System.runFinalization()
                }
            }
        }
    }
}
