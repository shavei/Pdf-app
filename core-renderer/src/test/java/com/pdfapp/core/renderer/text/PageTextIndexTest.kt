package com.pdfapp.core.renderer.text

import com.google.common.truth.Truth.assertThat
import com.pdfapp.core.renderer.model.PdfPoint
import com.pdfapp.core.renderer.model.PdfRect
import org.junit.Test

/**
 * Pure-JVM tests for the search/selection geometry over a hand-built index:
 * two lines, ten-point-wide glyphs, line 1 at y 100..110, line 2 at y 80..90.
 *
 * ```
 * "cat dog\nfish"
 * ```
 */
class PageTextIndexTest {
    private val index = buildIndex("cat dog\nfish")

    private fun buildIndex(text: String): PageTextIndex {
        var x = 0f
        var lineTop = 110f
        val boxes =
            text.map { char ->
                when (char) {
                    ' ' -> {
                        x += GLYPH_WIDTH
                        null
                    }
                    '\n' -> {
                        x = 0f
                        lineTop -= LINE_HEIGHT
                        null
                    }
                    else -> {
                        val box = PdfRect(x, lineTop - GLYPH_HEIGHT, x + GLYPH_WIDTH, lineTop)
                        x += GLYPH_WIDTH
                        box
                    }
                }
            }
        return PageTextIndex(pageIndex = 0, text = text, charBoxes = boxes)
    }

    @Test
    fun `search finds case-insensitive matches with merged single-line boxes`() {
        val matches = index.search("DOG")

        assertThat(matches).hasSize(1)
        val match = matches.single()
        assertThat(index.textIn(match.range)).isEqualTo("dog")
        assertThat(match.boxes).hasSize(1)
        val box = match.boxes.single()
        // "dog" starts after "cat " = 4 glyph advances.
        assertThat(box.left).isEqualTo(4 * GLYPH_WIDTH)
        assertThat(box.right).isEqualTo(7 * GLYPH_WIDTH)
        assertThat(box.top).isEqualTo(110f)
    }

    @Test
    fun `match spanning a line break produces one box per line`() {
        val start = index.text.indexOf("dog")
        val end = index.text.indexOf("fish") + "fish".length - 1
        val boxes = index.boxesFor(start..end)

        assertThat(boxes).hasSize(2)
        assertThat(boxes[0].top).isEqualTo(110f)
        assertThat(boxes[1].top).isEqualTo(110f - LINE_HEIGHT)
    }

    @Test
    fun `search for blank or missing text returns nothing`() {
        assertThat(index.search("")).isEmpty()
        assertThat(index.search("  ")).isEmpty()
        assertThat(index.search("bird")).isEmpty()
    }

    @Test
    fun `wordRangeAt returns the word under the point`() {
        // Inside the "o" of "dog": glyph 5, line 1.
        val range = index.wordRangeAt(PdfPoint(5.5f * GLYPH_WIDTH, 105f))

        assertThat(range).isNotNull()
        assertThat(index.textIn(range!!)).isEqualTo("dog")
    }

    @Test
    fun `wordRangeAt misses when the point is off every glyph`() {
        assertThat(index.wordRangeAt(PdfPoint(500f, 500f))).isNull()
    }

    @Test
    fun `selectionBetween snaps outward to whole words in either drag direction`() {
        val inCat = PdfPoint(1.5f * GLYPH_WIDTH, 105f)
        val inFish = PdfPoint(2.5f * GLYPH_WIDTH, 85f)

        val forward = index.selectionBetween(inCat, inFish)
        val backward = index.selectionBetween(inFish, inCat)

        assertThat(index.textIn(forward!!)).isEqualTo("cat dog\nfish")
        assertThat(backward).isEqualTo(forward)
    }

    @Test
    fun `a phrase is found across the line break it wraps at`() {
        // The page reads "cat dog fish"; the producer emitted "dog\nfish".
        val matches = index.search("dog fish")

        assertThat(matches).hasSize(1)
        assertThat(index.textIn(matches.single().range)).isEqualTo("dog\nfish")
        // Still two highlight quads — one per line.
        assertThat(matches.single().boxes).hasSize(2)
    }

    @Test
    fun `a word split by an end-of-line hyphen is found whole`() {
        val hyphenated = buildIndex("hyphen-\nated word")

        val matches = hyphenated.search("hyphenated")

        assertThat(matches).hasSize(1)
        assertThat(hyphenated.textIn(matches.single().range)).isEqualTo("hyphen-\nated")
        assertThat(hyphenated.search("hyphen-")).isEmpty()
    }

    @Test
    fun `non-breaking and repeated spaces match a single typed space`() {
        val padded = buildIndex("cat\u00A0dog")

        assertThat(padded.search("cat dog")).hasSize(1)
        assertThat(index.search("  cat   dog  ")).hasSize(1)
    }

    @Test
    fun `matches do not overlap`() {
        val repeated = buildIndex("aaaa")

        assertThat(repeated.search("aa")).hasSize(2)
    }

    @Test
    fun `charIndexNear tolerates points just outside a glyph`() {
        // 5pt above the "c" of "cat" — outside the box but within tolerance.
        val near = index.charIndexNear(PdfPoint(0.5f * GLYPH_WIDTH, 115f))

        assertThat(near).isEqualTo(0)
        assertThat(index.charIndexNear(PdfPoint(0.5f * GLYPH_WIDTH, 400f))).isNull()
    }

    private companion object {
        const val GLYPH_WIDTH = 10f
        const val GLYPH_HEIGHT = 10f
        const val LINE_HEIGHT = 20f
    }
}
