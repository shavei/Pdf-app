package com.pdfapp.ui.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextOverflow
import com.pdfapp.ui.PdfEditorViewModel
import com.pdfapp.ui.SearchController
import com.pdfapp.ui.ZoomPreset
import kotlinx.coroutines.delay

/** Reader app bar; swaps to an in-document search field when search is open. */
@Composable
fun ReaderTopBar(
    viewModel: PdfEditorViewModel,
    onShowThumbnails: () -> Unit,
    onShowOutline: () -> Unit,
    onShowGoToPage: () -> Unit,
    onOpenAnother: () -> Unit,
) {
    val search = viewModel.searchController
    if (search.active) {
        SearchTopBar(search)
    } else {
        TitleTopBar(viewModel, onShowThumbnails, onShowOutline, onShowGoToPage, onOpenAnother)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TitleTopBar(
    viewModel: PdfEditorViewModel,
    onShowThumbnails: () -> Unit,
    onShowOutline: () -> Unit,
    onShowGoToPage: () -> Unit,
    onOpenAnother: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    TopAppBar(
        title = {
            Text(
                viewModel.session?.displayName ?: "",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium,
            )
        },
        actions = {
            IconButton(onClick = { viewModel.searchController.open() }) {
                Icon(Icons.Filled.Search, contentDescription = "Search in document")
            }
            IconButton(onClick = onShowOutline) {
                Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Outline")
            }
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
                    onShowThumbnails = onShowThumbnails,
                    onShowGoToPage = onShowGoToPage,
                    onOpenAnother = onOpenAnother,
                )
            }
        },
    )
}

@Composable
private fun ReaderMenu(
    expanded: Boolean,
    viewModel: PdfEditorViewModel,
    onDismiss: () -> Unit,
    onShowThumbnails: () -> Unit,
    onShowGoToPage: () -> Unit,
    onOpenAnother: () -> Unit,
) {
    fun act(action: () -> Unit): () -> Unit =
        {
            onDismiss()
            action()
        }
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(text = { Text("Page thumbnails") }, onClick = act(onShowThumbnails))
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
            text = { Text("Night mode") },
            trailingIcon = { if (viewModel.nightMode) Icon(Icons.Filled.Check, null) },
            onClick = act { viewModel.toggleNightMode() },
        )
        DropdownMenuItem(
            text = { Text("Keep screen on") },
            trailingIcon = { if (viewModel.keepScreenOn) Icon(Icons.Filled.Check, null) },
            onClick = act { viewModel.toggleKeepScreenOn() },
        )
        DropdownMenuItem(text = { Text("Open another PDF") }, onClick = act(onOpenAnother))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchTopBar(search: SearchController) {
    var text by remember { mutableStateOf(search.query) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    LaunchedEffect(text) {
        delay(SEARCH_DEBOUNCE_MS)
        if (text != search.query) search.submit(text)
    }
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = { search.close() }) {
                Icon(Icons.Filled.Close, contentDescription = "Close search")
            }
        },
        title = {
            TextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                placeholder = { Text("Search document") },
                colors =
                    TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    ),
                modifier = Modifier.fillMaxWidth().imePadding().focusRequester(focusRequester),
            )
        },
        actions = {
            val counter =
                when {
                    search.matches.isNotEmpty() -> "${search.currentIndex + 1}/${search.matches.size}"
                    search.searching -> "…"
                    search.query.isBlank() -> ""
                    else -> "0/0"
                }
            Text(counter, style = MaterialTheme.typography.labelLarge)
            IconButton(onClick = { search.previous() }, enabled = search.matches.isNotEmpty()) {
                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Previous match")
            }
            IconButton(onClick = { search.next() }, enabled = search.matches.isNotEmpty()) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Next match")
            }
        },
    )
}

private const val SEARCH_DEBOUNCE_MS = 300L
