package com.example.financeapplication.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val AppColorScheme = darkColorScheme(
    primary = Accent,
    onPrimary = OnAccent,
    primaryContainer = AccentContainer,
    onPrimaryContainer = TextPrimary,
    secondary = Positive,
    onSecondary = Background,
    secondaryContainer = SurfaceHighest,
    onSecondaryContainer = TextPrimary,
    tertiary = Warning,
    onTertiary = Background,
    background = Background,
    onBackground = TextPrimary,
    surface = Surface,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceHigh,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = Background,
    surfaceContainerLow = Surface,
    surfaceContainer = Surface,
    surfaceContainerHigh = SurfaceHigh,
    surfaceContainerHighest = SurfaceHighest,
    surfaceTint = Accent,
    outline = Outline,
    outlineVariant = OutlineVariant,
    error = Negative,
    onError = OnAccent,
    errorContainer = NegativeContainer,
    onErrorContainer = TextPrimary,
)

/**
 * The app always uses its own dark, high-contrast palette.
 * Dynamic (wallpaper-based) colors are intentionally disabled.
 */
@Composable
fun FinanceApplicationTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppColorScheme,
        typography = Typography,
        content = content
    )
}
