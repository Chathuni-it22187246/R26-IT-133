package com.greenhands.app.ui.theme

import android.app.Activity
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

enum class ThemeMode {
    DARK,
    LIGHT,
    SYSTEM
}

private val DarkColorScheme = darkColorScheme(
    primary = ForestEmerald,
    onPrimary = NightBg,
    primaryContainer = ForestEmeraldDeep,
    onPrimaryContainer = ForestEmeraldSoft,
    secondary = ClimateTeal,
    onSecondary = NightBg,
    secondaryContainer = ClimateTealDeep,
    onSecondaryContainer = ClimateTealSoft,
    tertiary = AmberWarning,
    onTertiary = NightBg,
    tertiaryContainer = AmberWarningDeep,
    onTertiaryContainer = AmberWarningSoft,
    error = SoftError,
    onError = NightText,
    errorContainer = SoftErrorDeep,
    onErrorContainer = SoftErrorSoft,
    background = NightBg,
    onBackground = NightText,
    surface = NightSurface,
    onSurface = NightText,
    surfaceVariant = NightElevated,
    onSurfaceVariant = NightMuted,
    outline = NightBorder,
    outlineVariant = NightBorder,
    scrim = Color(0x99000000)
)

private val LightColorScheme = lightColorScheme(
    primary = ForestEmeraldDeep,
    onPrimary = Color.White,
    primaryContainer = ForestEmeraldSoft,
    onPrimaryContainer = DayText,
    secondary = ClimateTealDeep,
    onSecondary = Color.White,
    secondaryContainer = ClimateTealSoft,
    onSecondaryContainer = DayText,
    tertiary = AmberWarningDeep,
    onTertiary = Color.White,
    tertiaryContainer = AmberWarningSoft,
    onTertiaryContainer = DayText,
    error = SoftErrorDeep,
    onError = Color.White,
    errorContainer = SoftErrorSoft,
    onErrorContainer = SoftErrorDeep,
    background = DayBg,
    onBackground = DayText,
    surface = DaySurface,
    onSurface = DayText,
    surfaceVariant = DayElevated,
    onSurfaceVariant = DayMuted,
    outline = DayBorder,
    outlineVariant = DayBorder,
    scrim = Color(0x66000000)
)

@Composable
fun GreenHandsTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = AndroidColor.TRANSPARENT
            window.navigationBarColor = AndroidColor.TRANSPARENT
            val insets = WindowCompat.getInsetsController(window, view)
            insets.isAppearanceLightStatusBars = !darkTheme
            insets.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography
    ) {
        CompositionLocalProvider(LocalContentColor provides colorScheme.onBackground) {
            content()
        }
    }
}
