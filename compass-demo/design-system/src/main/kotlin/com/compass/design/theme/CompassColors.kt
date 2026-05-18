package com.compass.design.theme

import androidx.compose.ui.graphics.Color

/**
 * Variant-agnostic colour tokens. No reference to KH/VN/etc; per-variant
 * theming, if ever introduced, would arrive through a separate mechanism.
 */
internal object CompassColors {
    val PrimaryBlue = Color(0xFF1B5BC8)
    val PrimaryBlueDark = Color(0xFF0F3D8C)
    val Background = Color(0xFFF6F8FB)
    val Surface = Color(0xFFFFFFFF)
    val OnPrimary = Color(0xFFFFFFFF)
    val OnSurface = Color(0xFF101828)
    val OnSurfaceVariant = Color(0xFF667085)
    val Error = Color(0xFFD92D20)
    val Outline = Color(0xFFD0D5DD)
    val Success = Color(0xFF12B76A)
}
