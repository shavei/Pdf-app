package com.pdfapp.ui.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pdfapp.ui.document.PdfEditorViewModel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Phase B.2 (mobile-ui-plan): the reader bottom bar surfaces the high-frequency
 * actions for the thumb, and its visibility follows the immersive `chromeVisible`
 * state.
 */
@RunWith(AndroidJUnit4::class)
class ReaderBottomBarTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun viewModel() = PdfEditorViewModel(ApplicationProvider.getApplicationContext())

    @Test
    fun bottomBar_exposes_the_primary_reading_actions() {
        composeRule.setContent {
            MaterialTheme {
                ReaderBottomBar(
                    viewModel = viewModel(),
                    onShowThumbnails = {},
                    onShowGoToPage = {},
                    onOpenAnother = {},
                )
            }
        }
        composeRule.onNodeWithContentDescription("Search in document").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Page thumbnails").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Night mode").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Edit document").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("More options").assertIsDisplayed()
    }

    @Test
    fun bottomBar_presence_follows_chromeVisible() {
        composeRule.setContent {
            var chromeVisible by remember { mutableStateOf(true) }
            MaterialTheme {
                AnimatedVisibility(
                    visible = chromeVisible,
                    enter = slideInVertically { it },
                    exit = slideOutVertically { it },
                ) {
                    ReaderBottomBar(
                        viewModel = viewModel(),
                        onShowThumbnails = {},
                        onShowGoToPage = {},
                        onOpenAnother = {},
                    )
                }
                Text(
                    text = "toggle",
                    modifier = Modifier.clickable { chromeVisible = !chromeVisible },
                )
            }
        }
        composeRule.onNodeWithContentDescription("Edit document").assertIsDisplayed()
        composeRule.onNodeWithText("toggle").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Edit document").assertDoesNotExist()
    }
}
