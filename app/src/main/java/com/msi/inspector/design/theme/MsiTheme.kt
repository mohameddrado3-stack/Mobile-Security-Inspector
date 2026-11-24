package com.msi.inspector.design.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val MsiColors = darkColorScheme(
    primary = Color(0xFF70E5CD),
    onPrimary = Color(0xFF07131A),
    secondary = Color(0xFF85B8FF),
    onSecondary = Color(0xFF071426),
    tertiary = Color(0xFFFFC278),
    background = Color(0xFF070C17),
    onBackground = Color(0xFFF2F6FC),
    surface = Color(0xFF111B2B),
    onSurface = Color(0xFFF2F6FC),
    surfaceVariant = Color(0xFF1B2A40),
    onSurfaceVariant = Color(0xFFA6B7CA),
    error = Color(0xFFFF7E85)
)

@Composable
fun MsiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MsiColors,
        content = content
    )
}
