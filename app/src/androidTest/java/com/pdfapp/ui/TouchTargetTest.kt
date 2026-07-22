package com.pdfapp.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test

/**
 * Phase A (mobile-ui-plan): the edit-mode tool controls must meet the 48 dp
 * accessibility floor for touch targets, even where the visible glyph is
 * smaller (e.g. the 28 dp colour dot, the `−`/`+` stepper glyphs).
 */
class TouchTargetTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun colorSwatch_touchTarget_isAtLeast48dp() {
        composeRule.setContent {
            MaterialTheme {
                ColorSwatch(
                    argb = 0xFF000000.toInt(),
                    isSelected = false,
                    onClick = {},
                    contentDescription = "swatch",
                )
            }
        }
        composeRule.onNodeWithContentDescription("swatch")
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun stepper_buttons_touchTarget_isAtLeast48dp() {
        composeRule.setContent {
            MaterialTheme {
                Stepper(
                    value = "12pt",
                    onMinus = {},
                    onPlus = {},
                    minusDescription = "decrease",
                    plusDescription = "increase",
                )
            }
        }
        composeRule.onNodeWithContentDescription("decrease")
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithContentDescription("increase")
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
    }
}
