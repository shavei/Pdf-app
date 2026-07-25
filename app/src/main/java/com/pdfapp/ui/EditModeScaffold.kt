package com.pdfapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.viewinterop.AndroidView
import com.pdfapp.core.renderer.model.PdfPoint
import com.pdfapp.overlay.OverlayCanvasView
import com.pdfapp.overlay.model.OverlayLayer
import com.pdfapp.overlay.model.TextOverlay

// EDIT-mode plumbing for PdfEditorScreen: the chrome around the interactive
// OverlayCanvasView, the state fed into it, and the text-overlay dialogs it
// raises. Split out of the screen so that file stays focused on the READ/EDIT
// shell itself.

/** Feed edit-mode state (page bitmap, tool settings) into the canvas view. */
@Composable
internal fun EditModeBindings(
    viewModel: PdfEditorViewModel,
    canvasView: OverlayCanvasView?,
    editTool: OverlayCanvasView.Mode,
) {
    val rendered = viewModel.renderedPage
    LaunchedEffect(rendered, canvasView) {
        val view = canvasView ?: return@LaunchedEffect
        val page = rendered ?: return@LaunchedEffect
        val layer = viewModel.overlayDocument?.layerFor(page.index) ?: OverlayLayer(page.index)
        view.setPage(page, layer)
    }
    LaunchedEffect(viewModel.inkColorArgb, viewModel.inkStrokeWidthPt, canvasView) {
        canvasView?.inkColorArgb = viewModel.inkColorArgb
        canvasView?.inkStrokeWidthPt = viewModel.inkStrokeWidthPt
    }
    LaunchedEffect(editTool, canvasView) { canvasView?.mode = editTool }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditTopBar(onBack: () -> Unit) {
    CenterAlignedTopAppBar(
        title = { Text("Edit", fontWeight = FontWeight.SemiBold) },
        navigationIcon = {
            IconButton(onClick = onBack, modifier = Modifier.iconTouchTarget()) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to reading")
            }
        },
    )
}

@Composable
internal fun EditModeContent(onCanvasReady: (OverlayCanvasView) -> Unit) {
    // The tool chrome now lives in the Scaffold's bottom bar (mobile-ui-plan
    // Phase C), so edit mode gives the whole content area to the page. The
    // canvas view fills it and handles pinch-zoom and panning itself, so no
    // scroll containers wrap it.
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx -> OverlayCanvasView(ctx).also(onCanvasReady) },
        )
    }
}

@Composable
internal fun TextOverlayDialogs(
    viewModel: PdfEditorViewModel,
    canvasView: OverlayCanvasView?,
    pendingTextPoint: PdfPoint?,
    onPendingConsumed: () -> Unit,
    editingText: TextOverlay?,
    onEditingConsumed: () -> Unit,
) {
    pendingTextPoint?.let { point ->
        TextEntryDialog(
            onDismiss = onPendingConsumed,
            onConfirm = { text ->
                canvasView?.let { view ->
                    view.layer =
                        view.layer.withText(
                            TextOverlay(
                                text = text,
                                position = point,
                                fontSizePt = viewModel.textSizePt,
                                colorArgb = viewModel.textColorArgb,
                            ),
                        )
                }
                onPendingConsumed()
            },
        )
    }

    editingText?.let { overlay ->
        TextEntryDialog(
            title = "Edit text",
            confirmLabel = "Save",
            initialText = overlay.text,
            onDismiss = onEditingConsumed,
            onConfirm = { text ->
                canvasView?.let { view ->
                    view.layer = view.layer.updateText(overlay.copy(text = text))
                }
                onEditingConsumed()
            },
            onDelete = {
                canvasView?.let { view ->
                    view.layer = view.layer.removeText(overlay.id)
                }
                onEditingConsumed()
            },
        )
    }
}
