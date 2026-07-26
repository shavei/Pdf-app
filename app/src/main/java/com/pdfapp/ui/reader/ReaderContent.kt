package com.pdfapp.ui.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pdfapp.ui.common.ReaderSemantics
import com.pdfapp.ui.common.iconTouchTarget
import com.pdfapp.ui.common.touchTargetFloor
import com.pdfapp.ui.document.PdfEditorViewModel
import kotlinx.coroutines.launch

/**
 * READ-mode body: the continuous Drive-style reader plus its floating chrome —
 * the copy bar for an active selection and the fill bar for an active form. The
 * transient page bubble lives on the fast scroller inside [ReaderView].
 */
@Composable
fun ReaderContent(
    viewModel: PdfEditorViewModel,
    snackbarHostState: SnackbarHostState,
    chromeVisible: Boolean,
    onToggleChrome: () -> Unit,
    onSaveForm: (flatten: Boolean) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        ReaderView(
            viewModel = viewModel,
            chromeVisible = chromeVisible,
            onToggleChrome = onToggleChrome,
            modifier = Modifier.fillMaxSize(),
        )
        Column(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    // The fill bar carries Save, Reset and Close for a form whose
                    // fields are typed into — so the soft keyboard would otherwise
                    // come up over the one control that finishes the job. Riding
                    // above the IME keeps it reachable while a field has focus.
                    .imePadding()
                    .navigationBarsPadding()
                    .padding(12.dp),
        ) {
            SelectionCopyBar(viewModel, snackbarHostState)
            FormFillBar(viewModel, onSaveForm)
        }
    }
}

/** Floating bar offering to copy the current text selection. */
@Composable
private fun SelectionCopyBar(
    viewModel: PdfEditorViewModel,
    snackbarHostState: SnackbarHostState,
) {
    val selection = viewModel.selectionController.selection ?: return
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    // A selection is made by dragging on the page, so nothing focuses this bar
    // when it appears: a polite live region speaks what was selected instead
    // (mobile-ui-plan Phase F.2).
    val label = ReaderSemantics.selectionLabel(selection.text)
    Surface(
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 4.dp,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
                .semantics {
                    liveRegion = LiveRegionMode.Polite
                    contentDescription = label
                },
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
                modifier = Modifier.touchTargetFloor(),
            ) { Text("Copy") }
            IconButton(
                onClick = { viewModel.selectionController.clear() },
                modifier = Modifier.iconTouchTarget(),
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Dismiss selection")
            }
        }
    }
}
