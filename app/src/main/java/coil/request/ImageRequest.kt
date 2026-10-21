package coil.request

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import coil.size.Precision

sealed class ImageResult {
    abstract val drawable: Drawable?
}

class SuccessResult(
    override val drawable: Drawable
) : ImageResult()

class ErrorResult(
    override val drawable: Drawable? = null,
    val throwable: Throwable
) : ImageResult()

class ImageRequest internal constructor(
    val context: Context,
    val data: Any?,
    val targetWidth: Int,
    val targetHeight: Int,
    val precision: Precision,
    val bitmapConfig: Bitmap.Config,
    val allowRgb565: Boolean,
    val allowHardware: Boolean
) {
    val urlString: String
        get() = data?.toString() ?: ""

    val cacheKey: String
        get() = if (targetWidth > 0 && targetHeight > 0) {
            "${urlString}#${targetWidth}x${targetHeight}"
        } else {
            urlString
        }

    class Builder(private val context: Context) {
        private var data: Any? = null
        private var targetWidth: Int = 0
        private var targetHeight: Int = 0
        private var precision: Precision = Precision.AUTOMATIC
        private var bitmapConfig: Bitmap.Config = Bitmap.Config.RGB_565
        private var allowRgb565: Boolean = true
        private var allowHardware: Boolean = true

        fun data(data: Any?): Builder = apply {
            this.data = data
        }

        fun size(width: Int, height: Int): Builder = apply {
            this.targetWidth = width
            this.targetHeight = height
        }

        fun size(size: Int): Builder = apply {
            this.targetWidth = size
            this.targetHeight = size
        }

        fun precision(precision: Precision): Builder = apply {
            this.precision = precision
        }

        fun bitmapConfig(config: Bitmap.Config): Builder = apply {
            this.bitmapConfig = config
        }

        fun allowRgb565(enable: Boolean): Builder = apply {
            this.allowRgb565 = enable
        }

        fun allowHardware(enable: Boolean): Builder = apply {
            this.allowHardware = enable
        }

        fun crossfade(@Suppress("UNUSED_PARAMETER") enable: Boolean): Builder = this

        fun build(): ImageRequest = ImageRequest(
            context = context,
            data = data,
            targetWidth = targetWidth,
            targetHeight = targetHeight,
            precision = precision,
            bitmapConfig = bitmapConfig,
            allowRgb565 = allowRgb565,
            allowHardware = allowHardware
        )
    }
}
