package com.pdfapp.ui

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pdfapp.core.renderer.PageRenderer
import com.pdfapp.core.renderer.PdfDocumentSource
import com.pdfapp.core.renderer.RenderedPage
import com.pdfapp.overlay.model.InkSignature
import com.pdfapp.overlay.model.OverlayDocument
import com.pdfapp.overlay.model.OverlayLayer
import com.pdfapp.overlay.model.TextOverlay
import com.pdfapp.persistence.PdfFlattener
import com.pdfapp.persistence.PdfSaver
import com.tom_roush.pdfbox.pdmodel.PDDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** UI state for viewing and editing a multi-page PDF. */
class PdfEditorViewModel : ViewModel() {
    var renderedPage: RenderedPage? by mutableStateOf(null)
        private set

    var overlayDocument: OverlayDocument? by mutableStateOf(null)
        private set

    var currentPageIndex: Int by mutableStateOf(0)
        private set

    var busy: Boolean by mutableStateOf(false)
        private set

    /** One-shot message for the snackbar; cleared via [messageShown]. */
    var userMessage: String? by mutableStateOf(null)
        private set

    // Tool settings, read by the editor when creating overlays.
    var inkColorArgb: Int by mutableStateOf(InkSignature.DEFAULT_COLOR)
        private set
    var inkStrokeWidthPt: Float by mutableStateOf(InkSignature.DEFAULT_STROKE_WIDTH_PT)
        private set
    var textColorArgb: Int by mutableStateOf(TextOverlay.DEFAULT_COLOR)
        private set
    var textSizePt: Float by mutableStateOf(TextOverlay.DEFAULT_FONT_SIZE_PT)
        private set

    val pageCount: Int get() = overlayDocument?.pageCount ?: 0
    val canGoPrevious: Boolean get() = currentPageIndex > 0
    val canGoNext: Boolean get() = currentPageIndex < pageCount - 1

    private var sourceUri: Uri? = null
    private var source: PdfDocumentSource? = null

    /** Open a PDF from a SAF [uri] and render its first page. */
    fun open(
        context: Context,
        uri: Uri,
    ) {
        launchBusy {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
            source?.close()
            val opened =
                withContext(Dispatchers.IO) {
                    PdfDocumentSource.fromUri(context.contentResolver, uri)
                }
            source = opened
            sourceUri = uri
            overlayDocument = OverlayDocument(pageCount = opened.pageCount)
            currentPageIndex = 0
            renderedPage = PageRenderer(opened).renderPage(index = 0, pixelsPerPoint = RENDER_SCALE)
            userMessage = "Loaded ${opened.pageCount}-page PDF"
        }
    }

    fun nextPage() = goToPage(currentPageIndex + 1)

    fun previousPage() = goToPage(currentPageIndex - 1)

    private fun goToPage(index: Int) {
        val opened = source ?: return
        if (index !in 0 until opened.pageCount) return
        launchBusy {
            currentPageIndex = index
            renderedPage = PageRenderer(opened).renderPage(index, RENDER_SCALE)
        }
    }

    /** Persist the [layer] edited on the current page. */
    fun updateCurrentLayer(layer: OverlayLayer) {
        val document = overlayDocument ?: return
        overlayDocument = document.withLayer(layer)
    }

    fun setInkColor(argb: Int) {
        inkColorArgb = argb
    }

    fun setInkStrokeWidth(widthPt: Float) {
        inkStrokeWidthPt = widthPt.coerceIn(MIN_STROKE_PT, MAX_STROKE_PT)
    }

    fun setTextColor(argb: Int) {
        textColorArgb = argb
    }

    fun setTextSize(sizePt: Float) {
        textSizePt = sizePt.coerceIn(MIN_TEXT_PT, MAX_TEXT_PT)
    }

    /** Flatten every page's overlays into a fresh copy of the source PDF and save to [destUri]. */
    fun save(
        context: Context,
        destUri: Uri,
    ) {
        val src = sourceUri
        val document = overlayDocument
        if (src == null || document == null) {
            userMessage = "Nothing to save yet"
            return
        }
        if (!document.hasOverlays) {
            userMessage = "Add text or a signature first"
            return
        }
        launchBusy {
            withContext(Dispatchers.IO) {
                val input =
                    context.contentResolver.openInputStream(src)
                        ?: error("Unable to open source PDF")
                input.use { stream ->
                    PDDocument.load(stream).use { pdf ->
                        val flattener = PdfFlattener()
                        document.nonEmptyLayers.forEach { flattener.flattenInto(pdf, it) }
                        PdfSaver().saveToUri(context.contentResolver, destUri, pdf)
                    }
                }
            }
            userMessage = "Saved signed PDF"
        }
    }

    fun messageShown() {
        userMessage = null
    }

    /** Run [block] with the busy flag set, surfacing any failure to the snackbar. */
    private fun launchBusy(block: suspend () -> Unit) {
        viewModelScope.launch {
            busy = true
            runCatching { block() }
                .onFailure { userMessage = "Error: ${it.message}" }
            busy = false
        }
    }

    override fun onCleared() {
        source?.close()
        super.onCleared()
    }

    private companion object {
        const val RENDER_SCALE = 2f
        const val MIN_STROKE_PT = 1f
        const val MAX_STROKE_PT = 8f
        const val MIN_TEXT_PT = 8f
        const val MAX_TEXT_PT = 48f
    }
}
