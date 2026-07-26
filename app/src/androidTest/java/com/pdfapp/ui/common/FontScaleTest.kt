package com.pdfapp.ui.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pdfapp.data.RecentFile
import com.pdfapp.overlay.OverlayCanvasView
import com.pdfapp.ui.document.PdfEditorViewModel
import com.pdfapp.ui.editor.EditBottomBar
import com.pdfapp.ui.home.HomeScreen
import com.pdfapp.ui.reader.ReaderBottomBar
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Phase F.1/F.3 (mobile-ui-plan): the chrome survives a 2× system font scale —
 * rows grow to hold their text instead of clipping it inside a fixed box, and
 * every touch target still clears the 48 dp floor at both scales.
 */
@RunWith(AndroidJUnit4::class)
class FontScaleTest {
    @get:Rule
    val composeRule = createComposeRule()

    /** Renders [content] as if the user had set the system font scale to [fontScale]. */
    private fun ComposeContentTestRule.setContentAtFontScale(
        fontScale: Float,
        content: @Composable () -> Unit,
    ) = setContent {
        AtFontScale(fontScale) { content() }
    }

    @Composable
    private fun AtFontScale(
        fontScale: Float,
        content: @Composable () -> Unit,
    ) {
        val base = LocalDensity.current
        CompositionLocalProvider(
            LocalDensity provides Density(density = base.density, fontScale = fontScale),
        ) {
            MaterialTheme { content() }
        }
    }

    @Composable
    private fun Home() {
        HomeScreen(
            recents =
                listOf(
                    RecentFile(
                        uri = "content://test/quarterly-report",
                        displayName = RECENT_NAME,
                        pageCount = 12,
                        lastPageIndex = 2,
                        lastOpenedEpochMillis = 0L,
                    ),
                ),
            onOpenClick = {},
            onRecentClick = {},
        )
    }

    @Composable
    private fun EditBar() {
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

    /** Height of the node in raw pixels — density is fixed, so only text scaling moves it. */
    private fun rowHeightPx(): Int =
        composeRule.onNodeWithContentDescription(RECENT_LABEL).fetchSemanticsNode().size.height

    @Test
    fun recent_row_grows_with_the_font_scale_instead_of_clipping_its_text() {
        var scale by mutableFloatStateOf(NORMAL_SCALE)
        composeRule.setContent { AtFontScale(scale) { Home() } }

        val normal = rowHeightPx()
        composeRule.runOnUiThread { scale = LARGE_SCALE }
        composeRule.waitForIdle()
        val large = rowHeightPx()

        // A fixed-height row would measure the same at both scales and clip the
        // larger text; growing is the whole point of F.1.
        assertTrue(
            "recent row should grow with the font scale, was $normal px at " +
                "${NORMAL_SCALE}x and $large px at ${LARGE_SCALE}x",
            large > normal,
        )
    }

    @Test
    fun recent_row_at_double_font_scale_still_shows_its_name_and_position() {
        composeRule.setContentAtFontScale(LARGE_SCALE) { Home() }
        // Two-line wrapping (F.1) keeps the whole name reachable, and the row is
        // announced as one node rather than as a name plus a "·" separator (F.2).
        composeRule.onNodeWithContentDescription(RECENT_LABEL)
            .assertIsDisplayed()
            .assertHeightIsAtLeast(DynamicType.MIN_TOUCH_TARGET_DP.dp)
        composeRule.onNodeWithText("Open PDF").assertIsDisplayed()
    }

    @Test
    fun open_button_meets_the_touch_floor_at_both_font_scales() {
        var scale by mutableFloatStateOf(NORMAL_SCALE)
        composeRule.setContent { AtFontScale(scale) { Home() } }

        // A Material 3 filled button is only 40 dp tall by default (F.3).
        composeRule.onNodeWithText("Open PDF")
            .assertHeightIsAtLeast(DynamicType.MIN_TOUCH_TARGET_DP.dp)
            .assertWidthIsAtLeast(DynamicType.MIN_TOUCH_TARGET_DP.dp)

        composeRule.runOnUiThread { scale = LARGE_SCALE }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Open PDF")
            .assertHeightIsAtLeast(DynamicType.MIN_TOUCH_TARGET_DP.dp)
            .assertWidthIsAtLeast(DynamicType.MIN_TOUCH_TARGET_DP.dp)
    }

    @Test
    fun page_chip_meets_the_touch_floor_and_announces_its_position() {
        composeRule.setContentAtFontScale(LARGE_SCALE) { EditBar() }
        // "1 / 3" is meaningless to a screen reader, so the chip carries the
        // spoken form; a 40 dp Material text button is lifted to 48 dp (F.2/F.3).
        composeRule.onNodeWithContentDescription(ReaderSemantics.pageChipLabel(0, 3))
            .assertIsDisplayed()
            .assertHeightIsAtLeast(DynamicType.MIN_TOUCH_TARGET_DP.dp)
            .assertWidthIsAtLeast(DynamicType.MIN_TOUCH_TARGET_DP.dp)
    }

    @Test
    fun edit_tools_meet_the_touch_floor_at_double_font_scale() {
        composeRule.setContentAtFontScale(LARGE_SCALE) { EditBar() }
        // Regression: the icons are fixed-width and the page chip is weighted,
        // so a bar too narrow for its content at 2× squeezes the chip — never
        // an icon below the floor.
        listOf("Sign tool", "Text tool", "Select and move tool", "Undo", "More edit options")
            .forEach { description ->
                composeRule.onNodeWithContentDescription(description)
                    .assertHeightIsAtLeast(DynamicType.MIN_TOUCH_TARGET_DP.dp)
                    .assertWidthIsAtLeast(DynamicType.MIN_TOUCH_TARGET_DP.dp)
            }
    }

    @Test
    fun reader_actions_meet_the_touch_floor_at_double_font_scale() {
        // The reader bar carries the same five-icons-plus-chip pressure as the
        // edit bar, and had the same latent squeeze.
        composeRule.setContentAtFontScale(LARGE_SCALE) {
            ReaderBottomBar(
                viewModel = PdfEditorViewModel(ApplicationProvider.getApplicationContext()),
                onShowThumbnails = {},
                onShowGoToPage = {},
                onOpenAnother = {},
            )
        }
        listOf(
            "Search in document",
            "Page thumbnails",
            "Night mode",
            "Edit document",
            "More options",
        ).forEach { description ->
            composeRule.onNodeWithContentDescription(description)
                .assertHeightIsAtLeast(DynamicType.MIN_TOUCH_TARGET_DP.dp)
                .assertWidthIsAtLeast(DynamicType.MIN_TOUCH_TARGET_DP.dp)
        }
    }

    private companion object {
        const val NORMAL_SCALE = 1f
        const val LARGE_SCALE = 2f
        const val RECENT_NAME = "Quarterly report — signed final.pdf"
        val RECENT_LABEL =
            ReaderSemantics.recentFileLabel(
                displayName = RECENT_NAME,
                pageCount = 12,
                lastPageIndex = 2,
            )
    }
}
