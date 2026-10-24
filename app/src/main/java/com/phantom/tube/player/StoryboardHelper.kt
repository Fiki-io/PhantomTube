package com.phantom.tube.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.util.LruCache
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class StoryboardLevel(
    val width: Int,
    val height: Int,
    val totalCount: Int,
    val cols: Int,
    val rows: Int,
    val stepMs: Int,
    val nameTemplate: String,
    val sigh: String?,
    val levelIndex: Int
)

data class StoryboardFrame(
    val sheetUrl: String,
    val frameWidth: Int,
    val frameHeight: Int,
    val cropX: Int,
    val cropY: Int,
    val sheetWidth: Int = frameWidth,
    val sheetHeight: Int = frameHeight
)

data class StoryboardData(
    val baseUrlTemplate: String,
    val levels: List<StoryboardLevel>
) {
    fun getFrame(timeSeconds: Float, durationSeconds: Float): StoryboardFrame? {
        if (levels.isEmpty()) return null

        // Pick compact level (width 80..160) to minimize sprite sheet bitmap RAM footprint
        val level = levels.filter { it.width in 80..160 }.maxByOrNull { it.width }
            ?: levels.firstOrNull { it.width >= 80 }
            ?: levels.last()

        val intervalSec = if (level.stepMs > 0) {
            level.stepMs / 1000f
        } else {
            (durationSeconds / level.totalCount.coerceAtLeast(1)).coerceAtLeast(1f)
        }

        val frameIndex = (timeSeconds / intervalSec.coerceAtLeast(0.1f))
            .toInt()
            .coerceIn(0, (level.totalCount - 1).coerceAtLeast(0))

        val framesPerSheet = (level.cols * level.rows).coerceAtLeast(1)
        val sheetIndex = frameIndex / framesPerSheet
        val frameInSheet = frameIndex % framesPerSheet

        val col = frameInSheet % level.cols
        val row = frameInSheet / level.cols

        val sheetName = level.nameTemplate.replace("\$M", sheetIndex.toString())
        var sheetUrl = baseUrlTemplate
            .replace("\$L", level.levelIndex.toString())
            .replace("\$N", sheetName)

        if (!level.sigh.isNullOrBlank()) {
            sheetUrl = if (sheetUrl.contains("?")) "$sheetUrl&sigh=${level.sigh}" else "$sheetUrl?sigh=${level.sigh}"
        }

        return StoryboardFrame(
            sheetUrl = sheetUrl,
            frameWidth = level.width,
            frameHeight = level.height,
            cropX = col * level.width,
            cropY = row * level.height,
            sheetWidth = (level.width * level.cols).coerceAtLeast(level.width),
            sheetHeight = (level.height * level.rows).coerceAtLeast(level.height)
        )
    }
}

object StoryboardHelper {

    private const val MAX_FRAME_CACHE_BYTES = 512 * 1024 // 512 KB max for cropped preview frames
    private const val MAX_SHEET_CACHE_BYTES = 2 * 1024 * 1024 // 2 MB max for sprite sheets

    private val frameCache = object : LruCache<String, Bitmap>(MAX_FRAME_CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount.coerceAtLeast(1)
    }

    private val sheetCache = object : LruCache<String, Bitmap>(MAX_SHEET_CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount.coerceAtLeast(1)
    }

    fun clearCache() {
        frameCache.evictAll()
        sheetCache.evictAll()
    }

    fun parse(spec: String): StoryboardData? {
        if (spec.isBlank()) return null
        val parts = spec.split("|")
        if (parts.size < 2) return null

        val baseUrl = parts[0]
        val levels = mutableListOf<StoryboardLevel>()

        for (i in 1 until parts.size) {
            val tokens = parts[i].split("#")
            if (tokens.size >= 7) {
                try {
                    val w = tokens[0].toInt()
                    val h = tokens[1].toInt()
                    val count = tokens[2].toInt()
                    val cols = tokens[3].toInt()
                    val rows = tokens[4].toInt()
                    val stepMs = tokens[5].toInt()
                    val nameTemplate = tokens[6]
                    val sigh = if (tokens.size > 7) tokens[7] else null

                    levels.add(
                        StoryboardLevel(
                            width = w,
                            height = h,
                            totalCount = count,
                            cols = cols,
                            rows = rows,
                            stepMs = stepMs,
                            nameTemplate = nameTemplate,
                            sigh = sigh,
                            levelIndex = i - 1
                        )
                    )
                } catch (e: Exception) {
                    // Ignore malformed level block
                }
            }
        }

        return if (levels.isNotEmpty()) StoryboardData(baseUrl, levels) else null
    }

    suspend fun loadFrameBitmap(context: Context, frame: StoryboardFrame): Bitmap? = withContext(Dispatchers.IO) {
        val cacheKey = "${frame.sheetUrl}#${frame.cropX}_${frame.cropY}_${frame.frameWidth}_${frame.frameHeight}"
        frameCache.get(cacheKey)?.let { if (!it.isRecycled) return@withContext it }

        var sheetBitmap = sheetCache.get(frame.sheetUrl)
        if (sheetBitmap == null || sheetBitmap.isRecycled) {
            try {
                val request = ImageRequest.Builder(context)
                    .data(frame.sheetUrl)
                    .size(frame.sheetWidth, frame.sheetHeight)
                    .bitmapConfig(Bitmap.Config.RGB_565)
                    .allowHardware(false) // Required for Bitmap.createBitmap cropping
                    .build()
                val result = (context.imageLoader.execute(request) as? SuccessResult)?.drawable
                sheetBitmap = (result as? BitmapDrawable)?.bitmap
                if (sheetBitmap != null && !sheetBitmap.isRecycled) {
                    sheetCache.put(frame.sheetUrl, sheetBitmap)
                }
            } catch (e: Exception) {
                return@withContext null
            }
        }

        if (sheetBitmap == null || sheetBitmap.isRecycled) return@withContext null

        try {
            val scaleX = if (frame.sheetWidth > 0) sheetBitmap.width.toFloat() / frame.sheetWidth.toFloat() else 1f
            val scaleY = if (frame.sheetHeight > 0) sheetBitmap.height.toFloat() / frame.sheetHeight.toFloat() else 1f

            val scaledW = (frame.frameWidth * scaleX).toInt().coerceAtLeast(1)
            val scaledH = (frame.frameHeight * scaleY).toInt().coerceAtLeast(1)
            val scaledX = (frame.cropX * scaleX).toInt()
            val scaledY = (frame.cropY * scaleY).toInt()

            val safeX = scaledX.coerceIn(0, (sheetBitmap.width - scaledW).coerceAtLeast(0))
            val safeY = scaledY.coerceIn(0, (sheetBitmap.height - scaledH).coerceAtLeast(0))
            val safeW = scaledW.coerceAtMost(sheetBitmap.width - safeX)
            val safeH = scaledH.coerceAtMost(sheetBitmap.height - safeY)

            if (safeW > 0 && safeH > 0) {
                val cropped = Bitmap.createBitmap(sheetBitmap, safeX, safeY, safeW, safeH)
                frameCache.put(cacheKey, cropped)
                return@withContext cropped
            }
        } catch (e: Exception) {
            // Ignore crop OOM/bounds error
        }

        return@withContext null
    }
}
