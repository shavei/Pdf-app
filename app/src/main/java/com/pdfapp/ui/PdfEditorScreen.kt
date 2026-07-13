package com.pdfapp.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pdfapp.R
import com.pdfapp.core.renderer.model.PdfPoint
import com.pdfapp.overlay.OverlayCanvasView
import com.pdfapp.overlay.model.OverlayLayer
import com.pdfapp.overlay.model.TextOverlay

/**
 * Single-screen editor wiring the three feature modules: open + render
 * (`:core-renderer`), draw overlays (`:overlay-engine`), and flatten + save
 * (`:file-persistence`). The custom [OverlayCanvasView] holds the live overlay
 * layer for the current page and reports changes back to the view-model, which
 * tracks overlays for every page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfEditorScreen(viewModel: PdfEditorViewModel = viewModel()) {
    val context = LocalContext.current
    var canvasView by remember { mutableStateOf<OverlayCanvasView?>(null) }
    var mode by remember { mutableStateOf(OverlayCanvasView.Mode.INK) }
    var pendingTextPoint by remember { mutableStateOf<PdfPoint?>(null) }
    var editingText by remember { mutableStateOf<TextOverlay?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    val openLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument(),
        ) { uri -> uri?.let { viewModel.open(context, it) } }

    val saveLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument(MIME_PDF),
        ) { uri ->
            uri?.let { dest ->
                canvasView?.commitSignature()
                viewModel.save(context, dest)
            }
        }

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
    LaunchedEffect(mode, canvasView) { canvasView?.mode = mode }
    LaunchedEffect(viewModel.userMessage) {
        viewModel.userMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.messageShown()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            val controlsEnabled = rendered != null && !viewModel.busy
            EditorToolbar(
                mode = mode,
                enabled = controlsEnabled,
                onModeChange = { mode = it },
                onOpen = { openLauncher.launch(arrayOf(MIME_PDF)) },
                onCommitInk = { canvasView?.commitSignature() },
                onUndo = { canvasView?.undo() },
                onClear = { canvasView?.clearOverlays() },
                onSave = { saveLauncher.launch("signed.pdf") },
            )
            if (rendered != null) {
                ToolSettingsRow(viewModel)
                PageNavBar(
                    currentIndex = viewModel.currentPageIndex,
                    pageCount = viewModel.pageCount,
                    canPrevious = viewModel.canGoPrevious && !viewModel.busy,
                    canNext = viewModel.canGoNext && !viewModel.busy,
                    onPrevious = {
                        canvasView?.commitSignature()
                        viewModel.previousPage()
                    },
                    onNext = {
                        canvasView?.commitSignature()
                        viewModel.nextPage()
                    },
                )
            }

            Box(modifier = Modifier.fillMaxSize().weight(1f)) {
                // The canvas view fills the area and handles pinch-zoom and
                // panning itself, so no scroll containers wrap it.
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        OverlayCanvasView(ctx).also { view ->
                            view.onTextPlacementRequested = { point -> pendingTextPoint = point }
                            view.onTextEditRequested = { overlay -> editingText = overlay }
                            view.onLayerChanged = { layer -> viewModel.updateCurrentLayer(layer) }
                            canvasView = view
                        }
                    },
                )
                if (viewModel.busy) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
            }
        }
    }

    pendingTextPoint?.let { point ->
        TextEntryDialog(
            onDismiss = { pendingTextPoint = null },
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
                pendingTextPoint = null
            },
        )
    }

    editingText?.let { overlay ->
        TextEntryDialog(
            title = "Edit text",
            confirmLabel = "Save",
            initialText = overlay.text,
            onDismiss = { editingText = null },
            onConfirm = { text ->
                canvasView?.let { view ->
                    view.layer = view.layer.updateText(overlay.copy(text = text))
                }
                editingText = null
            },
            onDelete = {
                canvasView?.let { view ->
                    view.layer = view.layer.removeText(overlay.id)
                }
                editingText = null
            },
        )
    }
}

private const val MIME_PDF = "application/pdf"
