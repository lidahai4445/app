package com.paperly.mobile.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(
    primary = Color(0xFF3D5A80), secondary = Color(0xFF5C6B73), tertiary = Color(0xFFB5651D),
    surface = Color(0xFFFBFAF7), surfaceContainer = Color(0xFFEFEDE8), surfaceContainerLow = Color(0xFFF5F3EF),
)
private val Dark = darkColorScheme(
    primary = Color(0xFFA9C4E8), onPrimary = Color(0xFF0E2A47), secondary = Color(0xFFB8C5CC), tertiary = Color(0xFFF0B27A),
    background = Color(0xFF1B1C1E), surface = Color(0xFF1B1C1E), onSurface = Color(0xFFE6E4E0), onSurfaceVariant = Color(0xFFC4C2BD),
    surfaceContainer = Color(0xFF26272A), surfaceContainerLow = Color(0xFF222326), secondaryContainer = Color(0xFF34404B),
)

@Composable
fun PaperlyTheme(content: @Composable () -> Unit) {
    // 固定“纸张”配色，不跟随系统取色，保持安静
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
