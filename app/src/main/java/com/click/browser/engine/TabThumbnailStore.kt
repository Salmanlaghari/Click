package com.click.browser.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.webkit.WebView

/**
 * LRU in-memory cache of scaled tab thumbnails for the visual tab switcher.
 *
 * Design for smoothness (Prince: "smooth chale"):
 * - Thumbnails are captured ONCE per page finish (never continuously) at a
 *   small size (360px wide, RGB_565) so the grid scrolls without jank.
 * - Max 24 entries; eldest dropped silently (no manual recycle — a recycled
 *   bitmap still referenced by a visible card would crash on draw).
 * - Incognito tabs are NEVER captured (privacy): the switcher shows a
 *   lock placeholder for them instead.
 */
object TabThumbnailStore {
    private const val MAX_ENTRIES = 24
    private const val THUMB_WIDTH = 360

    private val cache = object : LinkedHashMap<String, Bitmap>(MAX_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?): Boolean {
            return size > MAX_ENTRIES
        }
    }

    @Synchronized
    fun put(tabId: String, bitmap: Bitmap) {
        cache[tabId]?.let { old -> if (old != bitmap) old.recycle() }
        cache[tabId] = bitmap
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
     * Captures the WebView's visible viewport scaled down to [THUMB_WIDTH].
     * MUST be called on the UI thread. Returns null when the view has no
     * size yet or capture fails.
     */
    fun capture(webView: WebView): Bitmap? {
        return try {
            val w = webView.width
            val h = webView.height
            if (w <= 0 || h <= 0) return null
            val scale = THUMB_WIDTH.toFloat() / w
            val bmp = Bitmap.createBitmap(
                THUMB_WIDTH,
                (h * scale).toInt().coerceAtLeast(1),
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
}
