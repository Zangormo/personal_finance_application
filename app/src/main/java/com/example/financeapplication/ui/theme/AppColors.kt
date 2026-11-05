package com.example.financeapplication.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
data class AppColors(
    val primaryText: Color,
    val border: Color,
    val placeholderText: Color,
    val accent: Color,
    val positive: Color,
    val negative: Color,
    val warning: Color,
)

@Composable
fun appColors(): AppColors {
    return AppColors(
        primaryText = MaterialTheme.colorScheme.onSurface,
        border = MaterialTheme.colorScheme.outline,
        placeholderText = MaterialTheme.colorScheme.onSurfaceVariant,
        accent = MaterialTheme.colorScheme.primary,
        positive = Positive,
        negative = Negative,
        warning = Warning,
    )
}
