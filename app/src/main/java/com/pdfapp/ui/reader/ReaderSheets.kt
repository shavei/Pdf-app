package com.pdfapp.ui.reader

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pdfapp.core.renderer.text.OutlineEntry
import com.pdfapp.ui.PdfEditorViewModel

/**
 * Page-thumbnail grid for jump navigation (plan 2.1), as a modal sheet. On
 * expanded windows the same grid docks beside the page instead — see
 * [ThumbnailPane] (mobile-ui-plan Phase E.3).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThumbnailSheet(
    viewModel: PdfEditorViewModel,
    onDismiss: () -> Unit,
) {
    if (viewModel.session == null) return
    ModalBottomSheet(onDismissRequest = onDismiss) {
        ThumbnailGrid(viewModel) { index ->
            viewModel.goToPage(index)
            onDismiss()
        }
    }
}

/** The thumbnail grid itself, shared by [ThumbnailSheet] and [ThumbnailPane]. */
@Composable
internal fun ThumbnailGrid(
    viewModel: PdfEditorViewModel,
    modifier: Modifier = Modifier,
    onSelect: (Int) -> Unit,
) {
    val session = viewModel.session ?: return
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = THUMB_CELL_MIN_WIDTH.dp),
        modifier = modifier.padding(horizontal = 12.dp),
    ) {
        items(count = session.pageCount) { index ->
            ThumbnailCell(
                viewModel = viewModel,
                pageIndex = index,
                isCurrent = index == viewModel.currentPageIndex,
                onClick = { onSelect(index) },
            )
        }
    }
}

@Composable
@Suppress("ProduceStateDoesNotAssignValue") // value is assigned; lint misreads the multi-line producer
private fun ThumbnailCell(
    viewModel: PdfEditorViewModel,
    pageIndex: Int,
    isCurrent: Boolean,
    onClick: () -> Unit,
) {
    val session = viewModel.session ?: return
    val bitmap by produceState<ImageBitmap?>(null, session, pageIndex) {
        val size = session.cache.pageSize(pageIndex)
        val rendered = session.cache.page(pageIndex, THUMB_WIDTH_PX / size.widthPt)
        value = rendered.bitmap.asImageBitmap()
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(6.dp).clickable(onClick = onClick),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(THUMB_CELL_HEIGHT.dp)
                    .then(
                        if (isCurrent) {
                            Modifier.border(2.dp, MaterialTheme.colorScheme.primary)
                        } else {
                            Modifier
                        },
                    ),
        ) {
            bitmap?.let {
                Image(
                    bitmap = it,
                    contentDescription = "Page ${pageIndex + 1} thumbnail",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().height(THUMB_CELL_HEIGHT.dp),
                )
            } ?: CircularProgressIndicator(modifier = Modifier.height(20.dp))
        }
        Text("${pageIndex + 1}", style = MaterialTheme.typography.labelMedium)
    }
}

/**
 * Document outline / table of contents drawer (plan 2.4), as a modal sheet. The
 * docked [ThumbnailPane] offers the same list on expanded windows.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutlineSheet(
    viewModel: PdfEditorViewModel,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        OutlineContent(viewModel, onNavigate = onDismiss)
    }
}

/**
 * Outline list with its loading and empty states, shared by [OutlineSheet] and
 * [ThumbnailPane]. [onNavigate] fires after a jump so an overlaying host can
 * dismiss itself; a docked host passes a no-op.
 */
@Composable
internal fun OutlineContent(
    viewModel: PdfEditorViewModel,
    modifier: Modifier = Modifier,
    onNavigate: () -> Unit,
) {
    LaunchedEffect(viewModel.session) { viewModel.loadOutline() }
    when (val outline = viewModel.outline) {
        null ->
            Box(
                contentAlignment = Alignment.Center,
                modifier = modifier.fillMaxWidth().padding(32.dp),
            ) { CircularProgressIndicator() }
        else ->
            if (outline.isEmpty()) {
                Text(
                    "No outline in this document",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = modifier.padding(32.dp),
                )
            } else {
                OutlineList(outline, modifier) { entry ->
                    viewModel.goToPage(entry.pageIndex)
                    onNavigate()
                }
            }
    }
}

@Composable
private fun OutlineList(
    outline: List<OutlineEntry>,
    modifier: Modifier = Modifier,
    onEntryClick: (OutlineEntry) -> Unit,
) {
    LazyColumn(modifier = modifier.padding(bottom = 16.dp)) {
        items(count = outline.size) { i ->
            val entry = outline[i]
            Text(
                text = entry.title.ifBlank { "(untitled)" },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { onEntryClick(entry) }
                        .padding(
                            start = (16 + entry.depth * OUTLINE_INDENT_DP).dp,
                            end = 16.dp,
                            top = 12.dp,
                            bottom = 12.dp,
                        ),
            )
        }
    }
}

private const val THUMB_WIDTH_PX = 220f
private const val THUMB_CELL_HEIGHT = 130
private const val THUMB_CELL_MIN_WIDTH = 104
private const val OUTLINE_INDENT_DP = 16
