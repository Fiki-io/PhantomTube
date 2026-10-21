package coil.disk

import java.io.File

class DiskCache internal constructor(
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

        fun build(): DiskCache = DiskCache(directory, maxSizeBytes)
    }
}
