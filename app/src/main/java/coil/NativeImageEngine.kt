package coil

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.util.LruCache
import coil.decode.ExifOrientationPolicy
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.ErrorResult
import coil.request.ImageRequest
import coil.request.ImageResult
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest

interface ImageLoaderFactory {
    fun newImageLoader(): ImageLoader
}

class ImageLoader internal constructor(
    private val context: Context,
    private val httpClient: OkHttpClient,
    val memoryCache: MemoryCache?,
    private val diskCache: DiskCache?,
    private val defaultConfig: Bitmap.Config,
    maxParallelism: Int
) {
    private val decodeSemaphore = Semaphore(maxParallelism.coerceIn(1, 2))

    private val decodeTempBuffer = object : ThreadLocal<ByteArray>() {
        override fun initialValue(): ByteArray = ByteArray(16 * 1024)
    }

    private val bitmapRamCache = object : LruCache<String, Bitmap>(
        (memoryCache?.maxSizeBytes ?: (2 * 1024 * 1024)).coerceIn(1024 * 1024, 2560 * 1024)
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
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN ||
            level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW
        ) {
            bitmapRamCache.evictAll()
            val cacheDir = diskCache?.directory ?: File(context.cacheDir, "image_cache")
            val maxBytes = diskCache?.maxSizeBytes ?: (16L * 1024 * 1024)
            pruneDiskCache(cacheDir, maxBytes)
        } else if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE) {
            bitmapRamCache.trimToSize(bitmapRamCache.maxSize() / 2)
        }
    }

    internal fun getFromMemory(key: String): Bitmap? {
        if (key.isBlank()) return null
        val cached = bitmapRamCache.get(key)
        return if (cached != null && !cached.isRecycled) cached else null
    }

    suspend fun execute(request: ImageRequest): ImageResult = withContext(Dispatchers.IO) {
        val url = request.urlString.trim()
        if (url.isBlank()) {
            return@withContext ErrorResult(throwable = IllegalArgumentException("Blank image URL"))
        }

        val key = request.cacheKey
        getFromMemory(key)?.let { cachedBmp ->
            return@withContext SuccessResult(BitmapDrawable(context.resources, cachedBmp))
        }

        try {
            val rawBytes = readFromDiskOrNetwork(url) ?: return@withContext ErrorResult(
                throwable = IllegalStateException("Empty image bytes")
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
                decodeBitmap(
                    bytes = rawBytes,
                    targetWidth = maxW,
                    targetHeight = maxH,
                    config = Bitmap.Config.RGB_565
                )
            } ?: return@withContext ErrorResult(throwable = IllegalStateException("Decode failed"))

            if (!bitmap.isRecycled) {
                bitmapRamCache.put(key, bitmap)
            }
            SuccessResult(BitmapDrawable(context.resources, bitmap))
        } catch (e: Throwable) {
            ErrorResult(throwable = e)
        }
    }

    private val diskWriteCount = java.util.concurrent.atomic.AtomicInteger(0)

    private fun readFromDiskOrNetwork(url: String): ByteArray? {
        val cacheDir = diskCache?.directory ?: File(context.cacheDir, "image_cache")
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
        val diskFile = File(cacheDir, sha256Hex(url))
        if (diskFile.exists() && diskFile.length() > 64L) {
            try {
                diskFile.setLastModified(System.currentTimeMillis())
                return diskFile.readBytes()
            } catch (_: Exception) {
            }
        }

        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/126.0.0.0 Mobile Safari/537.36")
            .get()
            .build()

        return httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val bytes = resp.body?.bytes() ?: return null
            if (bytes.isEmpty()) return null
            try {
                diskFile.writeBytes(bytes)
                val maxBytes = diskCache?.maxSizeBytes ?: (16L * 1024 * 1024)
                pruneDiskCacheIfNeeded(cacheDir, maxBytes)
            } catch (_: Exception) {
            }
            bytes
        }
    }

    private fun pruneDiskCacheIfNeeded(cacheDir: File, maxSizeBytes: Long) {
        if (diskWriteCount.incrementAndGet() % 25 != 0) return
        pruneDiskCache(cacheDir, maxSizeBytes)
    }

    private fun pruneDiskCache(cacheDir: File, maxSizeBytes: Long) {
        try {
            val files = cacheDir.listFiles() ?: return
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

    private fun decodeBitmap(
        bytes: ByteArray,
        targetWidth: Int,
        targetHeight: Int,
        config: Bitmap.Config
    ): Bitmap? {
        val tempBuf = decodeTempBuffer.get()
        val boundsOpts = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
            inTempStorage = tempBuf
        }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOpts)
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

        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = config
            inTempStorage = tempBuf
            inDither = true
        }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts) ?: return null

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
        private var httpClient: OkHttpClient? = null
        private var memoryCache: MemoryCache? = null
        private var diskCache: DiskCache? = null
        private var defaultConfig: Bitmap.Config = Bitmap.Config.RGB_565
        private var maxParallelism: Int = 2

        fun okHttpClient(client: OkHttpClient): Builder = apply {
            this.httpClient = client
        }

        fun bitmapConfig(config: Bitmap.Config): Builder = apply {
            this.defaultConfig = config
        }

        fun allowRgb565(@Suppress("UNUSED_PARAMETER") enable: Boolean): Builder = this

        fun bitmapFactoryMaxParallelism(maxParallelism: Int): Builder = apply {
            this.maxParallelism = maxParallelism
        }

        fun bitmapFactoryExifOrientationPolicy(
            @Suppress("UNUSED_PARAMETER") policy: ExifOrientationPolicy
        ): Builder = this

        fun memoryCache(initializer: () -> MemoryCache): Builder = apply {
            this.memoryCache = initializer()
        }

        fun diskCache(initializer: () -> DiskCache): Builder = apply {
            this.diskCache = initializer()
        }

        fun respectCacheHeaders(@Suppress("UNUSED_PARAMETER") enable: Boolean): Builder = this

        fun allowHardware(@Suppress("UNUSED_PARAMETER") enable: Boolean): Builder = this

        fun crossfade(@Suppress("UNUSED_PARAMETER") enable: Boolean): Builder = this

        fun build(): ImageLoader = ImageLoader(
            context = context.applicationContext,
            httpClient = httpClient ?: OkHttpClient(),
            memoryCache = memoryCache,
            diskCache = diskCache,
            defaultConfig = defaultConfig,
            maxParallelism = maxParallelism
        )
    }

    companion object {
        @Volatile
        private var instance: ImageLoader? = null

        fun get(context: Context): ImageLoader {
            return instance ?: synchronized(this) {
                instance ?: run {
                    val app = context.applicationContext
                    val created = if (app is ImageLoaderFactory) {
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

val Context.imageLoader: ImageLoader
    get() = ImageLoader.get(this)
