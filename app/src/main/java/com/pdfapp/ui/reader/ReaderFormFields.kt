package com.pdfapp.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.pdfapp.core.renderer.form.FormFieldKind
import com.pdfapp.core.renderer.form.PdfFormField
import com.pdfapp.ui.FormController
import com.pdfapp.ui.FormSemantics
import com.pdfapp.ui.PdfEditorViewModel
import kotlin.math.roundToInt

/**
 * The fill-form layer for one reader page (plan Phase 4): a native Compose input
 * per AcroForm widget, laid over the rendered page.
 *
 * Positions come from the widget's PDF-space rectangle scaled by the page's
 * current [pointScale] — the same points-to-pixels path as search highlights —
 * so a field stays glued to its box through zoom, night mode and rotation. The
 * layer is only composed while [FormController.active], which keeps text fields
 * from stealing the reader's pan, tap and long-press gestures the rest of the
 * time.
 */
@Composable
internal fun FormFieldLayer(
    viewModel: PdfEditorViewModel,
    pageIndex: Int,
    pointScale: Float,
    pageHeightPt: Float,
) {
    val controller = viewModel.formController
    if (!controller.active) return
    val fields = controller.fieldsByPage[pageIndex].orEmpty()
    if (fields.isEmpty()) return
    val density = LocalDensity.current

    Box(modifier = Modifier.fillMaxSize()) {
        for (field in fields) {
            // The generation key rebuilds the inputs after a Reset, so a focused
            // text field cannot sync its stale IME buffer back over the values
            // the reset just restored.
            key(field.widgetId, controller.generation) {
                val widthPx = field.box.width * pointScale
                val heightPx = field.box.height * pointScale
                val left = field.box.left * pointScale
                val top = (pageHeightPt - field.box.top) * pointScale
                Box(
                    modifier =
                        Modifier
                            .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
                            .size(
                                width = with(density) { widthPx.toDp() },
                                height = with(density) { heightPx.toDp() },
                            ),
                ) {
                    FormFieldInput(
                        controller = controller,
                        field = field,
                        heightPx = heightPx,
                        pointScale = pointScale,
                    )
                }
            }
        }
    }
}

/** One widget's input, chosen by its [PdfFormField.kind]. */
@Composable
private fun FormFieldInput(
    controller: FormController,
    field: PdfFormField,
    heightPx: Float,
    pointScale: Float,
) {
    when (field.kind) {
        FormFieldKind.TEXT -> TextFieldInput(controller, field, heightPx, pointScale)
        FormFieldKind.CHECKBOX -> CheckBoxInput(controller, field)
        FormFieldKind.RADIO -> RadioInput(controller, field)
        FormFieldKind.CHOICE -> ChoiceInput(controller, field, heightPx, pointScale)
    }
}

@Composable
private fun TextFieldInput(
    controller: FormController,
    field: PdfFormField,
    heightPx: Float,
    pointScale: Float,
) {
    val value = controller.valueOf(field)
    val label = FormSemantics.fieldLabel(field, value)
    val style = fieldTextStyle(field, heightPx, pointScale)
    if (field.readOnly) {
        // A read-only field is still worth showing — it is part of the form the
        // user is reading — but it must not offer a caret.
        Text(
            text = value,
            style = style,
            maxLines = if (field.multiline) Int.MAX_VALUE else 1,
            overflow = TextOverflow.Clip,
            modifier = fieldSurface(field).fieldSemantics { contentDescription = label },
        )
        return
    }
    BasicTextField(
        value = value,
        onValueChange = { controller.setValue(field, it) },
        singleLine = !field.multiline,
        textStyle = style,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        modifier = fieldSurface(field).fieldSemantics { contentDescription = label },
    )
}

@Composable
private fun CheckBoxInput(
    controller: FormController,
    field: PdfFormField,
) {
    val on = controller.isOn(field)
    ToggleMark(
        field = field,
        on = on,
        icon = Icons.Filled.Check,
        label = FormSemantics.checkBoxLabel(field, on),
        role = Role.Checkbox,
        onClick = { controller.toggle(field) },
    )
}

@Composable
private fun RadioInput(
    controller: FormController,
    field: PdfFormField,
) {
    val on = controller.isOn(field)
    ToggleMark(
        field = field,
        on = on,
        icon = Icons.Filled.Circle,
        label = FormSemantics.radioLabel(field, on),
        role = Role.RadioButton,
        onClick = { controller.select(field) },
    )
}

/**
 * Shared body of the two tickable widgets: the field box with a centred mark
 * when set. The mark is drawn inside the PDF's own rectangle rather than as a
 * Material [androidx.compose.material3.Checkbox], which carries 48 dp of its own
 * padding and would not sit inside a 10 pt box.
 */
