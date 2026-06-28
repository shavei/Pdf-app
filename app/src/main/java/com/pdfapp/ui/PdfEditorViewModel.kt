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
import com.pdfapp.overlay.model.OverlayLayer
import com.pdfapp.persistence.PdfFlattener
import com.pdfapp.persistence.PdfSaver
import com.tom_roush.pdfbox.pdmodel.PDDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** UI state for editing a single page (step-1 scope renders page 0). */
class PdfEditorViewModel : ViewModel() {
    var renderedPage: RenderedPage? by mutableStateOf(null)
        private set

    var status: String by mutableStateOf("Open a PDF to begin")
        private set

    private var sourceUri: Uri? = null
    private var source: PdfDocumentSource? = null

    /** Open a PDF from a SAF [uri] and render its first page. */
    fun open(
        context: Context,
        uri: Uri,
    ) {
        viewModelScope.launch {
            runCatching {
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
                renderedPage = PageRenderer(opened).renderPage(index = 0, pixelsPerPoint = RENDER_SCALE)
                status = "Loaded page 1 of ${opened.pageCount}"
            }.onFailure { status = "Failed to open: ${it.message}" }
        }
    }

    /** Flatten [layer] into a fresh copy of the source PDF and save to [destUri]. */
    fun save(
        context: Context,
        destUri: Uri,
        layer: OverlayLayer,
    ) {
        val src = sourceUri
        if (src == null) {
            status = "Nothing to save yet"
            return
        }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val input =
                        context.contentResolver.openInputStream(src)
                            ?: error("Unable to open source PDF")
                    input.use { stream ->
                        PDDocument.load(stream).use { document ->
                            PdfFlattener().flattenInto(document, layer)
                            PdfSaver().saveToUri(context.contentResolver, destUri, document)
                        }
                    }
                }
                status = "Saved signed PDF"
            }.onFailure { status = "Failed to save: ${it.message}" }
        }
    }

    override fun onCleared() {
        source?.close()
        super.onCleared()
    }

    private companion object {
        const val RENDER_SCALE = 2f
    }
}
