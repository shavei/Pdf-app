package com.pdfapp.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.pdfapp.BuildConfig
import com.pdfapp.R

/**
 * How the running build names itself in the UI.
 *
 * The version string is whatever CI stamped as the `versionName` — `1.4.1` for a
 * tagged release, `1.4.1 (build 102)` for a rolling build from `main` — so the
 * home screen, the release notes and Android's App info all read the same.
 */
object AppVersion {
    /**
     * "Signet 1.4.1". Falls back to the bare app name if a build somehow carries
     * no version, so the line never renders as a dangling app name and a blank.
     */
    fun label(
        appName: String,
        versionName: String,
    ): String = versionName.trim().takeIf(String::isNotEmpty)?.let { "$appName $it" } ?: appName
}

/** [AppVersion.label] for the build that is actually running. */
@Composable
fun appVersionLabel(): String = AppVersion.label(stringResource(R.string.app_name), BuildConfig.VERSION_NAME)
