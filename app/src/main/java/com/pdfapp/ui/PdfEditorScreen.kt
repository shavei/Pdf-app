package com.pdfapp.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pdfapp.core.renderer.model.PdfPoint
import com.pdfapp.overlay.OverlayCanvasView
import com.pdfapp.overlay.model.OverlayLayer
import com.pdfapp.overlay.model.TextOverlay

/**
 * Single-screen editor wiring the three feature modules: open + render
 * (`:core-renderer`), draw overlays (`:overlay-engine`), and flatten + save
 * (`:file-persistence`). The custom [OverlayCanvasView] holds the live overlay
 * layer; this screen only orchestrates it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfEditorScreen(viewModel: PdfEditorViewModel = viewModel()) {
    val context = LocalContext.current
    var canvasView by remember { mutableStateOf<OverlayCanvasView?>(null) }
    var mode by remember { mutableStateOf(OverlayCanvasView.Mode.INK) }
    var pendingTextPoint by remember { mutableStateOf<PdfPoint?>(null) }

    val openLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { viewModel.open(context, it) } }

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf"),
    ) { uri ->
        uri?.let { dest -> viewModel.save(context, dest, canvasView?.layer ?: OverlayLayer(0)) }
    }

    val rendered = viewModel.renderedPage
    LaunchedEffect(rendered, canvasView) {
        val view = canvasView ?: return@LaunchedEffect
        rendered?.let(view::setPage)
    }
    LaunchedEffect(mode, canvasView) { canvasView?.mode = mode }

    Scaffold(topBar = { TopAppBar(title = { Text("PDF-App") }) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = { openLauncher.launch(arrayOf(MIME_PDF)) }) { Text("Open") }
                FilterChip(
                    selected = mode == OverlayCanvasView.Mode.INK,
                    onClick = { mode = OverlayCanvasView.Mode.INK },
                    label = { Text("Sign") },
                )
                FilterChip(
                    selected = mode == OverlayCanvasView.Mode.TEXT,
                    onClick = { mode = OverlayCanvasView.Mode.TEXT },
                    label = { Text("Text") },
                )
                Button(onClick = { canvasView?.commitSignature() }) { Text("Ink✓") }
                Button(
                    onClick = { saveLauncher.launch("signed.pdf") },
                    enabled = rendered != null,
                ) { Text("Save") }
            }

            Text(text = viewModel.status, modifier = Modifier.padding(horizontal = 12.dp))

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .horizontalScroll(rememberScrollState()),
            ) {
                AndroidView(
                    factory = { ctx ->
                        OverlayCanvasView(ctx).also { view ->
                            view.onTextPlacementRequested = { point -> pendingTextPoint = point }
                            canvasView = view
                        }
                    },
                )
            }
        }
    }

    pendingTextPoint?.let { point ->
        TextEntryDialog(
            onDismiss = { pendingTextPoint = null },
            onConfirm = { text ->
                canvasView?.let { it.layer = it.layer.withText(TextOverlay(text, point)) }
                pendingTextPoint = null
            },
        )
    }
}

private const val MIME_PDF = "application/pdf"
