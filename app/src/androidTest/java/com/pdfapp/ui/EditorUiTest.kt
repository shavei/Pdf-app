package com.pdfapp.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pdfapp.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Smoke test that the home screen renders its core actions on a device. */
@RunWith(AndroidJUnit4::class)
class EditorUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun home_screen_shows_open_action() {
        composeRule.onNodeWithText("Open PDF").assertIsDisplayed()
        // Empty state: no documents opened yet on a fresh install.
        composeRule.onNodeWithText("Documents you open will show up here.").assertExists()
    }
}
