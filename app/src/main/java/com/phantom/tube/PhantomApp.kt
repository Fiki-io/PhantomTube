package com.phantom.tube

import android.app.Application
import android.content.ComponentCallbacks2
import android.graphics.Bitmap
import com.phantom.tube.core.crash.PhantomCrashHandler
import com.phantom.tube.core.database.PhantomDatabase
import com.phantom.tube.core.image.ImageDiskCache
import com.phantom.tube.core.image.ImageMemoryCache
import com.phantom.tube.core.image.PhantomImageLoader
import com.phantom.tube.core.image.PhantomImageLoaderFactory
import com.phantom.tube.core.image.phantomImageLoader
import com.phantom.tube.core.network.HttpCache
import com.phantom.tube.core.network.HttpDispatcher
import com.phantom.tube.core.network.PhantomHttpClient
import com.phantom.tube.data.innertube.InnerTubeClient
import com.phantom.tube.data.repository.PhantomRepository
import com.phantom.tube.data.sponsorblock.SponsorBlockClient
import com.phantom.tube.data.settings.PhantomPreferences
import com.phantom.tube.data.innertube.cache.ChannelAvatarCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class PhantomApp : Application(), PhantomImageLoaderFactory {

    companion object {
        @Volatile
        var sharedClientInstance: PhantomHttpClient? = null
            private set
    }

    lateinit var database: PhantomDatabase
        private set

    lateinit var preferences: PhantomPreferences
        private set

    lateinit var repository: PhantomRepository
        private set

    val sharedHttpClient: PhantomHttpClient by lazy {
        val dispatcher = HttpDispatcher().apply {
            maxRequests = 8
            maxRequestsPerHost = 4
        }
        PhantomHttpClient.Builder()
            .dispatcher(dispatcher)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .cache(HttpCache(cacheDir.resolve("http_cache"), 8L * 1024 * 1024))
            .build()
            .also { sharedClientInstance = it }
    }

    val updateManager: com.phantom.tube.core.update.UpdateManager by lazy {
        com.phantom.tube.core.update.UpdateManager(this, sharedHttpClient)
    }

    override fun onCreate() {
        super.onCreate()
        PhantomCrashHandler.install(this)
        database = PhantomDatabase.getInstance(this)
        preferences = PhantomPreferences(this)
        com.phantom.tube.player.PhantomRenderEngine.init(sharedHttpClient)
        val innerTube = InnerTubeClient(httpClient = sharedHttpClient)
        ChannelAvatarCache.init(innerTube)
        repository = PhantomRepository(
            innerTubeClient = innerTube,
            sponsorBlockClient = SponsorBlockClient(httpClient = sharedHttpClient),
            watchHistoryDao = database.watchHistoryDao(),
            favoriteDao = database.favoriteDao(),
            searchHistoryDao = database.searchHistoryDao(),
            subscriptionDao = database.subscriptionDao(),
            downloadDao = database.downloadDao(),
            preferences = preferences
        )

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val subs = database.subscriptionDao().getAllSubscriptions().first()
                subs.forEach { sub ->
                    if (sub.channelAvatarUrl.isNotBlank()) {
                        ChannelAvatarCache.put(
                            sub.channelId,
                            sub.channelTitle,
                            sub.channelAvatarUrl
                        )
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    override fun newImageLoader(): PhantomImageLoader {
        val maxMem = Runtime.getRuntime().maxMemory()
        val targetCacheBytes = (maxMem / 10L)
            .coerceIn(8L * 1024L * 1024L, 24L * 1024L * 1024L)
            .toInt()

        return PhantomImageLoader.Builder(this)
            .httpClient(sharedHttpClient)
            .bitmapConfig(Bitmap.Config.RGB_565)
            .maxParallelism(2)
            .memoryCache {
                ImageMemoryCache.Builder()
                    .maxSizeBytes(targetCacheBytes)
                    .build()
            }
            .diskCache {
                ImageDiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(16L * 1024 * 1024)
                    .build()
            }
            .build()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        try {
            this.phantomImageLoader.trimMemory(level)
            if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL || level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
                com.phantom.tube.core.util.PhantomMemoryOptimizer.trimNow(
                    context = this,
                    aggressiveGraphicsPurge = level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN
                )
            }
        } catch (_: Exception) {
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        try {
            com.phantom.tube.core.util.PhantomMemoryOptimizer.trimNow(
                context = this,
                aggressiveGraphicsPurge = true
            )
        } catch (_: Exception) {
        }
    }
}
