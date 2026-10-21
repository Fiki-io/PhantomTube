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
    private val decodeSemaphore = Semaphore(maxParallelism.coerceAtLeast(2))

    private val bitmapRamCache = object : LruCache<String, Bitmap>(
        (memoryCache?.maxSizeBytes ?: (6 * 1024 * 1024)).coerceAtLeast(2 * 1024 * 1024)
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

            val bitmap = decodeSemaphore.withPermit {
                decodeBitmap(
                    bytes = rawBytes,
                    targetWidth = request.targetWidth,
                    targetHeight = request.targetHeight,
                    config = if (request.allowRgb565) Bitmap.Config.RGB_565 else request.bitmapConfig
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

    private fun readFromDiskOrNetwork(url: String): ByteArray? {
        val cacheDir = diskCache?.directory ?: File(context.cacheDir, "image_cache")
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
        val diskFile = File(cacheDir, sha256Hex(url))
        if (diskFile.exists() && diskFile.length() > 64L) {
            try {
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
            } catch (_: Exception) {
            }
            bytes
        }
    }

    private fun decodeBitmap(
        bytes: ByteArray,
        targetWidth: Int,
        targetHeight: Int,
        config: Bitmap.Config
    ): Bitmap? {
        if (targetWidth > 0 && targetHeight > 0) {
            val boundsOpts = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOpts)
            val srcW = boundsOpts.outWidth
            val srcH = boundsOpts.outHeight
            var sampleSize = 1
            if (srcW > targetWidth || srcH > targetHeight) {
                val halfW = srcW / 2
                val halfH = srcH / 2
                while ((halfW / sampleSize) >= targetWidth && (halfH / sampleSize) >= targetHeight) {
                    sampleSize *= 2
                }
            }
            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = config
            }
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts)
        } else {
            val decodeOpts = BitmapFactory.Options().apply {
                inPreferredConfig = config
            }
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts)
        }
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
