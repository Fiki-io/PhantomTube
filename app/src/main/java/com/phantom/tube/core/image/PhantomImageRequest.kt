package com.phantom.tube.core.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable

sealed class PhantomImageResult {
    abstract val drawable: Drawable?
}

class ImageSuccessResult(
    override val drawable: Drawable
) : PhantomImageResult()

class ImageErrorResult(
    override val drawable: Drawable? = null,
    val throwable: Throwable
) : PhantomImageResult()

class PhantomImageRequest internal constructor(
    val context: Context,
    val data: Any?,
    val targetWidth: Int,
    val targetHeight: Int,
    val bitmapConfig: Bitmap.Config,
    val allowRgb565: Boolean
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
        private var bitmapConfig: Bitmap.Config = Bitmap.Config.RGB_565
        private var allowRgb565: Boolean = true

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

        fun bitmapConfig(config: Bitmap.Config): Builder = apply {
            this.bitmapConfig = config
        }

        fun allowRgb565(enable: Boolean): Builder = apply {
            this.allowRgb565 = enable
        }

        fun build(): PhantomImageRequest = PhantomImageRequest(
            context = context,
            data = data,
            targetWidth = targetWidth,
            targetHeight = targetHeight,
            bitmapConfig = bitmapConfig,
            allowRgb565 = allowRgb565
        )
    }
}
