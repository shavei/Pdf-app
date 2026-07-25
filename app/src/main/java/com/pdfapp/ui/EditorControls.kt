package com.pdfapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pdfapp.overlay.OverlayCanvasView

/** Ink/text colour choices offered in the tool-settings sheet. */
private val PALETTE: List<Int> =
    listOf(0xFF000000L, 0xFF001A66L, 0xFFB00020L, 0xFF1B5E20L).map { it.toInt() }

/**
 * Edit-mode bottom bar (mobile-ui-plan Phase C.1 + C.3): an icon-first
 * [BottomAppBar] that always fits — no horizontal scroll. The three tools
 * (Sign / Text / Select) are icon toggles and **Undo** is always visible; a
 * tap-to-jump "page X / N" chip folds the old `PageNavBar` in (C.3). The
 * on-demand tool settings (C.2), single-step page moves, and the
 * destructive **Clear** / **Apply ink** live in a [MoreVert] overflow so the
 * bar stays narrow enough for a small phone and `Clear` can't be hit by
 * accident. **Save** is a prominent trailing `FloatingActionButton` owned by
 * the screen's Scaffold, so it never crowds this row.
 */
@Composable
fun EditBottomBar(
    mode: OverlayCanvasView.Mode,
    enabled: Boolean,
    currentIndex: Int,
    pageCount: Int,
    canPrevious: Boolean,
    canNext: Boolean,
    onModeChange: (OverlayCanvasView.Mode) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onShowGoToPage: () -> Unit,
    onShowSettings: () -> Unit,
    onUndo: () -> Unit,
    onCommitInk: () -> Unit,
    onClear: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current

    // A light tick confirms a tool switch without pulling the eye off the page
    // (mobile-ui-plan Phase D.2). Only a real change buzzes — re-tapping the
    // active tool is silent.
    fun switchTo(target: OverlayCanvasView.Mode) {
        if (target != mode) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onModeChange(target)
    }
    BottomAppBar {
        ToolToggle(
            icon = Icons.Filled.Draw,
            description = "Sign tool",
            selected = mode == OverlayCanvasView.Mode.INK,
            enabled = enabled,
            onClick = { switchTo(OverlayCanvasView.Mode.INK) },
        )
        ToolToggle(
            icon = Icons.Filled.TextFields,
            description = "Text tool",
            selected = mode == OverlayCanvasView.Mode.TEXT,
            enabled = enabled,
            onClick = { switchTo(OverlayCanvasView.Mode.TEXT) },
        )
        ToolToggle(
            icon = Icons.Filled.OpenWith,
            description = "Select and move tool",
            selected = mode == OverlayCanvasView.Mode.EDIT,
            enabled = enabled,
            onClick = { switchTo(OverlayCanvasView.Mode.EDIT) },
        )
        Spacer(Modifier.weight(1f))
        if (pageCount > 0) {
            // Same spoken label and 48 dp floor as the reader's page chip
            // (mobile-ui-plan Phase F.2/F.3).
            val chipLabel = ReaderSemantics.pageChipLabel(currentIndex, pageCount)
            TextButton(
                onClick = onShowGoToPage,
                enabled = enabled,
                modifier = Modifier.touchTargetFloor().semantics { contentDescription = chipLabel },
            ) {
                Text(
                    "${currentIndex + 1} / $pageCount",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
        IconButton(onClick = onUndo, enabled = enabled) {
            Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
        }
        Box {
            IconButton(onClick = { menuOpen = true }, enabled = enabled) {
                Icon(Icons.Filled.MoreVert, contentDescription = "More edit options")
            }
            EditOverflowMenu(
                expanded = menuOpen,
                canPrevious = canPrevious,
                canNext = canNext,
                onDismiss = { menuOpen = false },
                onShowSettings = onShowSettings,
                onPrevious = onPrevious,
                onNext = onNext,
                onCommitInk = onCommitInk,
                onClear = onClear,
            )
        }
    }
}

/** A tinted icon toggle for one edit tool; tint tracks the active selection. */
@Composable
private fun ToolToggle(
    icon: ImageVector,
    description: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(
            icon,
            contentDescription = description,
            tint =
                if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
        )
    }
}

/** Long-tail edit actions: tool settings, page moves, and the rare/destructive ones. */
@Composable
private fun EditOverflowMenu(
    expanded: Boolean,
    canPrevious: Boolean,
    canNext: Boolean,
    onDismiss: () -> Unit,
    onShowSettings: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onCommitInk: () -> Unit,
    onClear: () -> Unit,
) {
    fun act(action: () -> Unit): () -> Unit =
        {
            onDismiss()
            action()
        }
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(text = { Text("Tool settings") }, onClick = act(onShowSettings))
        DropdownMenuItem(
            text = { Text("Previous page") },
            enabled = canPrevious,
            onClick = act(onPrevious),
        )
        DropdownMenuItem(
            text = { Text("Next page") },
            enabled = canNext,
            onClick = act(onNext),
        )
        DropdownMenuItem(text = { Text("Apply ink") }, onClick = act(onCommitInk))
        DropdownMenuItem(text = { Text("Clear page") }, onClick = act(onClear))
    }
}

/**
 * Contextual tool settings (mobile-ui-plan Phase C.2): a [ModalBottomSheet]
 * scoped to the active tool, replacing the always-present settings row. Colour
 * swatches keep their 48 dp touch box (Phase A) and gain room to breathe.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolSettingsSheet(
    viewModel: PdfEditorViewModel,
    mode: OverlayCanvasView.Mode,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (mode) {
                OverlayCanvasView.Mode.INK -> {
                    Text("Ink colour", style = MaterialTheme.typography.titleSmall)
                    ColorSwatches(
                        selected = viewModel.inkColorArgb,
                        onSelect = viewModel::setInkColor,
                        label = "Ink",
                    )
                    Text("Stroke width", style = MaterialTheme.typography.titleSmall)
                    Stepper(
                        value = "${viewModel.inkStrokeWidthPt.toInt()}pt",
                        onMinus = { viewModel.setInkStrokeWidth(viewModel.inkStrokeWidthPt - 1f) },
                        onPlus = { viewModel.setInkStrokeWidth(viewModel.inkStrokeWidthPt + 1f) },
                        minusDescription = "Thinner ink stroke",
                        plusDescription = "Thicker ink stroke",
                    )
                }
                OverlayCanvasView.Mode.TEXT -> {
                    Text("Text colour", style = MaterialTheme.typography.titleSmall)
                    ColorSwatches(
                        selected = viewModel.textColorArgb,
                        onSelect = viewModel::setTextColor,
                        label = "Text",
                    )
                    Text("Text size", style = MaterialTheme.typography.titleSmall)
                    Stepper(
                        value = "${viewModel.textSizePt.toInt()}pt",
                        onMinus = { viewModel.setTextSize(viewModel.textSizePt - 2f) },
                        onPlus = { viewModel.setTextSize(viewModel.textSizePt + 2f) },
                        minusDescription = "Smaller text",
                        plusDescription = "Larger text",
                    )
                }
                OverlayCanvasView.Mode.EDIT ->
                    Text(
                        "Tap an overlay on the page to move, edit, or delete it.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
            }
        }
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
    // A 48 dp IconButton meets the touch-target floor in both visual and layout
    // bounds (the Material3 default is only 40 dp) without enlarging the glyph.
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = onMinus,
            modifier = Modifier.size(48.dp).semantics { contentDescription = minusDescription },
        ) { Text("−") }
        Text(value)
        IconButton(
            onClick = onPlus,
            modifier = Modifier.size(48.dp).semantics { contentDescription = plusDescription },
        ) { Text("+") }
    }
}
