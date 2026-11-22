package ru.productaccounting.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AppColors = lightColorScheme(
    primary = Color(0xFF8A3D18),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDBCC),
    secondary = Color(0xFF755847),
    background = Color(0xFFFFF8F4),
    surface = Color(0xFFFFF8F4),
    surfaceVariant = Color(0xFFF4DED4),
)

@Composable
fun ProductAccountingTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = AppColors, content = content)
}
