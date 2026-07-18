package com.micasong.player.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.micasong.player.data.settings.ThemeMode

/**
 * MiCaSong's Material 3 theme. Honours the spec's theme modes (System / Light / Dark / Black,
 * §25) and Material You dynamic color on Android 12+. When dynamic color is off it falls back
 * to the brand palette.
 */
@Composable
fun MiCaSongTheme(
    themeMode: ThemeMode,
    dynamicColor: Boolean,
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

    var colorScheme = when {
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
