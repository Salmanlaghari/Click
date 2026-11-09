package com.click.browser.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.webkit.WebView

/**
 * LRU in-memory cache of scaled tab thumbnails for the visual tab switcher.
 *
 * Design for smoothness (Prince: "smooth chale"):
 * - Thumbnails are captured ONCE per rendered page (never continuously), then
 *   bounded to 360x800 RGB_565 so the grid scrolls without jank.
 * - Max 24 entries; eldest dropped silently (no manual recycle — a recycled
 *   bitmap still referenced by a visible card would crash on draw).
 * - Incognito tabs are NEVER captured (privacy): the switcher shows a
 *   lock placeholder for them instead.
 */
object TabThumbnailStore {
    private const val MAX_ENTRIES = 24
    private const val THUMB_WIDTH = 360
    private const val THUMB_HEIGHT = 800

    private val cache = object : LinkedHashMap<String, Bitmap>(MAX_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?): Boolean {
            return size > MAX_ENTRIES
        }
    }

    @Synchronized
    fun put(tabId: String, bitmap: Bitmap) {
        if (bitmap.isRecycled) return
        val thumbnail = runCatching { normalize(bitmap) }.getOrNull()
        if (thumbnail == null) {
            runCatching { bitmap.recycle() }
            return
        }
        if (thumbnail !== bitmap) runCatching { bitmap.recycle() }
        cache[tabId]?.let { old -> if (old !== thumbnail) old.recycle() }
        cache[tabId] = thumbnail
    }

    @Synchronized
    fun get(tabId: String): Bitmap? = cache[tabId]

    @Synchronized
    fun remove(tabId: String) {
        cache.remove(tabId)?.recycle()
    }

    /** Copy of the current cache; the switcher reads this when (re)composed. */
    @Synchronized
    fun snapshot(): Map<String, Bitmap> = HashMap(cache)

    /**
     * Captures the WebView's visible viewport scaled within the thumbnail bounds.
     * MUST be called on the UI thread. Returns null when the view has no
     * size yet or capture fails.
     */
    fun capture(webView: WebView): Bitmap? {
        return try {
            val w = webView.width
            val h = webView.height
            if (w <= 0 || h <= 0) return null
            val size = tabThumbnailDimensions(w, h, THUMB_WIDTH, THUMB_HEIGHT) ?: return null
            val scale = minOf(size.width.toFloat() / w, size.height.toFloat() / h)
            val bmp = Bitmap.createBitmap(
                size.width,
                size.height,
                Bitmap.Config.RGB_565
            )
            val canvas = Canvas(bmp)
            canvas.scale(scale, scale)
            webView.draw(canvas)
            bmp
        } catch (_: Exception) {
            null
        }
    }

    private fun normalize(bitmap: Bitmap): Bitmap? {
        val size = tabThumbnailDimensions(bitmap.width, bitmap.height, THUMB_WIDTH, THUMB_HEIGHT)
            ?: return null
        if (bitmap.config == Bitmap.Config.RGB_565 &&
            bitmap.width == size.width && bitmap.height == size.height
        ) return bitmap

        val thumbnail = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.RGB_565)
        Canvas(thumbnail).drawBitmap(
            bitmap,
            null,
            Rect(0, 0, size.width, size.height),
            Paint(Paint.FILTER_BITMAP_FLAG)
        )
        return thumbnail
    }
}
