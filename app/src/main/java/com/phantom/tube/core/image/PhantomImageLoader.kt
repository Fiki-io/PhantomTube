package com.phantom.tube.core.image

import android.content.ComponentCallbacks2
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.util.LruCache
import com.phantom.tube.core.network.HttpRequest
import com.phantom.tube.core.network.PhantomHttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Lightweight internal image loader built on Android [BitmapFactory], [LruCache],
 * SHA-256 disk caching, and [PhantomHttpClient], with zero third-party image dependencies.
 */
interface PhantomImageLoaderFactory {
    fun newImageLoader(): PhantomImageLoader
}

class PhantomImageLoader internal constructor(
    private val context: Context,
    private val httpClient: PhantomHttpClient,
    val memoryCache: ImageMemoryCache?,
    private val diskCache: ImageDiskCache?,
    private val defaultConfig: Bitmap.Config,
    maxParallelism: Int
) {
    private val decodeSemaphore = Semaphore(maxParallelism.coerceIn(1, 3))
    private val diskPruneScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val isPruningDisk = AtomicBoolean(false)
    private val diskWriteCount = AtomicInteger(0)

    private val decodeTempBuffer = object : ThreadLocal<ByteArray>() {
        override fun initialValue(): ByteArray = ByteArray(16 * 1024)
    }

    private val bitmapRamCache = object : LruCache<String, Bitmap>(
        (memoryCache?.maxSizeBytes ?: (8 * 1024 * 1024)).coerceIn(1024 * 1024, 32 * 1024 * 1024)
    ) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return value.allocationByteCount.coerceAtLeast(1)
        }
    }

    init {
        memoryCache?.onClear = {
            bitmapRamCache.evictAll()
        }
    }

    fun trimMemory(level: Int) {
        when {
            level >= ComponentCallbacks2.TRIM_MEMORY_COMPLETE ||
                level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND ||
                level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> {
                bitmapRamCache.evictAll()
                val cacheDir = diskCache?.directory ?: File(context.cacheDir, "image_cache")
                val maxBytes = diskCache?.maxSizeBytes ?: (16L * 1024 * 1024)
                pruneDiskCacheAsync(cacheDir, maxBytes)
            }
            level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> {
                bitmapRamCache.trimToSize((bitmapRamCache.maxSize() / 4).coerceAtLeast(1))
            }
            level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> {
                bitmapRamCache.trimToSize((bitmapRamCache.maxSize() / 2).coerceAtLeast(1))
            }
            level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE -> {
                bitmapRamCache.trimToSize(((bitmapRamCache.maxSize() * 3) / 4).coerceAtLeast(1))
            }
        }
    }

    internal fun getFromMemory(key: String): Bitmap? {
        if (key.isBlank()) return null
        val cached = bitmapRamCache.get(key)
        return if (cached != null && !cached.isRecycled) cached else null
    }

    suspend fun execute(request: PhantomImageRequest): PhantomImageResult = withContext(Dispatchers.IO) {
        val url = request.urlString.trim()
        if (url.isBlank()) {
            return@withContext ImageErrorResult(throwable = IllegalArgumentException("Blank image URL"))
        }

        val key = request.cacheKey
        getFromMemory(key)?.let { cachedBmp ->
            return@withContext ImageSuccessResult(BitmapDrawable(context.resources, cachedBmp))
        }

        try {
            val diskFile = resolveDiskFile(url) ?: return@withContext ImageErrorResult(
                throwable = IllegalStateException("Empty image stream")
            )

            val isAvatarUrl = (url.contains("ggpht.com") || url.contains("googleusercontent.com")) && !url.contains("=w")
            val maxW = when {
                request.targetWidth > 0 -> request.targetWidth.coerceAtMost(2048)
                isAvatarUrl -> 88
                else -> 320
            }
            val maxH = when {
                request.targetHeight > 0 -> request.targetHeight.coerceAtMost(2048)
                isAvatarUrl -> 88
                else -> 180
            }

            val bitmap = decodeSemaphore.withPermit {
                decodeBitmapFromFile(
                    file = diskFile,
                    targetWidth = maxW,
                    targetHeight = maxH,
                    request = request
                )
            } ?: return@withContext ImageErrorResult(throwable = IllegalStateException("Decode failed"))

            if (!bitmap.isRecycled) {
                bitmapRamCache.put(key, bitmap)
            }
            ImageSuccessResult(BitmapDrawable(context.resources, bitmap))
        } catch (e: Throwable) {
            ImageErrorResult(throwable = e)
        }
    }

    private fun resolveDiskFile(url: String): File? {
        val cacheDir = diskCache?.directory ?: File(context.cacheDir, "image_cache")
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
        val hash = sha256Hex(url)
        val diskFile = File(cacheDir, hash)
        if (diskFile.exists() && diskFile.length() > 64L) {
            return diskFile
        }

        val req = HttpRequest.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/126.0.0.0 Mobile Safari/537.36")
            .get()
            .build()

        val tempFile = File(cacheDir, "$hash.${Thread.currentThread().id}.tmp")
        var completed = false
        try {
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val stream = resp.body?.byteStream() ?: return null
                var totalWritten = 0L
                FileOutputStream(tempFile).use { out ->
                    val buf = ByteArray(16 * 1024)
                    var read: Int
                    while (stream.read(buf).also { read = it } != -1) {
                        out.write(buf, 0, read)
                        totalWritten += read
                    }
                    out.flush()
                }
                if (totalWritten <= 0L) return null
                if (!tempFile.renameTo(diskFile)) {
                    tempFile.copyTo(diskFile, overwrite = true)
                    tempFile.delete()
                }
                completed = diskFile.exists() && diskFile.length() > 0L
                if (completed) {
                    val maxBytes = diskCache?.maxSizeBytes ?: (16L * 1024 * 1024)
                    pruneDiskCacheIfNeeded(cacheDir, maxBytes)
                    return diskFile
                }
            }
        } finally {
            if (!completed && tempFile.exists()) {
                tempFile.delete()
            }
        }
        return null
    }

    private fun pruneDiskCacheIfNeeded(cacheDir: File, maxSizeBytes: Long) {
        if (diskWriteCount.incrementAndGet() % 25 != 0) return
        pruneDiskCacheAsync(cacheDir, maxSizeBytes)
    }

    private fun pruneDiskCacheAsync(cacheDir: File, maxSizeBytes: Long) {
        if (!isPruningDisk.compareAndSet(false, true)) return
        diskPruneScope.launch {
            try {
                pruneDiskCache(cacheDir, maxSizeBytes)
            } finally {
                isPruningDisk.set(false)
            }
        }
    }

    private fun pruneDiskCache(cacheDir: File, maxSizeBytes: Long) {
        try {
            val files = cacheDir.listFiles()?.filter { !it.name.endsWith(".tmp") } ?: return
            var totalSize = files.sumOf { it.length() }
            if (totalSize <= maxSizeBytes) return

            val sortedFiles = files.sortedBy { it.lastModified() }
            val targetSize = (maxSizeBytes * 0.75).toLong()

            for (file in sortedFiles) {
                val len = file.length()
                if (file.delete()) {
                    totalSize -= len
                    if (totalSize <= targetSize) break
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun decodeBitmapFromFile(
        file: File,
        targetWidth: Int,
        targetHeight: Int,
        request: PhantomImageRequest
    ): Bitmap? {
        val tempBuf = decodeTempBuffer.get()
        val path = file.absolutePath
        val boundsOpts = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
            inTempStorage = tempBuf
        }
        BitmapFactory.decodeFile(path, boundsOpts)
        val srcW = boundsOpts.outWidth
        val srcH = boundsOpts.outHeight
        if (srcW <= 0 || srcH <= 0) return null

        val effW = if (targetWidth > 0) targetWidth else 320
        val effH = if (targetHeight > 0) targetHeight else 180

        var sampleSize = 1
        if (srcW > effW || srcH > effH) {
            val halfW = srcW / 2
            val halfH = srcH / 2
            while ((halfW / sampleSize) >= effW && (halfH / sampleSize) >= effH) {
                sampleSize *= 2
            }
        }

        val mime = boundsOpts.outMimeType?.lowercase().orEmpty()
        val needsAlpha = !request.allowRgb565 ||
            request.bitmapConfig == Bitmap.Config.ARGB_8888 ||
            mime == "image/png" ||
            mime == "image/webp"
        val resolvedConfig = if (needsAlpha) Bitmap.Config.ARGB_8888 else defaultConfig

        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = resolvedConfig
            inTempStorage = tempBuf
            inDither = resolvedConfig == Bitmap.Config.RGB_565
        }
        val decoded = BitmapFactory.decodeFile(path, decodeOpts) ?: return null

        if (decoded.width > (effW * 1.25f).toInt() && decoded.height > (effH * 1.25f).toInt()) {
            val scale = minOf(effW.toFloat() / decoded.width.toFloat(), effH.toFloat() / decoded.height.toFloat())
            val scaledW = (decoded.width * scale).toInt().coerceAtLeast(1)
            val scaledH = (decoded.height * scale).toInt().coerceAtLeast(1)
            if (scaledW < decoded.width && scaledH < decoded.height) {
                return try {
                    val scaled = Bitmap.createScaledBitmap(decoded, scaledW, scaledH, true)
                    if (scaled !== decoded) {
                        decoded.recycle()
                    }
                    scaled
                } catch (_: Throwable) {
                    decoded
                }
            }
        }
        return decoded
    }

    private fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder(digest.size * 2)
        for (b in digest) {
            sb.append(String.format("%02x", b))
        }
        return sb.toString()
    }

    class Builder(private val context: Context) {
        private var httpClient: PhantomHttpClient? = null
        private var memoryCache: ImageMemoryCache? = null
        private var diskCache: ImageDiskCache? = null
        private var defaultConfig: Bitmap.Config = Bitmap.Config.RGB_565
        private var maxParallelism: Int = 2

        fun httpClient(client: PhantomHttpClient): Builder = apply {
            this.httpClient = client
        }

        fun bitmapConfig(config: Bitmap.Config): Builder = apply {
            this.defaultConfig = config
        }

        fun maxParallelism(maxParallelism: Int): Builder = apply {
            this.maxParallelism = maxParallelism
        }

        fun memoryCache(initializer: () -> ImageMemoryCache): Builder = apply {
            this.memoryCache = initializer()
        }

        fun diskCache(initializer: () -> ImageDiskCache): Builder = apply {
            this.diskCache = initializer()
        }

        fun build(): PhantomImageLoader = PhantomImageLoader(
            context = context.applicationContext,
            httpClient = httpClient ?: PhantomHttpClient(),
            memoryCache = memoryCache,
            diskCache = diskCache,
            defaultConfig = defaultConfig,
            maxParallelism = maxParallelism
        )
    }

    companion object {
        @Volatile
        private var instance: PhantomImageLoader? = null

        fun get(context: Context): PhantomImageLoader {
            return instance ?: synchronized(this) {
                instance ?: run {
                    val app = context.applicationContext
                    val created = if (app is PhantomImageLoaderFactory) {
                        app.newImageLoader()
                    } else {
                        Builder(app).build()
                    }
                    instance = created
                    created
                }
            }
        }
    }
}

val Context.phantomImageLoader: PhantomImageLoader
    get() = PhantomImageLoader.get(this)
