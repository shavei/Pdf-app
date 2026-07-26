package com.pdfapp.ui.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pdfapp.BuildConfig
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The home screen states which build is installed. Sideloaded APKs have no store
 * listing to check, so this is how "am I on the newest build?" gets answered on
 * the device — and it must show the very string the build was stamped with.
 */
@RunWith(AndroidJUnit4::class)
class AppVersionUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun home_screen_shows_the_running_build_version() {
        composeRule.setContent {
            HomeScreen(recents = emptyList(), onOpenClick = {}, onRecentClick = {})
        }

        composeRule.onNodeWithText("Signet ${BuildConfig.VERSION_NAME}").assertIsDisplayed()
    }
}
