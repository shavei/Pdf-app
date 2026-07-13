package com.pdfapp.persistence

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class VisualOrderTest {
    @Test
    fun `pure ltr text is unchanged`() {
        assertThat("Hello, world 123".toVisualOrder()).isEqualTo("Hello, world 123")
    }

    @Test
    fun `pure rtl text is reversed into visual order`() {
        assertThat("שלום".toVisualOrder()).isEqualTo("םולש")
    }

    @Test
    fun `rtl words keep their visual word order`() {
        // Logical "shalom olam" reads right-to-left, so visually the last word
        // is drawn first (leftmost glyph run first for PDF's LTR placement).
        assertThat("שלום עולם".toVisualOrder()).isEqualTo("םלוע םולש")
    }

    @Test
    fun `mixed text reverses only the rtl run`() {
        assertThat("Signed by יעל today".toVisualOrder())
            .isEqualTo("Signed by לעי today")
    }

    @Test
    fun `combining marks stay attached to their base letter`() {
        // שָׁ = shin + qamats + shin-dot; the cluster must move as one unit.
        val pointed = "שָׁלוֹם"
        val visual = pointed.toVisualOrder()
        assertThat(visual.length).isEqualTo(pointed.length)
        assertThat(visual.substring(visual.length - 3)).isEqualTo("שָׁ")
    }
}
