package com.pdfapp.ui.document

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.pdfapp.core.renderer.model.PdfPoint
import com.pdfapp.core.renderer.model.PdfRect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Text-selection state for the reader (plan 2.3): long-press selects the word
 * under the finger, dragging extends the selection word-by-word on the same
 * page, and the selected string is offered for copying.
 */
class SelectionController(
    private val scope: CoroutineScope,
    private val session: () -> DocumentSession?,
) {
    data class Selection(
        val pageIndex: Int,
        val text: String,
        val boxes: List<PdfRect>,
    )

    var selection: Selection? by mutableStateOf(null)
        private set

    private var anchorPage = -1
    private var anchor: PdfPoint? = null
    private var computeJob: Job? = null

    /**
     * Whether a long-press selection is latched. Set the instant the press
     * lands — before the (asynchronous) text lookup fills [selection] — so a
     * gesture handler can tell a selection drag from a viewport pan without
     * waiting a frame. Deliberately not snapshot state: it is read from pointer
     * callbacks, which must not drive recomposition.
     */
    val isSelecting: Boolean
        get() = anchor != null

    /** Long-press at [point] on [pageIndex]: select the word underneath. */
    fun startAt(
        pageIndex: Int,
        point: PdfPoint,
    ) {
        anchorPage = pageIndex
        anchor = point
        recompute(focus = point)
    }

    /** Drag to [point] (same page as the anchor): extend across words. */
    fun extendTo(point: PdfPoint) {
        if (anchor != null) recompute(focus = point)
    }

    fun clear() {
        computeJob?.cancel()
        selection = null
        anchor = null
        anchorPage = -1
    }

    private fun recompute(focus: PdfPoint) {
        val page = anchorPage
        val start = anchor ?: return
        computeJob?.cancel()
        computeJob =
            scope.launch {
                val active = session() ?: return@launch
                val computed =
                    withContext(Dispatchers.IO) {
                        val pageText = active.textDocument().pageText(page)
                        val range =
                            if (start == focus) {
                                pageText.wordRangeAt(start)
                            } else {
                                pageText.selectionBetween(start, focus)
                            }
                        range?.let {
                            Selection(page, pageText.textIn(it), pageText.boxesFor(it))
                        }
                    }
                // Keep the last good selection while the finger crosses a gap.
                if (computed != null) selection = computed
            }
    }
}