@Composable
private fun ToggleMark(
    field: PdfFormField,
    on: Boolean,
    icon: ImageVector,
    label: String,
    role: Role,
    onClick: () -> Unit,
) {
    val enabled = !field.readOnly
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            fieldSurface(field)
                .then(
                    if (enabled) {
                        Modifier.clickable(role = role, onClick = onClick)
                    } else {
                        Modifier
                    },
                ).fieldSemantics {
                    contentDescription = label
                    toggleableState = if (on) ToggleableState.On else ToggleableState.Off
                },
    ) {
        if (on) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxSize().padding(1.dp),
            )
        }
    }
}

@Composable
private fun ChoiceInput(
    controller: FormController,
    field: PdfFormField,
    heightPx: Float,
    pointScale: Float,
) {
    var open by remember { mutableStateOf(false) }
    val value = controller.valueOf(field)
    val shown = field.options.firstOrNull { it.value == value }?.label ?: value
    val label = FormSemantics.choiceLabel(field, shown)
    val selectable = !field.readOnly && field.options.isNotEmpty()
    Box {
        Box(
            contentAlignment = Alignment.CenterStart,
            modifier =
                fieldSurface(field)
                    .then(
                        if (selectable) {
                            Modifier.clickable(role = Role.DropdownList) { open = true }
                        } else {
                            Modifier
                        },
                    ).fieldSemantics { contentDescription = label },
        ) {
            Text(
                text = shown,
                style = fieldTextStyle(field, heightPx, pointScale),
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
            // No caret on a field that cannot be opened — it would promise a
            // menu that never appears.
            if (selectable) {
                val caret = minOf(with(LocalDensity.current) { heightPx.toDp() }, CHOICE_CARET_MAX)
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.CenterEnd).size(caret),
                )
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for (option in field.options) {
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        open = false
                        controller.setValue(field, option.value)
                    },
                )
            }
        }
    }
}

/**
 * Every widget's semantics, and the reason they are all *merging* nodes: the
 * page they sit on merges its descendants so a screen reader can speak a page as
 * one utterance (`ReaderSemantics.pageLabel`). Merging stops at a descendant that
 * merges in its own right — so without this, a form input's label would be
 * swallowed into the page's description instead of being a control the user can
 * reach and act on.
 */
private fun Modifier.fieldSemantics(properties: SemanticsPropertyReceiver.() -> Unit): Modifier =
    semantics(mergeDescendants = true, properties = properties)

/**
 * The tint every widget shares: a translucent wash plus a hairline border, so a
 * fillable box is visible against the page without hiding the label printed
 * under it. Read-only fields get the muted variant.
 */
@Composable
private fun fieldSurface(field: PdfFormField): Modifier {
    val accent = MaterialTheme.colorScheme.primary
    val tint = if (field.readOnly) READ_ONLY_FILL else FILL_ALPHA
    return Modifier
        .fillMaxSize()
        .background(accent.copy(alpha = tint))
        .border(1.dp, accent.copy(alpha = BORDER_ALPHA))
        .padding(horizontal = 2.dp)
}

/**
 * Text sized to match the page. A field with an explicit default-appearance size
 * scales with the document; an auto-sizing one (PDF's "size 0") is fitted to the
 * widget's own height, the same rule a viewer's appearance generator uses.
 */
@Composable
private fun fieldTextStyle(
    field: PdfFormField,
    heightPx: Float,
    pointScale: Float,
): TextStyle {
    val density = LocalDensity.current
    val sizePx =
        if (field.fontSizePt > 0f) {
            field.fontSizePt * pointScale
        } else {
            // Multiline boxes are tall by design, so their auto size comes off a
            // single line's worth rather than the whole box.
            val line = if (field.multiline) AUTO_MULTILINE_LINE_PT * pointScale else heightPx
            line * AUTO_SIZE_FRACTION
        }
    return TextStyle(
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = with(density) { sizePx.coerceAtLeast(MIN_FIELD_TEXT_PX).toSp() },
    )
}

private const val FILL_ALPHA = 0.12f
private const val BORDER_ALPHA = 0.55f
private const val READ_ONLY_FILL = 0.05f

// Fraction of a line's height the glyphs take, matching PDF appearance
// generators' own auto-size heuristic closely enough to look native.
private const val AUTO_SIZE_FRACTION = 0.66f

// A multiline field's height says nothing about its font size; fall back to a
// typical form line (in PDF points, so it scales with zoom) rather than render a
// tall comment box in 40 pt text.
private const val AUTO_MULTILINE_LINE_PT = 12f
private const val MIN_FIELD_TEXT_PX = 6f
private val CHOICE_CARET_MAX = 14.dp
