package com.pdfapp.ui.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.BottomAppBar
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
import androidx.compose.ui.Modifier
import com.pdfapp.ui.PdfEditorViewModel
import com.pdfapp.ui.ZoomPreset

/**
 * READ-mode bottom bar (mobile-ui-plan Phase B.2): brings the highest-frequency
 * actions down to the thumb — Search, Thumbnails, Night mode and Edit — with a
 * tap-to-jump "page X / N" chip and a [MoreVert] overflow for the long tail.
 * Visibility follows the immersive `chromeVisible` state in [com.pdfapp.ui.PdfEditorScreen].
 */
@Composable
fun ReaderBottomBar(
    viewModel: PdfEditorViewModel,
    onShowThumbnails: () -> Unit,
    onShowGoToPage: () -> Unit,
    onOpenAnother: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    BottomAppBar {
        IconButton(onClick = { viewModel.searchController.open() }) {
            Icon(Icons.Filled.Search, contentDescription = "Search in document")
        }
        IconButton(onClick = onShowThumbnails) {
            Icon(Icons.Filled.GridView, contentDescription = "Page thumbnails")
        }
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
        Spacer(Modifier.weight(1f))
        if (viewModel.pageCount > 0) {
            TextButton(onClick = onShowGoToPage) {
                Text(
                    "${viewModel.currentPageIndex + 1} / ${viewModel.pageCount}",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = { viewModel.enterEditMode() }) {
            Icon(Icons.Filled.Edit, contentDescription = "Edit document")
        }
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
}

/** Long-tail reader actions that don't earn a permanent bottom-bar slot. */
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
