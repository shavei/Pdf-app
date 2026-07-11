package com.pdfapp.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pdfapp.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Smoke test that the editor's core toolbar renders on a device. */
@RunWith(AndroidJUnit4::class)
class EditorUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun toolbar_shows_core_actions() {
        // "Open" is the left-most control, so it is always within the viewport.
        composeRule.onNodeWithText("Open").assertIsDisplayed()
        // The remaining controls live in a horizontally-scrollable row and may be
        // off-screen on a narrow device, so assert they are composed, not visible.
        composeRule.onNodeWithText("Sign").assertExists()
        composeRule.onNodeWithText("Text").assertExists()
        composeRule.onNodeWithText("Edit").assertExists()
        composeRule.onNodeWithText("Save").assertExists()
    }
}
