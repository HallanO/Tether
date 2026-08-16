package com.example.tether.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ============================================================================
// Color Theory System: Anchored on Slate 900 (#0F172A) & White (#FFFFFF)
// Direct 180° Complementary Hue (222° - 180° = 42°): Warm Amber / Ochre Gold
// Hardware Instrument Design Language (No Neon/Cyberpunk Clichés)
// ============================================================================

// Core Palette Tokens
val Slate900 = Color(0xFF0F172A)      // Logo Route Color & Deep Background
val Slate800 = Color(0xFF1E293B)      // Grounded Dark Surface
val Slate700 = Color(0xFF334155)      // Dark Border & Surface Variant
val Slate600 = Color(0xFF475569)      // Subdued Neutral
val Slate500 = Color(0xFF64748B)      // Secondary Text / Light Border
val Slate300 = Color(0xFFCBD5E1)      // Soft Outline
val Slate200 = Color(0xFFE2E8F0)      // Light Border / Subtle Surface
val Slate100 = Color(0xFFF1F5F9)      // Light Surface Variant
val Slate50  = Color(0xFFF8FAFC)      // Light Canvas Base
val PureWhite = Color(0xFFFFFFFF)     // Logo Canvas Base

// Precision Steel Blue Accent System (Sophisticated Technical Blue)
val PrecisionBlue700 = Color(0xFF1D4ED8)  // High-contrast Deep Blue
val PrecisionBlue600 = Color(0xFF2563EB)  // Light Mode Primary Accent
val PrecisionBlue500 = Color(0xFF3B82F6)  // Dark Mode Primary Accent
val PrecisionBlue400 = Color(0xFF60A5FA)  // Soft Blue Highlight

// Supporting Precision Tones
val SlateBlue600 = Color(0xFF475569)     // Slate Secondary (Light)
val SlateBlue400 = Color(0xFF94A3B8)     // Slate Secondary (Dark)
val Emerald600 = Color(0xFF059669)        // Connected Emerald (Light)
val Emerald400 = Color(0xFF10B981)        // Connected Emerald (Dark)
val Crimson600 = Color(0xFFDC2626)        // Alert Crimson (Light)
val Crimson400 = Color(0xFFEF4444)        // Alert Crimson (Dark)

// Active Theme Color References
val DarkBackground = Slate900
val DarkSurface = Slate800
val DarkSurfaceVariant = Slate700
val AccentPrimary = PrecisionBlue500      // Precision Steel Blue Primary Accent
val AccentSecondary = PrecisionBlue400
val StatusConnected = Emerald400
val StatusAlert = Crimson400
val TextPrimary = Slate50
val TextSecondary = Slate500
val SurfaceBorder = Slate700

// Legacy Aliases mapped to color-theory tokens
val NeonCyan = AccentPrimary
val ElectricBlue = AccentSecondary
val NeonEmerald = StatusConnected
val NeonCoral = StatusAlert
val RadarGreen = StatusConnected

private val DarkColorScheme = darkColorScheme(
    primary = PrecisionBlue500,
    onPrimary = Slate900,
    primaryContainer = PrecisionBlue700,
    onPrimaryContainer = Color.White,
    secondary = PrecisionBlue400,
    onSecondary = Color.White,
    tertiary = Emerald400,
    background = Slate900,
    onBackground = Slate50,
    surface = Slate800,
    onSurface = Slate50,
    surfaceVariant = Slate700,
    onSurfaceVariant = Slate300,
    outline = Slate600,
    error = Crimson400
)

private val LightColorScheme = lightColorScheme(
    primary = PrecisionBlue600,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDBEAFE), // Light Steel Blue Container
    onPrimaryContainer = Color(0xFF1E40AF),
    secondary = PrecisionBlue700,
    onSecondary = Color.White,
    tertiary = Emerald600,
    background = Slate50,
    onBackground = Slate900,
    surface = PureWhite,
    onSurface = Slate900,
    surfaceVariant = Slate100,
    onSurfaceVariant = Slate700,
    outline = Slate300,
    error = Crimson600
)

@Composable
fun TetherTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colors,
        content = content
    )
}

