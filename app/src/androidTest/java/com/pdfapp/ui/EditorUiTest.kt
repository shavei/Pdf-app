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
        composeRule.onNodeWithText("Open").assertIsDisplayed()
        composeRule.onNodeWithText("Sign").assertIsDisplayed()
        composeRule.onNodeWithText("Text").assertIsDisplayed()
        composeRule.onNodeWithText("Save").assertIsDisplayed()
    }
}
