package com.green.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val ColorScheme = darkColorScheme(
    background = Bg,
    surface = Surface,
    surfaceVariant = Surface2,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onSurfaceVariant = Dim,
    primary = Accent,
    onPrimary = OnAccent,
    outline = Border,
    outlineVariant = Border2,
    error = Danger,
)

@Composable
fun GreenTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = ColorScheme, typography = Typography, content = content)
}
