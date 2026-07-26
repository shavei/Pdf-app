package com.pdfapp.ui.common

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The version line on the home screen. It is the answer to "which build is this
 * phone running?", so it shows the stamped `versionName` verbatim — including
 * the "(build N)" suffix CI adds to rolling builds.
 */
class AppVersionTest {
    @Test
    fun label_shows_a_release_version_as_is() {
        assertThat(AppVersion.label("Signet", "1.4.1")).isEqualTo("Signet 1.4.1")
    }

    @Test
    fun label_keeps_the_build_number_of_a_rolling_build() {
        assertThat(AppVersion.label("Signet", "1.4.1 (build 102)"))
            .isEqualTo("Signet 1.4.1 (build 102)")
    }

    @Test
    fun label_falls_back_to_the_app_name_when_no_version_was_stamped() {
        assertThat(AppVersion.label("Signet", "   ")).isEqualTo("Signet")
    }
}
