package coil.memory

import android.content.Context

class MemoryCache internal constructor(val maxSizeBytes: Int) {

    var onClear: (() -> Unit)? = null

    fun clear() {
        onClear?.invoke()
    }

    class Builder(@Suppress("UNUSED_PARAMETER") private val context: Context) {
        private var maxSizeBytes: Int = 6 * 1024 * 1024

        fun maxSizeBytes(size: Int): Builder = apply {
            this.maxSizeBytes = size
        }

        fun strongReferencesEnabled(@Suppress("UNUSED_PARAMETER") enabled: Boolean): Builder = this

        fun weakReferencesEnabled(@Suppress("UNUSED_PARAMETER") enabled: Boolean): Builder = this

        fun build(): MemoryCache = MemoryCache(maxSizeBytes)
    }
}
