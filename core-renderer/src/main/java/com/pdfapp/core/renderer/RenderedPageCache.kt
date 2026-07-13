package com.pdfapp.core.renderer

import android.util.LruCache
import com.pdfapp.core.renderer.model.PageSize
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.roundToInt

/**
 * Memory-bounded page cache in front of a [PageRendering].
 *
 * Serves the continuous-scroll reader and the thumbnail grid: pages are
 * rendered on demand, keyed by (page, quantised scale) so a zoom change
 * re-renders crisply while the fit-width and thumbnail variants of other
 * pages stay warm. An LRU sized in bytes (default: a quarter of the heap)
 * evicts the least recently shown bitmaps first, keeping 1000-page documents
 * scrollable at a bounded memory cost.
 *
 * All rendering funnels through one [Mutex] because [android.graphics.pdf.PdfRenderer]
 * allows only a single open page at a time; concurrent requests for the same
 * key render once.
 */
class RenderedPageCache(
    private val renderer: PageRendering,
    maxBytes: Int = defaultBudgetBytes(),
) {
    private data class Key(
        val pageIndex: Int,
        val scaleCentiPixelsPerPoint: Int,
    )

    private val renderMutex = Mutex()
    private val pageSizes = HashMap<Int, PageSize>()
    private val cache =
        object : LruCache<Key, RenderedPage>(maxBytes) {
            override fun sizeOf(
                key: Key,
                value: RenderedPage,
            ): Int = value.bitmap.byteCount
        }

    /**
     * The rendered page for [pageIndex] at [pixelsPerPoint] (quantised to
     * 0.01 px/pt), from cache when warm.
     */
    suspend fun page(
        pageIndex: Int,
        pixelsPerPoint: Float,
    ): RenderedPage {
        val key = Key(pageIndex, (pixelsPerPoint * SCALE_QUANTUM).roundToInt().coerceAtLeast(1))
        cache.get(key)?.let { return it }
        return renderMutex.withLock {
            cache.get(key) ?: renderer
                .renderPage(pageIndex, key.scaleCentiPixelsPerPoint / SCALE_QUANTUM)
                .also { cache.put(key, it) }
        }
    }

    /** Displayed size of [pageIndex] in points, cached after the first lookup. */
    suspend fun pageSize(pageIndex: Int): PageSize {
        synchronized(pageSizes) { pageSizes[pageIndex] }?.let { return it }
        val size = renderMutex.withLock { renderer.pageSize(pageIndex) }
        synchronized(pageSizes) { pageSizes[pageIndex] = size }
        return size
    }

    /** Drop every cached bitmap (e.g. when the document closes). */
    fun clear() {
        cache.evictAll()
        synchronized(pageSizes) { pageSizes.clear() }
    }

    companion object {
        private const val SCALE_QUANTUM = 100f
        private const val MIN_BUDGET_BYTES = 32 * 1024 * 1024
        private const val MAX_BUDGET_BYTES = 256 * 1024 * 1024

        /** A quarter of the JVM heap, clamped to a sane 32–256 MB window. */
        fun defaultBudgetBytes(): Int =
            (Runtime.getRuntime().maxMemory() / 4)
                .coerceIn(MIN_BUDGET_BYTES.toLong(), MAX_BUDGET_BYTES.toLong())
                .toInt()
    }
}
