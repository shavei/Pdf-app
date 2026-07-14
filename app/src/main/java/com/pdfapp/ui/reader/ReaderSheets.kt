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

/** Page-thumbnail grid for jump navigation (plan 2.1). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThumbnailSheet(
    viewModel: PdfEditorViewModel,
    onDismiss: () -> Unit,
) {
    val session = viewModel.session ?: return
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 104.dp),
            modifier = Modifier.padding(horizontal = 12.dp),
        ) {
            items(count = session.pageCount) { index ->
                ThumbnailCell(
                    viewModel = viewModel,
                    pageIndex = index,
                    isCurrent = index == viewModel.currentPageIndex,
                    onClick = {
                        viewModel.goToPage(index)
                        onDismiss()
                    },
                )
            }
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

/** Document outline / table of contents drawer (plan 2.4). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutlineSheet(
    viewModel: PdfEditorViewModel,
    onDismiss: () -> Unit,
) {
    LaunchedEffect(Unit) { viewModel.loadOutline() }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        when (val outline = viewModel.outline) {
            null ->
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                ) { CircularProgressIndicator() }
            else ->
                if (outline.isEmpty()) {
                    Text(
                        "No outline in this document",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(32.dp),
                    )
                } else {
                    OutlineList(outline) { entry ->
                        viewModel.goToPage(entry.pageIndex)
                        onDismiss()
                    }
                }
        }
    }
}

@Composable
private fun OutlineList(
    outline: List<OutlineEntry>,
    onEntryClick: (OutlineEntry) -> Unit,
) {
    LazyColumn(modifier = Modifier.padding(bottom = 16.dp)) {
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
private const val OUTLINE_INDENT_DP = 16
