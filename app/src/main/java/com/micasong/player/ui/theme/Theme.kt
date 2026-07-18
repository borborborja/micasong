package com.micasong.player.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.micasong.player.data.settings.ThemeMode
import com.micasong.player.data.theme.CustomTheme
import com.micasong.player.data.theme.ThemeJson

/**
 * MiCaSong's Material 3 theme. Honours the spec's theme modes (System / Light / Dark / Black,
 * §25) and Material You dynamic color on Android 12+. When dynamic color is off it falls back
 * to the brand palette.
 */
@Composable
fun MiCaSongTheme(
    themeMode: ThemeMode,
    dynamicColor: Boolean,
    customThemeJson: String? = null,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val useDark = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK, ThemeMode.BLACK -> true
    }
    val context = LocalContext.current
    val supportsDynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val custom = ThemeJson.parse(customThemeJson)

    var colorScheme = when {
        // A custom theme (spec §25) overrides dynamic/brand palettes.
        custom != null -> applyCustomColors(if (useDark) BrandDarkColors else BrandLightColors, custom, useDark)
        dynamicColor && supportsDynamic && useDark -> dynamicDarkColorScheme(context)
        dynamicColor && supportsDynamic && !useDark -> dynamicLightColorScheme(context)
        useDark -> BrandDarkColors
        else -> BrandLightColors
    }

    if (themeMode == ThemeMode.BLACK) {
        colorScheme = colorScheme.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceContainerLowest = Color.Black,
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = MiCaSongTypography,
        content = content,
    )
}

/** Overlay a [CustomTheme]'s role colors (spec §25) onto [base]; absent roles keep the base value. */
private fun applyCustomColors(base: ColorScheme, theme: CustomTheme, dark: Boolean): ColorScheme {
    fun c(role: String, fallback: Color): Color = theme.colorFor(role, dark)?.let { Color(it) } ?: fallback
    return base.copy(
        primary = c("primary", base.primary),
        onPrimary = c("onPrimary", base.onPrimary),
        primaryContainer = c("primaryContainer", base.primaryContainer),
        onPrimaryContainer = c("onPrimaryContainer", base.onPrimaryContainer),
        secondary = c("secondary", base.secondary),
        onSecondary = c("onSecondary", base.onSecondary),
        secondaryContainer = c("secondaryContainer", base.secondaryContainer),
        onSecondaryContainer = c("onSecondaryContainer", base.onSecondaryContainer),
        tertiary = c("tertiary", base.tertiary),
        onTertiary = c("onTertiary", base.onTertiary),
        tertiaryContainer = c("tertiaryContainer", base.tertiaryContainer),
        onTertiaryContainer = c("onTertiaryContainer", base.onTertiaryContainer),
        error = c("error", base.error),
        onError = c("onError", base.onError),
        errorContainer = c("errorContainer", base.errorContainer),
        onErrorContainer = c("onErrorContainer", base.onErrorContainer),
        background = c("background", base.background),
        onBackground = c("onBackground", base.onBackground),
        surface = c("surface", base.surface),
        onSurface = c("onSurface", base.onSurface),
        surfaceVariant = c("surfaceVariant", base.surfaceVariant),
        onSurfaceVariant = c("onSurfaceVariant", base.onSurfaceVariant),
        outline = c("outline", base.outline),
        outlineVariant = c("outlineVariant", base.outlineVariant),
        inverseSurface = c("inverseSurface", base.inverseSurface),
        inverseOnSurface = c("inverseOnSurface", base.inverseOnSurface),
        inversePrimary = c("inversePrimary", base.inversePrimary),
        surfaceTint = c("surfaceTint", base.surfaceTint),
        scrim = c("scrim", base.scrim),
    )
}
