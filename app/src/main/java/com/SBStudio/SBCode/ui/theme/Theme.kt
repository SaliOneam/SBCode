package com.SBStudio.SBCode.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

@Composable
fun SBCodeTheme(content: @Composable () -> Unit) {
    // Built here (not once at startup) so it follows the dark mode switch.
    val scheme = darkColorScheme(
        primary = SbPrimary,
        onPrimary = SbText,
        secondary = SbAccent,
        background = SbBg,
        onBackground = SbText,
        surface = SbCard,
        onSurface = SbText,
        surfaceVariant = SbPanel,
        onSurfaceVariant = SbTextDim,
        outline = SbCardBorder,
    )
    MaterialTheme(
        colorScheme = scheme,
        typography = Typography,
        content = content
    )
}
