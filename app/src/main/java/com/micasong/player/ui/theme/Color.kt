package com.micasong.player.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Fallback color schemes derived from MiCaSong's own brand seed (a violet), used when the
 * device doesn't support Material You dynamic color or the user opts out (spec §25). All
 * values are original — none copied from the third-party app.
 */

private val Violet = Color(0xFF6B4FA0)
private val VioletLight = Color(0xFF8A6FBF)
private val VioletContainer = Color(0xFFE9DDFF)
private val Amber = Color(0xFFB58900)

val BrandLightColors = lightColorScheme(
    primary = Violet,
    onPrimary = Color.White,
    primaryContainer = VioletContainer,
    onPrimaryContainer = Color(0xFF23005B),
    secondary = Color(0xFF625B71),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8DEF8),
    onSecondaryContainer = Color(0xFF1E192B),
    tertiary = Amber,
    onTertiary = Color.White,
    background = Color(0xFFFEF7FF),
    onBackground = Color(0xFF1D1B20),
    surface = Color(0xFFFEF7FF),
    onSurface = Color(0xFF1D1B20),
    surfaceVariant = Color(0xFFE7E0EB),
    onSurfaceVariant = Color(0xFF49454E),
    outline = Color(0xFF7A757F),
)

val BrandDarkColors = darkColorScheme(
    primary = VioletLight,
    onPrimary = Color(0xFF3A2069),
    primaryContainer = Color(0xFF523781),
    onPrimaryContainer = VioletContainer,
    secondary = Color(0xFFCBC2DB),
    onSecondary = Color(0xFF332D41),
    secondaryContainer = Color(0xFF4A4458),
    onSecondaryContainer = Color(0xFFE8DEF8),
    tertiary = Color(0xFFE6C34D),
    onTertiary = Color(0xFF3D2F00),
    background = Color(0xFF141218),
    onBackground = Color(0xFFE6E0E9),
    surface = Color(0xFF141218),
    onSurface = Color(0xFFE6E0E9),
    surfaceVariant = Color(0xFF49454E),
    onSurfaceVariant = Color(0xFFCAC4CF),
    outline = Color(0xFF948F99),
)

/** AMOLED-friendly "black" scheme (spec §25): dark palette with a true-black background. */
val BrandBlackColors = BrandDarkColors.copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0A0A0A),
    surfaceContainer = Color(0xFF121212),
)
