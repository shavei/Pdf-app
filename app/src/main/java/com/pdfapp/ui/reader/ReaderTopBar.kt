package com.pdfapp.ui.reader

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import com.pdfapp.ui.common.ReaderSemantics
import com.pdfapp.ui.common.iconTouchTarget
import com.pdfapp.ui.document.PdfEditorViewModel
import com.pdfapp.ui.document.SearchController
import kotlinx.coroutines.delay

/**
 * Reader app bar; swaps to an in-document search field when search is open.
 * The high-frequency actions moved to the thumb-reachable [ReaderBottomBar]
 * (mobile-ui-plan Phase B), so the title bar keeps only the document name and
 * the outline drawer.
 */
@Composable
fun ReaderTopBar(
    viewModel: PdfEditorViewModel,
    onShowOutline: () -> Unit,
) {
    val search = viewModel.searchController
    if (search.active) {
        SearchTopBar(search)
    } else {
        TitleTopBar(viewModel, onShowOutline)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TitleTopBar(
    viewModel: PdfEditorViewModel,
    onShowOutline: () -> Unit,
) {
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
            IconButton(onClick = onShowOutline, modifier = Modifier.iconTouchTarget()) {
                Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Outline")
            }
        },
    )
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
            IconButton(onClick = { search.close() }, modifier = Modifier.iconTouchTarget()) {
                Icon(Icons.Filled.Close, contentDescription = "Close search")
            }
        },
        title = {
            TextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                placeholder = { Text("Search document") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                // Enter still owed a debounce runs that query; otherwise it steps
                // to the next hit, the way a browser's find bar does.
                keyboardActions =
                    KeyboardActions(
                        onSearch = { if (text != search.query) search.submit(text) else search.next() },
                    ),
                colors =
                    TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    ),
                modifier = Modifier.fillMaxWidth().imePadding().focusRequester(focusRequester),
            )
        },
        actions = {
            // The counter is terse because it shares a crowded bar; TalkBack gets
            // the long form ("Match 3 of 12") instead (mobile-ui-plan Phase F.2).
            val counter =
                ReaderSemantics.searchCounterText(
                    currentIndex = search.currentIndex,
                    matchCount = search.matches.size,
                    searching = search.searching,
                    hasQuery = search.query.isNotBlank(),
                    truncated = search.truncated,
                )
            val counterLabel =
                ReaderSemantics.searchCounterLabel(
                    currentIndex = search.currentIndex,
                    matchCount = search.matches.size,
                    searching = search.searching,
                    hasQuery = search.query.isNotBlank(),
                    truncated = search.truncated,
                )
            if (counter.isNotEmpty()) {
                Text(
                    counter,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.semantics { contentDescription = counterLabel },
                )
            }
            IconButton(
                onClick = { search.previous() },
                enabled = search.matches.isNotEmpty(),
                modifier = Modifier.iconTouchTarget(),
            ) {
                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Previous match")
            }
            IconButton(
                onClick = { search.next() },
                enabled = search.matches.isNotEmpty(),
                modifier = Modifier.iconTouchTarget(),
            ) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Next match")
            }
        },
    )
}

private const val SEARCH_DEBOUNCE_MS = 300L
