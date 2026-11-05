package com.example.financeapplication.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle

/**
 * Text is vertically centered inside its line height and the extra leading above the first line
 * and below the last line is trimmed, so a text block has equal space above and below its glyphs
 * (otherwise single-line text looks shifted up inside its container).
 */
private val Balanced = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.Both
)

private fun TextStyle.balanced() = copy(lineHeightStyle = Balanced)

val Typography = Typography().run {
    copy(
        displayLarge = displayLarge.balanced(),
        displayMedium = displayMedium.balanced(),
        displaySmall = displaySmall.balanced(),
        headlineLarge = headlineLarge.balanced(),
        headlineMedium = headlineMedium.balanced(),
        headlineSmall = headlineSmall.balanced(),
        titleLarge = titleLarge.balanced(),
        titleMedium = titleMedium.balanced(),
        titleSmall = titleSmall.balanced(),
        bodyLarge = bodyLarge.balanced(),
        bodyMedium = bodyMedium.balanced(),
        bodySmall = bodySmall.balanced(),
        labelLarge = labelLarge.balanced(),
        labelMedium = labelMedium.balanced(),
        labelSmall = labelSmall.balanced(),
    )
}
