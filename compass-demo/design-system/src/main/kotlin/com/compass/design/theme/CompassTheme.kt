package com.compass.design.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val CompassColorScheme = lightColorScheme(
    primary = CompassColors.PrimaryBlue,
    onPrimary = CompassColors.OnPrimary,
    background = CompassColors.Background,
    onBackground = CompassColors.OnSurface,
    surface = CompassColors.Surface,
    onSurface = CompassColors.OnSurface,
    onSurfaceVariant = CompassColors.OnSurfaceVariant,
    error = CompassColors.Error,
    outline = CompassColors.Outline,
)

private val CompassTypography = Typography(
    titleLarge = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun CompassTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CompassColorScheme,
        typography = CompassTypography,
        content = content,
    )
}
