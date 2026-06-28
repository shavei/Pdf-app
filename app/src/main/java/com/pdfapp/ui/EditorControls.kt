package com.pdfapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.pdfapp.overlay.OverlayCanvasView

/** Ink/text colour choices offered in the settings row. */
private val PALETTE: List<Int> =
    listOf(0xFF000000L, 0xFF001A66L, 0xFFB00020L, 0xFF1B5E20L).map { it.toInt() }

/** Primary action toolbar: open, mode toggle, commit ink, undo, clear, save. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorToolbar(
    mode: OverlayCanvasView.Mode,
    enabled: Boolean,
    onModeChange: (OverlayCanvasView.Mode) -> Unit,
    onOpen: () -> Unit,
    onCommitInk: () -> Unit,
    onUndo: () -> Unit,
    onClear: () -> Unit,
    onSave: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(onClick = onOpen) { Text("Open") }
        FilterChip(
            selected = mode == OverlayCanvasView.Mode.INK,
            onClick = { onModeChange(OverlayCanvasView.Mode.INK) },
            label = { Text("Sign") },
            enabled = enabled,
        )
        FilterChip(
            selected = mode == OverlayCanvasView.Mode.TEXT,
            onClick = { onModeChange(OverlayCanvasView.Mode.TEXT) },
            label = { Text("Text") },
            enabled = enabled,
        )
        OutlinedButton(onClick = onCommitInk, enabled = enabled) { Text("Ink✓") }
        OutlinedButton(onClick = onUndo, enabled = enabled) { Text("Undo") }
        OutlinedButton(onClick = onClear, enabled = enabled) { Text("Clear") }
        Button(onClick = onSave, enabled = enabled) { Text("Save") }
    }
}

/** Ink/text colour and size controls. */
@Composable
fun ToolSettingsRow(viewModel: PdfEditorViewModel) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Ink")
        ColorSwatches(selected = viewModel.inkColorArgb, onSelect = viewModel::setInkColor)
        Stepper(
            value = "${viewModel.inkStrokeWidthPt.toInt()}pt",
            onMinus = { viewModel.setInkStrokeWidth(viewModel.inkStrokeWidthPt - 1f) },
            onPlus = { viewModel.setInkStrokeWidth(viewModel.inkStrokeWidthPt + 1f) },
        )
        Text("Text")
        ColorSwatches(selected = viewModel.textColorArgb, onSelect = viewModel::setTextColor)
        Stepper(
            value = "${viewModel.textSizePt.toInt()}pt",
            onMinus = { viewModel.setTextSize(viewModel.textSizePt - 2f) },
            onPlus = { viewModel.setTextSize(viewModel.textSizePt + 2f) },
        )
    }
}

/** Previous / next page navigation with a page indicator. */
@Composable
fun PageNavBar(
    currentIndex: Int,
    pageCount: Int,
    canPrevious: Boolean,
    canNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButton(onClick = onPrevious, enabled = canPrevious) { Text("Prev") }
        Text("Page ${currentIndex + 1} / $pageCount")
        OutlinedButton(onClick = onNext, enabled = canNext) { Text("Next") }
    }
}

@Composable
private fun ColorSwatches(
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        PALETTE.forEach { argb ->
            ColorSwatch(
                argb = argb,
                isSelected = argb == selected,
                onClick = { onSelect(argb) },
            )
        }
    }
}

@Composable
private fun ColorSwatch(
    argb: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .size(28.dp)
                .background(Color(argb), CircleShape)
                .border(
                    width = if (isSelected) 3.dp else 1.dp,
                    color = if (isSelected) Color.White else Color.Gray,
                    shape = CircleShape,
                ).clickable(onClick = onClick),
    )
}

@Composable
private fun Stepper(
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onMinus) { Text("−") }
        Text(value)
        TextButton(onClick = onPlus) { Text("+") }
    }
}
