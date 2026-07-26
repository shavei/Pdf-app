package com.pdfapp.ui.editor

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pdfapp.overlay.OverlayCanvasView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Phase C (mobile-ui-plan): the edit-mode bottom bar is icon-first and fits
 * without horizontal scroll — the three tools are toggles, Undo is always
 * visible, and a tap-to-jump page chip folds in the old page-nav row. Save is
 * a Scaffold-owned FAB (not in this row), so the bar stays narrow; the
 * long-tail actions (tool settings, page moves, Apply ink, Clear) live in the
 * overflow.
 */
@RunWith(AndroidJUnit4::class)
class EditBottomBarTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun bottomBar_fits_and_exposes_tools_page_chip_and_undo() {
        composeRule.setContent {
            MaterialTheme {
                EditBottomBar(
                    mode = OverlayCanvasView.Mode.INK,
                    enabled = true,
                    currentIndex = 0,
                    pageCount = 3,
                    canPrevious = false,
                    canNext = true,
                    onModeChange = {},
                    onPrevious = {},
                    onNext = {},
                    onShowGoToPage = {},
                    onShowSettings = {},
                    onUndo = {},
                    onCommitInk = {},
                    onClear = {},
                )
            }
        }
        composeRule.onNodeWithContentDescription("Sign tool").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Text tool").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Select and move tool").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Undo").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("More edit options").assertIsDisplayed()
        // The folded-in page-nav chip (Phase C.3).
        composeRule.onNodeWithText("1 / 3").assertIsDisplayed()
    }

    @Test
    fun bottomBar_toggles_tool_and_opens_settings_from_overflow() {
        var mode = OverlayCanvasView.Mode.INK
        var settingsOpened = false
        composeRule.setContent {
            MaterialTheme {
                EditBottomBar(
                    mode = mode,
                    enabled = true,
                    currentIndex = 1,
                    pageCount = 2,
                    canPrevious = true,
                    canNext = false,
                    onModeChange = { mode = it },
                    onPrevious = {},
                    onNext = {},
                    onShowGoToPage = {},
                    onShowSettings = { settingsOpened = true },
                    onUndo = {},
                    onCommitInk = {},
                    onClear = {},
                )
            }
        }
        composeRule.onNodeWithContentDescription("Text tool").performClick()
        assertEquals(OverlayCanvasView.Mode.TEXT, mode)
        composeRule.onNodeWithContentDescription("More edit options").performClick()
        composeRule.onNodeWithText("Tool settings").performClick()
        assertTrue(settingsOpened)
    }
}
