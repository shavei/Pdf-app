package com.pdfapp.core.renderer.text

import com.google.common.truth.Truth.assertThat
import com.pdfapp.core.renderer.model.PdfPoint
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.interactive.action.PDActionURI
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageXYZDestination
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Integration tests for the read-only PdfBox facade: generate real PDFs in
 * memory, then extract text geometry, search, outline, and links through the
 * production API. Runs headless on the JVM via Robolectric (PdfBox needs a
 * Context for its resource loader).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PdfTextDocumentTest {
    @Before
    fun setUp() {
        PDFBoxResourceLoader.init(RuntimeEnvironment.getApplication())
    }

    /** Build a PDF where page N shows one line of [pageTexts] at (72, 700). */
    private fun buildPdf(
        vararg pageTexts: String,
        decorate: (PDDocument) -> Unit = {},
    ): PdfTextDocument {
        val bytes =
            PDDocument().use { document ->
                pageTexts.forEach { text ->
                    val page = PDPage(PDRectangle.LETTER)
                    document.addPage(page)
                    PDPageContentStream(document, page).use { content ->
                        content.beginText()
                        content.setFont(PDType1Font.HELVETICA, FONT_SIZE)
                        content.newLineAtOffset(TEXT_X, TEXT_Y)
                        content.showText(text)
                        content.endText()
                    }
                }
                decorate(document)
                ByteArrayOutputStream().also(document::save).toByteArray()
            }
        return PdfTextDocument.load(ByteArrayInputStream(bytes))
    }

    @Test
    fun `extracts page text with glyph boxes near the drawn position`() {
        buildPdf("Hello world").use { document ->
            val page = document.pageText(0)

            assertThat(page.text).contains("Hello world")
            val match = page.search("world").single()
            val box = match.boxes.single()
            // "world" sits right of "Hello " and on the 700pt baseline.
            assertThat(box.left).isGreaterThan(TEXT_X)
            assertThat(box.bottom).isWithin(BASELINE_TOLERANCE_PT).of(TEXT_Y)
            assertThat(box.top).isGreaterThan(box.bottom)
            assertThat(box.right).isGreaterThan(box.left)
        }
    }

    @Test
    fun `search is case-insensitive and per page`() {
        buildPdf("nothing here", "the NEEDLE is on page two").use { document ->
            assertThat(document.searchPage(0, "needle")).isEmpty()

            val matches = document.searchPage(1, "needle")
            assertThat(matches).hasSize(1)
            assertThat(matches.single().pageIndex).isEqualTo(1)
        }
    }

    /**
     * The regression this whole normalisation exists for: on a real two-line
     * page the stripper puts a `\n` where the layout wrapped, so a literal
     * search missed any phrase that straddled it.
     */
    @Test
    fun `phrases and hyphenated words are found across a real line wrap`() {
        buildWrappedPdf("The quick brown fox jumps over the", "lazy dog, unin-", "terrupted").use {
            val page = it.pageText(0)

            assertThat(page.text).contains("the\nlazy")
            assertThat(it.searchPage(0, "over the lazy dog").single().boxes).hasSize(2)
            assertThat(it.searchPage(0, "uninterrupted")).hasSize(1)
            assertThat(it.searchPage(0, "quick brown")).hasSize(1)
        }
    }

    /** Build a one-page PDF with [lines] laid out as consecutive text lines. */
    private fun buildWrappedPdf(vararg lines: String): PdfTextDocument {
        val bytes =
            PDDocument().use { document ->
                val page = PDPage(PDRectangle.LETTER)
                document.addPage(page)
                PDPageContentStream(document, page).use { content ->
                    content.beginText()
                    content.setFont(PDType1Font.HELVETICA, FONT_SIZE)
                    content.newLineAtOffset(TEXT_X, TEXT_Y)
                    content.setLeading(LEADING)
                    lines.forEach { line ->
                        content.showText(line)
                        content.newLine()
                    }
                    content.endText()
                }
                ByteArrayOutputStream().also(document::save).toByteArray()
            }
        return PdfTextDocument.load(ByteArrayInputStream(bytes))
    }

    @Test
    fun `word selection round-trips through glyph geometry`() {
        buildPdf("Hello world").use { document ->
            val page = document.pageText(0)
            val worldBox = page.search("world").single().boxes.single()

            val range = page.wordRangeAt(worldBox.center)

            assertThat(range).isNotNull()
            assertThat(page.textIn(range!!)).isEqualTo("world")
        }
    }

    @Test
    fun `selection between two words covers the phrase`() {
        buildPdf("Hello brave world").use { document ->
            val page = document.pageText(0)
            val hello = page.search("Hello").single().boxes.single().center
            val world = page.search("world").single().boxes.single().center

            val range = page.selectionBetween(hello, world)

            assertThat(page.textIn(range!!)).isEqualTo("Hello brave world")
        }
    }

    @Test
    fun `outline resolves titles, nesting and page indices`() {
        val document =
            buildPdf("one", "two") { pdf ->
                val outline = PDDocumentOutline()
                pdf.documentCatalog.documentOutline = outline
                val chapter = outlineItem("Chapter 1", pdf, pageIndex = 0)
                chapter.addLast(outlineItem("Section 1.1", pdf, pageIndex = 1))
                outline.addLast(chapter)
            }
        document.use {
            val entries = it.outline()

            assertThat(entries).hasSize(2)
            assertThat(entries[0]).isEqualTo(OutlineEntry("Chapter 1", 0, 0))
            assertThat(entries[1]).isEqualTo(OutlineEntry("Section 1.1", 1, 1))
        }
    }

    @Test
    fun `document without outline yields an empty list`() {
        buildPdf("plain").use { document ->
            assertThat(document.outline()).isEmpty()
        }
    }

    @Test
    fun `url and internal links resolve with their hit boxes`() {
        val document =
            buildPdf("links", "target") { pdf ->
                val page = pdf.getPage(0)
                page.annotations.add(
                    PDAnnotationLink().apply {
                        rectangle = PDRectangle(LINK_X, LINK_Y, LINK_SIZE, LINK_SIZE)
                        action = PDActionURI().apply { uri = "https://example.com" }
                    },
                )
                page.annotations.add(
                    PDAnnotationLink().apply {
                        rectangle = PDRectangle(LINK_X * 2, LINK_Y, LINK_SIZE, LINK_SIZE)
                        destination = PDPageXYZDestination().apply { setPage(pdf.getPage(1)) }
                    },
                )
            }
        document.use {
            val links = it.links(0)

            assertThat(links).hasSize(2)
            val url = links.first { link -> link.target is PdfLink.Target.Url }
            assertThat((url.target as PdfLink.Target.Url).url).isEqualTo("https://example.com")
            assertThat(url.box.contains(PdfPoint(LINK_X + 1f, LINK_Y + 1f))).isTrue()
            val internal = links.first { link -> link.target is PdfLink.Target.Page }
            assertThat((internal.target as PdfLink.Target.Page).pageIndex).isEqualTo(1)
        }
    }

    private fun outlineItem(
        title: String,
        pdf: PDDocument,
        pageIndex: Int,
    ): PDOutlineItem =
        PDOutlineItem().apply {
            this.title = title
            destination = PDPageXYZDestination().apply { setPage(pdf.getPage(pageIndex)) }
        }

    private companion object {
        const val FONT_SIZE = 12f
        const val LEADING = 16f
        const val TEXT_X = 72f
        const val TEXT_Y = 700f
        const val BASELINE_TOLERANCE_PT = 4f
        const val LINK_X = 100f
        const val LINK_Y = 500f
        const val LINK_SIZE = 40f
    }
}
