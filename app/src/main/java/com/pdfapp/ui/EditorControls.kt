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
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pdfapp.overlay.OverlayCanvasView

/** Ink/text colour choices offered in the settings row. */
private val PALETTE: List<Int> =
    listOf(0xFF000000L, 0xFF001A66L, 0xFFB00020L, 0xFF1B5E20L).map { it.toInt() }

/** Primary edit-mode toolbar: tool toggle, commit ink, undo, clear, save. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorToolbar(
    mode: OverlayCanvasView.Mode,
    enabled: Boolean,
    onModeChange: (OverlayCanvasView.Mode) -> Unit,
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
        SingleChoiceSegmentedButtonRow {
            SegmentedButton(
                selected = mode == OverlayCanvasView.Mode.INK,
                onClick = { onModeChange(OverlayCanvasView.Mode.INK) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                enabled = enabled,
            ) { Text("Sign") }
            SegmentedButton(
                selected = mode == OverlayCanvasView.Mode.TEXT,
                onClick = { onModeChange(OverlayCanvasView.Mode.TEXT) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                enabled = enabled,
            ) { Text("Text") }
            SegmentedButton(
                selected = mode == OverlayCanvasView.Mode.EDIT,
                onClick = { onModeChange(OverlayCanvasView.Mode.EDIT) },
                shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                enabled = enabled,
            ) { Text("Edit") }
        }
        OutlinedButton(onClick = onCommitInk, enabled = enabled) { Text("Apply ink") }
        OutlinedButton(onClick = onUndo, enabled = enabled) { Text("Undo") }
        OutlinedButton(onClick = onClear, enabled = enabled) { Text("Clear") }
        Button(onClick = onSave, enabled = enabled) { Text("Save") }
    }
}

/** Tool colour/size controls for the ink and text tools. */
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
        ColorSwatches(
            selected = viewModel.inkColorArgb,
            onSelect = viewModel::setInkColor,
            label = "Ink",
        )
        Stepper(
            value = "${viewModel.inkStrokeWidthPt.toInt()}pt",
            onMinus = { viewModel.setInkStrokeWidth(viewModel.inkStrokeWidthPt - 1f) },
            onPlus = { viewModel.setInkStrokeWidth(viewModel.inkStrokeWidthPt + 1f) },
            minusDescription = "Thinner ink stroke",
            plusDescription = "Thicker ink stroke",
        )
        Text("Text")
        ColorSwatches(
            selected = viewModel.textColorArgb,
            onSelect = viewModel::setTextColor,
            label = "Text",
        )
        Stepper(
            value = "${viewModel.textSizePt.toInt()}pt",
            onMinus = { viewModel.setTextSize(viewModel.textSizePt - 2f) },
            onPlus = { viewModel.setTextSize(viewModel.textSizePt + 2f) },
            minusDescription = "Smaller text",
            plusDescription = "Larger text",
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
    label: String,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        PALETTE.forEachIndexed { index, argb ->
            ColorSwatch(
                argb = argb,
                isSelected = argb == selected,
                onClick = { onSelect(argb) },
                contentDescription = "$label colour ${index + 1}",
            )
        }
    }
}

@Composable
internal fun ColorSwatch(
    argb: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
    contentDescription: String? = null,
) {
    // 48 dp touch box (accessibility floor) around a 28 dp visual dot.
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .clickable(onClick = onClick)
                .then(
                    if (contentDescription != null) {
                        Modifier.semantics { this.contentDescription = contentDescription }
                    } else {
                        Modifier
                    },
                ),
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
                    ),
        )
    }
}

@Composable
internal fun Stepper(
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    minusDescription: String = "Decrease",
    plusDescription: String = "Increase",
) {
    // IconButton gives each control a 48 dp touch target without enlarging the glyph.
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = onMinus,
            modifier = Modifier.semantics { contentDescription = minusDescription },
        ) { Text("−") }
        Text(value)
        IconButton(
            onClick = onPlus,
            modifier = Modifier.semantics { contentDescription = plusDescription },
        ) { Text("+") }
    }
}
