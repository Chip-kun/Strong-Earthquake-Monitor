package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val KyoshinColorScheme = darkColorScheme(
    primary = KyoshinPrimary,
    onPrimary = Color.Black,
    primaryContainer = KyoshinPrimaryDark,
    onPrimaryContainer = Color.White,
    secondary = KyoshinSecondary,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF663C00),
    onSecondaryContainer = Color(0xFFFFD180),
    tertiary = KyoshinAccentRed,
    onTertiary = Color.White,
    background = KyoshinBg,
    onBackground = Color(0xFFE2E8F0),
    surface = KyoshinSurface,
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = KyoshinSurfaceVariant,
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = KyoshinCardBorder
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Keep high-tech monitor styling
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = KyoshinColorScheme,
        typography = Typography,
        content = content
    )
}
