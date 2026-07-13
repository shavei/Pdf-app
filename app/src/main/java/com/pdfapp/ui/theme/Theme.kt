package com.pdfapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Signet brand palette: deep indigo (the "paper and ink" ground the launcher
// icon uses) with a coral signature accent.
private val LightColors =
    lightColorScheme(
        primary = Color(0xFF394B87),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFDCE1FF),
        onPrimaryContainer = Color(0xFF00174B),
        secondary = Color(0xFFB84A31),
        onSecondary = Color(0xFFFFFFFF),
    )

private val DarkColors =
    darkColorScheme(
        primary = Color(0xFFB6C4FF),
        onPrimary = Color(0xFF06297B),
        primaryContainer = Color(0xFF21398F),
        onPrimaryContainer = Color(0xFFDCE1FF),
        secondary = Color(0xFFFFB4A2),
        onSecondary = Color(0xFF5F1608),
    )

/** App-wide Material theme carrying the Signet brand colours. */
@Composable
fun SignetTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
