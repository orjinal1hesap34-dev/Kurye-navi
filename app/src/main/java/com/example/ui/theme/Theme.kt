package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = CourierAmber,
    onPrimary = Color(0xFF0F172A),
    primaryContainer = CourierDarkAmber,
    onPrimaryContainer = Color.White,
    secondary = CourierCyan,
    onSecondary = Color(0xFF0F172A),
    background = CourierNightBg,
    onBackground = Color(0xFFF8FAFC),
    surface = CourierNightCard,
    onSurface = Color(0xFFF8FAFC),
    surfaceVariant = CourierNightSurface,
    onSurfaceVariant = Color(0xFFCBD5E1)
)

private val LightColorScheme = lightColorScheme(
    primary = CourierDarkAmber,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFEF3C7),
    onPrimaryContainer = Color(0xFF78350F),
    secondary = CourierElectricBlue,
    onSecondary = Color.White,
    background = CourierDayBg,
    onBackground = CourierDayText,
    surface = CourierDayCard,
    onSurface = CourierDayText,
    surfaceVariant = Color(0xFFE2E8F0),
    onSurfaceVariant = Color(0xFF334155)
)

@Composable
fun CourierNavTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
