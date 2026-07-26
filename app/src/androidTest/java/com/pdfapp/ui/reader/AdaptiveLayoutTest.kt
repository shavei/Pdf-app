package com.pdfapp.ui.reader

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pdfapp.ui.document.PdfEditorViewModel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Phase E (mobile-ui-plan): the reader body swaps chrome by window size — compact
 * phone portrait shows the page alone (its actions are in the bottom bar), wider
 * or shorter windows add the start-side rail, and expanded windows additionally
 * dock the navigation pane. The rail carries the same actions as the bottom bar.
 */
@RunWith(AndroidJUnit4::class)
class AdaptiveLayoutTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun viewModel() = PdfEditorViewModel(ApplicationProvider.getApplicationContext())

    /** Renders [ReaderBody] with stand-in slots so the swap alone is under test. */
    @Composable
    private fun Body(
        layout: ReaderLayoutSpec,
        chromeShown: Boolean = true,
        paneOpen: Boolean = true,
    ) {
        MaterialTheme {
            ReaderBody(
                layout = layout,
                chromeShown = chromeShown,
                paneOpen = paneOpen,
                rail = { Text(RAIL) },
                pane = { Text(PANE) },
            ) { Text(PAGE) }
        }
    }

    @Test
    fun compact_window_shows_the_page_without_rail_or_pane() {
        composeRule.setContent {
            Body(ReaderLayout.spec(widthDp = 411, heightDp = 891))
        }
        composeRule.onNodeWithText(PAGE).assertIsDisplayed()
        composeRule.onNodeWithText(RAIL).assertDoesNotExist()
        composeRule.onNodeWithText(PANE).assertDoesNotExist()
    }

    @Test
    fun medium_window_adds_the_rail_but_not_the_pane() {
        composeRule.setContent {
            Body(ReaderLayout.spec(widthDp = 700, heightDp = 900))
        }
        composeRule.onNodeWithText(RAIL).assertIsDisplayed()
        composeRule.onNodeWithText(PAGE).assertIsDisplayed()
        composeRule.onNodeWithText(PANE).assertDoesNotExist()
    }

    @Test
    fun expanded_window_docks_the_pane_beside_the_page() {
        composeRule.setContent {
            Body(ReaderLayout.spec(widthDp = 1280, heightDp = 800))
        }
        composeRule.onNodeWithText(RAIL).assertIsDisplayed()
        composeRule.onNodeWithText(PANE).assertIsDisplayed()
        composeRule.onNodeWithText(PAGE).assertIsDisplayed()
    }

    @Test
    fun immersive_chrome_hides_the_rail_and_pane_at_every_size() {
        composeRule.setContent {
            Body(
                layout = ReaderLayout.spec(widthDp = 1280, heightDp = 800),
                chromeShown = false,
            )
        }
        composeRule.onNodeWithText(PAGE).assertIsDisplayed()
        composeRule.onNodeWithText(RAIL).assertDoesNotExist()
        composeRule.onNodeWithText(PANE).assertDoesNotExist()
    }

    @Test
    fun a_closed_dock_keeps_the_rail_and_drops_only_the_pane() {
        composeRule.setContent {
            Body(
                layout = ReaderLayout.spec(widthDp = 1280, heightDp = 800),
                paneOpen = false,
            )
        }
        composeRule.onNodeWithText(RAIL).assertIsDisplayed()
        composeRule.onNodeWithText(PANE).assertDoesNotExist()
    }

    @Test
    fun rail_exposes_the_same_primary_actions_as_the_bottom_bar() {
        composeRule.setContent {
            MaterialTheme {
                ReaderNavRail(
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

    private companion object {
        const val RAIL = "rail-slot"
        const val PANE = "pane-slot"
        const val PAGE = "page-slot"
    }
}
