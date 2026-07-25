package com.pdfapp.ui.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.pdfapp.ui.PdfEditorViewModel
import com.pdfapp.ui.ZoomPreset

/**
 * The READ-mode primary actions, as individual controls rather than one bar.
 *
 * [ReaderBottomBar] lays them out horizontally for compact phone portrait and
 * [ReaderNavRail] vertically for short or wide windows (mobile-ui-plan Phase E),
 * so both surfaces share one definition — and one set of content descriptions.
 */
@Composable
internal fun SearchAction(viewModel: PdfEditorViewModel) {
    IconButton(onClick = { viewModel.searchController.open() }) {
        Icon(Icons.Filled.Search, contentDescription = "Search in document")
    }
}

@Composable
internal fun ThumbnailsAction(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(Icons.Filled.GridView, contentDescription = "Page thumbnails")
    }
}

@Composable
internal fun NightModeAction(viewModel: PdfEditorViewModel) {
    IconButton(onClick = { viewModel.toggleNightMode() }) {
        Icon(
            Icons.Filled.DarkMode,
            contentDescription = "Night mode",
            tint =
                if (viewModel.nightMode) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
        )
    }
}

/** Tap-to-jump "page X / N" chip; absent until a document reports its page count. */
@Composable
internal fun PageChip(
    viewModel: PdfEditorViewModel,
    onClick: () -> Unit,
) {
    if (viewModel.pageCount <= 0) return
    TextButton(onClick = onClick) {
        Text(
            "${viewModel.currentPageIndex + 1} / ${viewModel.pageCount}",
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
internal fun EditAction(viewModel: PdfEditorViewModel) {
    IconButton(onClick = { viewModel.enterEditMode() }) {
        Icon(Icons.Filled.Edit, contentDescription = "Edit document")
    }
}

/** The overflow button plus its anchored long-tail menu. */
@Composable
internal fun OverflowAction(
    viewModel: PdfEditorViewModel,
    onShowGoToPage: () -> Unit,
    onOpenAnother: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { menuOpen = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = "More options")
        }
        ReaderMenu(
            expanded = menuOpen,
            viewModel = viewModel,
            onDismiss = { menuOpen = false },
            onShowGoToPage = onShowGoToPage,
            onOpenAnother = onOpenAnother,
        )
    }
}

/** Long-tail reader actions that don't earn a permanent slot on the bar or rail. */
@Composable
private fun ReaderMenu(
    expanded: Boolean,
    viewModel: PdfEditorViewModel,
    onDismiss: () -> Unit,
    onShowGoToPage: () -> Unit,
    onOpenAnother: () -> Unit,
) {
    fun act(action: () -> Unit): () -> Unit =
        {
            onDismiss()
            action()
        }
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(text = { Text("Go to page") }, onClick = act(onShowGoToPage))
        DropdownMenuItem(
            text = { Text("Fit width") },
            onClick = act { viewModel.requestZoomPreset(ZoomPreset.FIT_WIDTH) },
        )
        DropdownMenuItem(
            text = { Text("Fit page") },
            onClick = act { viewModel.requestZoomPreset(ZoomPreset.FIT_PAGE) },
        )
        DropdownMenuItem(
            text = { Text("Keep screen on") },
            trailingIcon = { if (viewModel.keepScreenOn) Icon(Icons.Filled.Check, null) },
            onClick = act { viewModel.toggleKeepScreenOn() },
        )
        DropdownMenuItem(text = { Text("Open another PDF") }, onClick = act(onOpenAnother))
    }
}
