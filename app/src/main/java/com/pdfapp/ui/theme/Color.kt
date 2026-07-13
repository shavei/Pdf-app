package com.pdfapp.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Brand palette (indigo) used as the static fallback when Material You dynamic
// colour is unavailable (Android 11 and below, or when the user disables it).
// Only the roles we care about are overridden; Material fills the rest.

private val Indigo = Color(0xFF4F5B92)
private val IndigoLight = Color(0xFFBBC3FF)

internal val LightColors =
    lightColorScheme(
        primary = Indigo,
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFDDE1FF),
        onPrimaryContainer = Color(0xFF06164B),
        secondary = Color(0xFF5A5D72),
        secondaryContainer = Color(0xFFDFE1F9),
        onSecondaryContainer = Color(0xFF171A2C),
        background = Color(0xFFFBF8FF),
        onBackground = Color(0xFF1B1B21),
        surface = Color(0xFFFBF8FF),
        onSurface = Color(0xFF1B1B21),
        surfaceVariant = Color(0xFFE3E1EC),
        onSurfaceVariant = Color(0xFF46464F),
    )

internal val DarkColors =
    darkColorScheme(
        primary = IndigoLight,
        onPrimary = Color(0xFF222C61),
        primaryContainer = Color(0xFF374379),
        onPrimaryContainer = Color(0xFFDDE1FF),
        secondary = Color(0xFFC3C5DD),
        secondaryContainer = Color(0xFF424659),
        onSecondaryContainer = Color(0xFFDFE1F9),
        background = Color(0xFF121318),
        onBackground = Color(0xFFE4E1E9),
        surface = Color(0xFF121318),
        onSurface = Color(0xFFE4E1E9),
        surfaceVariant = Color(0xFF46464F),
        onSurfaceVariant = Color(0xFFC7C5D0),
    )
