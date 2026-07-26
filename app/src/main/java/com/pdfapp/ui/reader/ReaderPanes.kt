package com.pdfapp.ui.reader

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pdfapp.ui.document.PdfEditorViewModel

/**
 * Navigation pane docked beside the page on expanded windows (mobile-ui-plan
 * Phase E.3): the page thumbnails and the document outline, permanently visible
 * instead of overlaying the page as a modal sheet. Both lists are the same ones
 * the compact sheets use ([ThumbnailGrid], [OutlineContent]).
 *
 * Jumping to a page leaves the pane open — that is the point of docking it — so
 * the reader can walk a long document without reopening a sheet each time.
 */
@Composable
fun ThumbnailPane(
    viewModel: PdfEditorViewModel,
    modifier: Modifier = Modifier,
) {
    var showOutline by remember { mutableStateOf(false) }
    Surface(
        tonalElevation = 2.dp,
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.width(ReaderLayout.PANE_WIDTH_DP.dp).fillMaxHeight(),
    ) {
        Column {
            TabRow(selectedTabIndex = if (showOutline) 1 else 0) {
                Tab(
                    selected = !showOutline,
                    onClick = { showOutline = false },
                    text = { Text("Pages") },
                )
                Tab(
                    selected = showOutline,
                    onClick = { showOutline = true },
                    text = { Text("Outline") },
                )
            }
            if (showOutline) {
                OutlineContent(
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxWidth(),
                    onNavigate = {},
                )
            } else {
                ThumbnailGrid(viewModel) { index -> viewModel.goToPage(index) }
            }
        }
    }
}
