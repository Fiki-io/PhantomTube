package com.phantom.tube.core.image

import java.io.File

class ImageMemoryCache internal constructor(val maxSizeBytes: Int) {

    var onClear: (() -> Unit)? = null

    fun clear() {
        onClear?.invoke()
    }

    class Builder {
        private var maxSizeBytes: Int = 6 * 1024 * 1024

        fun maxSizeBytes(size: Int): Builder = apply {
            this.maxSizeBytes = size
        }

        fun build(): ImageMemoryCache = ImageMemoryCache(maxSizeBytes)
    }
}

class ImageDiskCache internal constructor(
    val directory: File?,
    val maxSizeBytes: Long
) {
    class Builder {
        private var directory: File? = null
        private var maxSizeBytes: Long = 40L * 1024 * 1024

        fun directory(directory: File): Builder = apply {
            this.directory = directory
        }

        fun maxSizeBytes(size: Long): Builder = apply {
            this.maxSizeBytes = size
        }

        fun build(): ImageDiskCache = ImageDiskCache(directory, maxSizeBytes)
    }
}
