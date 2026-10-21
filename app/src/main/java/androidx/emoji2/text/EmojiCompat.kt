package androidx.emoji2.text

import android.graphics.Canvas
import android.graphics.Paint
import android.text.style.ReplacementSpan
import android.view.inputmethod.EditorInfo

/**
 * Lightweight no-op compatibility stub for [androidx.emoji2.text.EmojiCompat] and [EmojiSpan].
 *
 * Compose's `ui-text` module unconditionally references `EmojiCompat.isConfigured()` during
 * paragraph layout (`EmojiCompatStatus.android.kt`). When the full `androidx.emoji2:emoji2`
 * artifact is excluded from Gradle dependencies to reduce binary size, this class satisfies
 * class resolution at runtime and reports `isConfigured() = false`, instructing Compose and
 * Android's Canvas text pipeline to render system fonts and emojis natively without the
 * downloadable font metadata overhead.
 */
class EmojiCompat private constructor() {

    abstract class InitCallback {
        open fun onInitialized() {}
        open fun onFailed(throwable: Throwable?) {}
    }

    fun getLoadState(): Int = LOAD_STATE_DEFAULT

    fun process(charSequence: CharSequence?): CharSequence? = charSequence

    fun registerInitCallback(callback: InitCallback) {}

    fun unregisterInitCallback(callback: InitCallback) {}

    fun getEmojiStart(charSequence: CharSequence, offset: Int): Int = -1

    fun getEmojiEnd(charSequence: CharSequence, offset: Int): Int = -1

    fun updateEditorInfo(outAttrs: EditorInfo) {}

    companion object {
        const val LOAD_STATE_DEFAULT = 3
        const val LOAD_STATE_LOADING = 0
        const val LOAD_STATE_SUCCEEDED = 1
        const val LOAD_STATE_FAILED = 2

        private val INSTANCE = EmojiCompat()

        @JvmStatic
        fun isConfigured(): Boolean = false

        @JvmStatic
        fun get(): EmojiCompat = INSTANCE
    }
}

abstract class EmojiSpan : ReplacementSpan() {
    override fun getSize(
        paint: Paint,
        text: CharSequence?,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?
    ): Int = 0

    override fun draw(
        canvas: Canvas,
        text: CharSequence?,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: Paint
    ) {}
}
