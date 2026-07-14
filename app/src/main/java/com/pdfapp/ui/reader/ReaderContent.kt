package com.pdfapp.ui.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pdfapp.ui.PdfEditorViewModel
import kotlinx.coroutines.launch

/**
 * READ-mode body: the one-page-at-a-time reader plus its floating chrome — the
 * "Page X / N" chip (tap to jump) and the copy bar for an active selection.
 */
@Composable
fun ReaderContent(
    viewModel: PdfEditorViewModel,
    snackbarHostState: SnackbarHostState,
    onShowGoToPage: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        ReaderView(
            viewModel = viewModel,
            modifier = Modifier.fillMaxSize(),
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
        ) {
            SelectionCopyBar(viewModel, snackbarHostState)
            PageChip(
                currentPage = viewModel.currentPageIndex,
                pageCount = viewModel.pageCount,
                onClick = onShowGoToPage,
            )
        }
    }
}

@Composable
private fun PageChip(
    currentPage: Int,
    pageCount: Int,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.secondaryContainer,
        tonalElevation = 3.dp,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            text = "Page ${currentPage + 1} / $pageCount",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

/** Floating bar offering to copy the current text selection (plan 2.3). */
@Composable
private fun SelectionCopyBar(
    viewModel: PdfEditorViewModel,
    snackbarHostState: SnackbarHostState,
) {
    val selection = viewModel.selectionController.selection ?: return
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    Surface(
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 4.dp,
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
            Text(
                text = selection.text.replace('\n', ' '),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = {
                    clipboard.setText(AnnotatedString(selection.text))
                    viewModel.selectionController.clear()
                    scope.launch { snackbarHostState.showSnackbar("Copied to clipboard") }
                },
            ) { Text("Copy") }
            IconButton(onClick = { viewModel.selectionController.clear() }) {
                Icon(Icons.Filled.Close, contentDescription = "Dismiss selection")
            }
        }
    }
}
