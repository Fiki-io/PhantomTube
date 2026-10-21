package com.phantom.tube

import android.app.Application
import android.content.ComponentCallbacks2
import android.graphics.Bitmap
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.imageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.phantom.tube.core.crash.PhantomCrashHandler
import com.phantom.tube.core.database.PhantomDatabase
import com.phantom.tube.core.security.PhantomNative
import com.phantom.tube.data.innertube.InnerTubeClient
import com.phantom.tube.data.repository.PhantomRepository
import com.phantom.tube.data.sponsorblock.SponsorBlockClient
import com.phantom.tube.data.settings.PhantomPreferences
import com.phantom.tube.data.innertube.cache.ChannelAvatarCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.Cache
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class PhantomApp : Application(), ImageLoaderFactory {

    companion object {
        @Volatile
        var sharedClientInstance: OkHttpClient? = null
            private set
    }

    lateinit var database: PhantomDatabase
        private set

    lateinit var preferences: PhantomPreferences
        private set

    lateinit var repository: PhantomRepository
        private set

    val sharedHttpClient: OkHttpClient by lazy {
        val dispatcher = Dispatcher().apply {
            maxRequests = 16
            maxRequestsPerHost = 6
        }
        OkHttpClient.Builder()
            .dispatcher(dispatcher)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .connectionPool(ConnectionPool(6, 2, TimeUnit.MINUTES))
            .cache(Cache(cacheDir.resolve("http_cache"), 24L * 1024 * 1024))
            .build()
            .also { sharedClientInstance = it }
    }

    val updateManager: com.phantom.tube.core.update.UpdateManager by lazy {
        com.phantom.tube.core.update.UpdateManager(this, sharedHttpClient)
    }

    override fun onCreate() {
        super.onCreate()
        PhantomCrashHandler.install(this)
        PhantomNative.verifySecurity(this)
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

        // Preload avatar cache with subscribed channels from local DB
        CoroutineScope(Dispatchers.IO).launch {
            try {
                database.subscriptionDao().getAllSubscriptions().collect { subs ->
                    subs.forEach { sub ->
                        if (sub.channelAvatarUrl.isNotBlank()) {
                            ChannelAvatarCache.put(
                                sub.channelId,
                                sub.channelTitle,
                                sub.channelAvatarUrl
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore background DB error
            }
        }
    }

    override fun newImageLoader(): ImageLoader {
        val maxHeapBytes = Runtime.getRuntime().maxMemory()
        val targetCacheBytes = (maxHeapBytes * 0.08).toLong().coerceIn(4L * 1024 * 1024, 10L * 1024 * 1024)

        return ImageLoader.Builder(this)
            .okHttpClient(sharedHttpClient)
            .bitmapConfig(Bitmap.Config.RGB_565)
            .allowRgb565(true)
            .bitmapFactoryMaxParallelism(2)
            .bitmapFactoryExifOrientationPolicy(coil.decode.ExifOrientationPolicy.IGNORE)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizeBytes(targetCacheBytes.toInt())
                    .strongReferencesEnabled(true)
                    .weakReferencesEnabled(false)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(40L * 1024 * 1024)
                    .build()
            }
            .respectCacheHeaders(false)
            .allowHardware(true)
            .crossfade(false)
            .build()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW || level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            try {
                this.imageLoader.memoryCache?.clear()
                com.phantom.tube.player.PhantomRenderEngine.clearCache()
                com.phantom.tube.player.StoryboardHelper.clearCache()
                ChannelAvatarCache.trim()
            } catch (_: Exception) {
            }
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        try {
            this.imageLoader.memoryCache?.clear()
            com.phantom.tube.player.PhantomRenderEngine.clearCache()
            com.phantom.tube.player.StoryboardHelper.clearCache()
            ChannelAvatarCache.clear()
        } catch (_: Exception) {
        }
    }
}
