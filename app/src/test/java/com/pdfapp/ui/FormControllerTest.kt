package com.pdfapp.ui

import com.google.common.truth.Truth.assertThat
import com.pdfapp.core.renderer.form.FormFieldKind
import com.pdfapp.core.renderer.form.FormOption
import com.pdfapp.core.renderer.form.PdfFormField
import com.pdfapp.core.renderer.model.PdfRect
import kotlinx.coroutines.test.TestScope
import org.junit.Test

/**
 * The fill state machine (plan Phase 4). No document is opened here: with no
 * session, `detect()` is a no-op and everything under test is the pure value
 * bookkeeping — which is exactly the part a save depends on being right.
 */
class FormControllerTest {
    private val messages = mutableListOf<String>()

    private fun controller() = FormController(TestScope(), { null }, messages::add)

    private fun text(
        name: String = "fullName",
        value: String = "",
        readOnly: Boolean = false,
        maxLength: Int? = null,
    ) = PdfFormField(
        name = name,
        label = "",
        widgetIndex = 0,
        kind = FormFieldKind.TEXT,
        pageIndex = 0,
        box = BOX,
        value = value,
        readOnly = readOnly,
        maxLength = maxLength,
    )

    private fun checkBox(
        name: String = "agree",
        value: String = "",
        on: String = "Yes",
    ) = PdfFormField(
        name = name,
        label = "",
        widgetIndex = 0,
        kind = FormFieldKind.CHECKBOX,
        pageIndex = 0,
        box = BOX,
        value = value,
        onValue = on,
    )

    private fun radio(
        widgetIndex: Int,
        on: String,
        value: String = "",
    ) = PdfFormField(
        name = "plan",
        label = "",
        widgetIndex = widgetIndex,
        kind = FormFieldKind.RADIO,
        pageIndex = 0,
        box = BOX,
        value = value,
        onValue = on,
    )

    @Test
    fun `an untouched controller has nothing to save`() {
        val controller = controller()
        assertThat(controller.isDirty).isFalse()
        assertThat(controller.values).isEmpty()
        assertThat(controller.hasForm).isFalse()
        assertThat(controller.active).isFalse()
    }

    @Test
    fun `a field shows the document's value until it is edited`() {
        val controller = controller()
        val field = text(value = "Ada")
        assertThat(controller.valueOf(field)).isEqualTo("Ada")
        controller.setValue(field, "Grace")
        assertThat(controller.valueOf(field)).isEqualTo("Grace")
        assertThat(controller.values).containsExactly("fullName", "Grace")
    }

    @Test
    fun `typing a field back to its original value clears the edit`() {
        val controller = controller()
        val field = text(value = "Ada")
        controller.setValue(field, "Grace")
        assertThat(controller.isDirty).isTrue()
        controller.setValue(field, "Ada")
        assertThat(controller.isDirty).isFalse()
        assertThat(controller.values).isEmpty()
    }

    @Test
    fun `a read-only field cannot be edited`() {
        val controller = controller()
        val field = text(value = "REF-1", readOnly = true)
        controller.setValue(field, "tampered")
        assertThat(controller.valueOf(field)).isEqualTo("REF-1")
        assertThat(controller.values).isEmpty()
    }

    @Test
    fun `a character limit truncates instead of rejecting`() {
        val controller = controller()
        val field = text(maxLength = 4)
        controller.setValue(field, "abcdefg")
        assertThat(controller.valueOf(field)).isEqualTo("abcd")
    }

    @Test
    fun `a checkbox toggles between its on-value and empty`() {
        val controller = controller()
        val field = checkBox()
        assertThat(controller.isOn(field)).isFalse()
        controller.toggle(field)
        assertThat(controller.isOn(field)).isTrue()
        assertThat(controller.values).containsExactly("agree", "Yes")
        controller.toggle(field)
        assertThat(controller.isOn(field)).isFalse()
    }

    @Test
    fun `a checkbox the document already ticked toggles off`() {
        val controller = controller()
        val field = checkBox(value = "Yes")
        assertThat(controller.isOn(field)).isTrue()
        controller.toggle(field)
        assertThat(controller.isOn(field)).isFalse()
        assertThat(controller.values).containsExactly("agree", "")
    }

    @Test
    fun `choosing one radio button deselects its siblings`() {
        val controller = controller()
        val basic = radio(widgetIndex = 0, on = "basic")
        val pro = radio(widgetIndex = 1, on = "pro")
        controller.select(pro)
        assertThat(controller.isOn(pro)).isTrue()
        assertThat(controller.isOn(basic)).isFalse()
        controller.select(basic)
        assertThat(controller.isOn(basic)).isTrue()
        assertThat(controller.isOn(pro)).isFalse()
        // One field, so one value written back — never two.
        assertThat(controller.values).containsExactly("plan", "basic")
    }

    @Test
    fun `a radio button cannot be toggled and a checkbox cannot be selected`() {
        val controller = controller()
        controller.toggle(radio(widgetIndex = 0, on = "basic"))
        controller.select(checkBox())
        assertThat(controller.values).isEmpty()
    }

    @Test
    fun `reset drops every pending edit`() {
        val controller = controller()
        controller.setValue(text(), "Grace")
        controller.toggle(checkBox())
        assertThat(controller.editedCount).isEqualTo(2)
        controller.reset()
        assertThat(controller.editedCount).isEqualTo(0)
        assertThat(controller.isDirty).isFalse()
    }

    @Test
    fun `a dropdown records the option's stored value, not its label`() {
        val controller = controller()
        val field =
            PdfFormField(
                name = "country",
                label = "",
                widgetIndex = 0,
                kind = FormFieldKind.CHOICE,
                pageIndex = 0,
                box = BOX,
                options = listOf(FormOption("GB", "United Kingdom")),
            )
        controller.setValue(field, field.options.first().value)
        assertThat(controller.values).containsExactly("country", "GB")
    }

    @Test
    fun `the fill layer cannot be opened for a document with no form`() {
        val controller = controller()
        controller.open()
        assertThat(controller.active).isFalse()
    }

    @Test
    fun `closing resets everything for the next document`() {
        val controller = controller()
        controller.setValue(text(), "Grace")
        controller.close()
        assertThat(controller.values).isEmpty()
        assertThat(controller.fields).isNull()
        assertThat(controller.active).isFalse()
        assertThat(controller.xfaOnly).isFalse()
    }

    private companion object {
        val BOX = PdfRect(left = 72f, bottom = 700f, right = 272f, top = 720f)
    }
}
