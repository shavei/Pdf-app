package com.pdfapp.core.renderer.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Exhaustive tests for the px <-> PDF-point conversion. This is the project's
 * highest-risk logic (origin flip + scale), so it is tested on the plain JVM.
 */
class CoordinateMapperTest {
    private val a4 = PageSize(widthPt = 595f, heightPt = 842f)

    @Test
    fun `bitmap dimensions scale by pixelsPerPoint`() {
        val mapper = CoordinateMapper(a4, pixelsPerPoint = 2f)
        assertThat(mapper.bitmapWidthPx).isEqualTo(1190f)
        assertThat(mapper.bitmapHeightPx).isEqualTo(1684f)
    }

    @Test
    fun `top-left pixel maps to top-left of page in PDF space`() {
        val mapper = CoordinateMapper(a4, pixelsPerPoint = 2f)
        // Bitmap origin (0,0) is the visual top-left == PDF (0, pageHeight).
        val pdf = mapper.toPdfPoint(PixelPoint(0f, 0f))
        assertThat(pdf.x).isEqualTo(0f)
        assertThat(pdf.y).isEqualTo(842f)
    }

    @Test
    fun `bottom-left pixel maps to PDF origin`() {
        val mapper = CoordinateMapper(a4, pixelsPerPoint = 1f)
        val pdf = mapper.toPdfPoint(PixelPoint(0f, 842f))
        assertThat(pdf.x).isEqualTo(0f)
        assertThat(pdf.y).isEqualTo(0f)
    }

    @Test
    fun `toPixel is the inverse of toPdfPoint`() {
        val mapper = CoordinateMapper(a4, pixelsPerPoint = 1.5f)
        val original = PixelPoint(123.5f, 456.25f)
        val roundTripped = mapper.toPixel(mapper.toPdfPoint(original))
        assertThat(roundTripped.x).isWithin(1e-3f).of(original.x)
        assertThat(roundTripped.y).isWithin(1e-3f).of(original.y)
    }

    @Test
    fun `toPdfPoint is the inverse of toPixel`() {
        val mapper = CoordinateMapper(a4, pixelsPerPoint = 2f)
        val original = PdfPoint(200f, 300f)
        val roundTripped = mapper.toPdfPoint(mapper.toPixel(original))
        assertThat(roundTripped.x).isWithin(1e-3f).of(original.x)
        assertThat(roundTripped.y).isWithin(1e-3f).of(original.y)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `non-positive scale is rejected`() {
        CoordinateMapper(a4, pixelsPerPoint = 0f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `non-positive page size is rejected`() {
        PageSize(widthPt = 0f, heightPt = 100f)
    }
}
