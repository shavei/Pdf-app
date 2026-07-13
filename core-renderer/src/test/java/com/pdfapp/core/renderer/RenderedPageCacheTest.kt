package com.pdfapp.core.renderer

import android.graphics.Bitmap
import com.google.common.truth.Truth.assertThat
import com.pdfapp.core.renderer.model.CoordinateMapper
import com.pdfapp.core.renderer.model.PageSize
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RenderedPageCacheTest {
    /** Fake renderer producing tiny bitmaps and counting/watching calls. */
    private class FakeRenderer : PageRendering {
        val renderCalls = AtomicInteger()
        val sizeCalls = AtomicInteger()
        val active = AtomicInteger()
        var maxObservedConcurrency = 0
            private set

        override suspend fun renderPage(
            index: Int,
            pixelsPerPoint: Float,
        ): RenderedPage {
            renderCalls.incrementAndGet()
            maxObservedConcurrency = maxOf(maxObservedConcurrency, active.incrementAndGet())
            delay(1)
            active.decrementAndGet()
            val size = PageSize(PAGE_SIDE_PT, PAGE_SIDE_PT)
            val px = (PAGE_SIDE_PT * pixelsPerPoint).toInt().coerceAtLeast(1)
            return RenderedPage(
                index = index,
                bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888),
                pageSize = size,
                mapper = CoordinateMapper(size, pixelsPerPoint),
            )
        }

        override suspend fun pageSize(index: Int): PageSize {
            sizeCalls.incrementAndGet()
            return PageSize(PAGE_SIDE_PT, PAGE_SIDE_PT)
        }
    }

    @Test
    fun `repeated requests for the same page and scale render once`() =
        runTest {
            val renderer = FakeRenderer()
            val cache = RenderedPageCache(renderer)

            val first = cache.page(0, 2f)
            val second = cache.page(0, 2f)

            assertThat(second).isSameInstanceAs(first)
            assertThat(renderer.renderCalls.get()).isEqualTo(1)
        }

    @Test
    fun `nearby scales share a quantised bucket, distinct scales do not`() =
        runTest {
            val renderer = FakeRenderer()
            val cache = RenderedPageCache(renderer)

            cache.page(0, 2.001f)
            cache.page(0, 2.004f)
            assertThat(renderer.renderCalls.get()).isEqualTo(1)

            cache.page(0, 3f)
            assertThat(renderer.renderCalls.get()).isEqualTo(2)
        }

    @Test
    fun `evicts least recently used pages once over budget and re-renders on demand`() =
        runTest {
            val renderer = FakeRenderer()
            // Budget fits two 100x100 ARGB_8888 bitmaps (40 KB each), not three.
            val cache = RenderedPageCache(renderer, maxBytes = 2 * 100 * 100 * 4)

            cache.page(0, 1f)
            cache.page(1, 1f)
            cache.page(2, 1f)
            assertThat(renderer.renderCalls.get()).isEqualTo(3)

            // Page 0 was evicted, pages 1 and 2 are still warm.
            cache.page(2, 1f)
            cache.page(1, 1f)
            assertThat(renderer.renderCalls.get()).isEqualTo(3)
            cache.page(0, 1f)
            assertThat(renderer.renderCalls.get()).isEqualTo(4)
        }

    @Test
    fun `concurrent requests never render two pages at once`() =
        runTest {
            val renderer = FakeRenderer()
            val cache = RenderedPageCache(renderer)

            (0 until 8).map { async { cache.page(it, 1f) } }.forEach { it.await() }

            assertThat(renderer.maxObservedConcurrency).isEqualTo(1)
            assertThat(renderer.renderCalls.get()).isEqualTo(8)
        }

    @Test
    fun `page sizes are looked up once and survive clear of bitmaps`() =
        runTest {
            val renderer = FakeRenderer()
            val cache = RenderedPageCache(renderer)

            assertThat(cache.pageSize(3)).isEqualTo(PageSize(PAGE_SIDE_PT, PAGE_SIDE_PT))
            cache.pageSize(3)
            assertThat(renderer.sizeCalls.get()).isEqualTo(1)

            cache.clear()
            cache.pageSize(3)
            assertThat(renderer.sizeCalls.get()).isEqualTo(2)
        }

    private companion object {
        const val PAGE_SIDE_PT = 100f
    }
}
