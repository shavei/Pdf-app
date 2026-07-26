package com.pdfapp.ui.reader

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pdfapp.ui.FormSemantics
import com.pdfapp.ui.PdfEditorViewModel
import com.pdfapp.ui.iconTouchTarget
import com.pdfapp.ui.touchTargetFloor

/**
 * READ-mode action that turns the fill-form layer on (plan Phase 4). Absent
 * until the background field scan has actually found a form, so a plain document
 * never grows a control it cannot use.
 */
@Composable
internal fun FillFormAction(viewModel: PdfEditorViewModel) {
    if (!viewModel.formController.hasForm) return
    IconButton(
        onClick = { viewModel.formController.open() },
        modifier = Modifier.iconTouchTarget(),
    ) {
        Icon(
            Icons.AutoMirrored.Filled.FactCheck,
            contentDescription = "Fill form",
            tint =
                if (viewModel.formController.active) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
        )
    }
}

/**
 * The floating bar shown while filling a form: how much has been filled, Reset,
 * a two-option Save, and a close button.
 *
 * It floats over the reader content — like the selection copy bar — rather than
 * taking the Scaffold's bottom slot, so it reaches the user identically on a
 * phone (bottom bar) and on a tablet (side rail), where that slot is empty.
 */
@Composable
internal fun FormFillBar(
    viewModel: PdfEditorViewModel,
    onSave: (flatten: Boolean) -> Unit,
) {
    val controller = viewModel.formController
    if (!controller.active) return
    val fieldCount = controller.fields?.size ?: 0
    var saveMenuOpen by remember { mutableStateOf(false) }
    val status = FormSemantics.statusText(fieldCount, controller.editedCount)
    val statusLabel = FormSemantics.statusLabel(fieldCount, controller.editedCount)

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
                text = status,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f).semantics { contentDescription = statusLabel },
            )
            if (controller.isDirty) {
                TextButton(
                    onClick = { controller.reset() },
                    modifier = Modifier.touchTargetFloor(),
                ) { Text("Reset") }
            }
            TextButton(
                onClick = { saveMenuOpen = true },
                enabled = fieldCount > 0,
                modifier = Modifier.touchTargetFloor(),
            ) { Text("Save") }
            DropdownMenu(expanded = saveMenuOpen, onDismissRequest = { saveMenuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Save filled form") },
                    onClick = {
                        saveMenuOpen = false
                        onSave(false)
                    },
                )
                DropdownMenuItem(
                    text = { Text("Save flattened (not editable)") },
                    onClick = {
                        saveMenuOpen = false
                        onSave(true)
                    },
                )
            }
            IconButton(
                onClick = { controller.hide() },
                modifier = Modifier.iconTouchTarget(),
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Close form filling")
            }
        }
    }
}
