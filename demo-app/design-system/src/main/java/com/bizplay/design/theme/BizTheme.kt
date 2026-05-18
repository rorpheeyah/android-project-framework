package com.bizplay.design.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = BizBlue500,
    onPrimary = BizWhite,
    primaryContainer = BizBlue100,
    onPrimaryContainer = BizBlue900,
    background = BizWhite,
    onBackground = BizGrey900,
    surface = BizWhite,
    onSurface = BizGrey900,
    surfaceVariant = BizGrey100,
    onSurfaceVariant = BizGrey700,
    outline = BizGrey200,
    error = BizRed500,
)

private val DarkColors = darkColorScheme(
    primary = BizBlue500,
    onPrimary = BizWhite,
    background = BizGrey900,
    onBackground = BizWhite,
    surface = BizGrey900,
    onSurface = BizWhite,
)

@Composable
fun BizTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography(),
        content = content,
    )
}
