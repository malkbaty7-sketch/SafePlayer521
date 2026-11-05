package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.example.util.ThemeManager

private val BronzeLightColorScheme = lightColorScheme(
    primary = BronzePrimaryLight, onPrimary = BronzeOnPrimaryLight,
    primaryContainer = BronzePrimaryContainerLight, onPrimaryContainer = BronzeOnPrimaryContainerLight,
    secondary = BronzeSecondaryLight, onSecondary = BronzeOnSecondaryLight,
    secondaryContainer = BronzeSecondaryContainerLight, onSecondaryContainer = BronzeOnSecondaryContainerLight,
    background = BronzeBackgroundLight, onBackground = BronzeOnBackgroundLight,
    surface = BronzeSurfaceLight, onSurface = BronzeOnSurfaceLight,
    surfaceVariant = BronzeSurfaceVariantLight, onSurfaceVariant = BronzeOnSurfaceVariantLight,
    error = ErrorLight, onError = OnErrorLight
)

private val BronzeDarkColorScheme = darkColorScheme(
    primary = BronzePrimaryDark, onPrimary = BronzeOnPrimaryDark,
    primaryContainer = BronzePrimaryContainerDark, onPrimaryContainer = BronzeOnPrimaryContainerDark,
    secondary = BronzeSecondaryDark, onSecondary = BronzeOnSecondaryDark,
    secondaryContainer = BronzeSecondaryContainerDark, onSecondaryContainer = BronzeOnSecondaryContainerDark,
    background = BronzeBackgroundDark, onBackground = BronzeOnBackgroundDark,
    surface = BronzeSurfaceDark, onSurface = BronzeOnSurfaceDark,
    surfaceVariant = BronzeSurfaceVariantDark, onSurfaceVariant = BronzeOnSurfaceVariantDark,
    error = ErrorDark, onError = OnErrorDark
)

private val OceanLightColorScheme = lightColorScheme(
    primary = OceanPrimaryLight, onPrimary = OceanOnPrimaryLight,
    primaryContainer = OceanPrimaryContainerLight, onPrimaryContainer = OceanOnPrimaryContainerLight,
    secondary = OceanSecondaryLight, onSecondary = OceanOnSecondaryLight,
    secondaryContainer = OceanSecondaryContainerLight, onSecondaryContainer = OceanOnSecondaryContainerLight,
    background = OceanBackgroundLight, onBackground = OceanOnBackgroundLight,
    surface = OceanSurfaceLight, onSurface = OceanOnSurfaceLight,
    surfaceVariant = OceanSurfaceVariantLight, onSurfaceVariant = OceanOnSurfaceVariantLight,
    error = ErrorLight, onError = OnErrorLight
)

private val OceanDarkColorScheme = darkColorScheme(
    primary = OceanPrimaryDark, onPrimary = OceanOnPrimaryDark,
    primaryContainer = OceanPrimaryContainerDark, onPrimaryContainer = OceanOnPrimaryContainerDark,
    secondary = OceanSecondaryDark, onSecondary = OceanOnSecondaryDark,
    secondaryContainer = OceanSecondaryContainerDark, onSecondaryContainer = OceanOnSecondaryContainerDark,
    background = OceanBackgroundDark, onBackground = OceanOnBackgroundDark,
    surface = OceanSurfaceDark, onSurface = OceanOnSurfaceDark,
    surfaceVariant = OceanSurfaceVariantDark, onSurfaceVariant = OceanOnSurfaceVariantDark,
    error = ErrorDark, onError = OnErrorDark
)

private val ForestLightColorScheme = lightColorScheme(
    primary = ForestPrimaryLight, onPrimary = ForestOnPrimaryLight,
    primaryContainer = ForestPrimaryContainerLight, onPrimaryContainer = ForestOnPrimaryContainerLight,
    secondary = ForestSecondaryLight, onSecondary = ForestOnSecondaryLight,
    secondaryContainer = ForestSecondaryContainerLight, onSecondaryContainer = ForestOnSecondaryContainerLight,
    background = ForestBackgroundLight, onBackground = ForestOnBackgroundLight,
    surface = ForestSurfaceLight, onSurface = ForestOnSurfaceLight,
    surfaceVariant = ForestSurfaceVariantLight, onSurfaceVariant = ForestOnSurfaceVariantLight,
    error = ErrorLight, onError = OnErrorLight
)

private val ForestDarkColorScheme = darkColorScheme(
    primary = ForestPrimaryDark, onPrimary = ForestOnPrimaryDark,
    primaryContainer = ForestPrimaryContainerDark, onPrimaryContainer = ForestOnPrimaryContainerDark,
    secondary = ForestSecondaryDark, onSecondary = ForestOnSecondaryDark,
    secondaryContainer = ForestSecondaryContainerDark, onSecondaryContainer = ForestOnSecondaryContainerDark,
    background = ForestBackgroundDark, onBackground = ForestOnBackgroundDark,
    surface = ForestSurfaceDark, onSurface = ForestOnSurfaceDark,
    surfaceVariant = ForestSurfaceVariantDark, onSurfaceVariant = ForestOnSurfaceVariantDark,
    error = ErrorDark, onError = OnErrorDark
)

private val SlateLightColorScheme = lightColorScheme(
    primary = SlatePrimaryLight, onPrimary = SlateOnPrimaryLight,
    primaryContainer = SlatePrimaryContainerLight, onPrimaryContainer = SlateOnPrimaryContainerLight,
    secondary = SlateSecondaryLight, onSecondary = SlateOnSecondaryLight,
    secondaryContainer = SlateSecondaryContainerLight, onSecondaryContainer = SlateOnSecondaryContainerLight,
    background = SlateBackgroundLight, onBackground = SlateOnBackgroundLight,
    surface = SlateSurfaceLight, onSurface = SlateOnSurfaceLight,
    surfaceVariant = SlateSurfaceVariantLight, onSurfaceVariant = SlateOnSurfaceVariantLight,
    error = ErrorLight, onError = OnErrorLight
)

private val SlateDarkColorScheme = darkColorScheme(
    primary = SlatePrimaryDark, onPrimary = SlateOnPrimaryDark,
    primaryContainer = SlatePrimaryContainerDark, onPrimaryContainer = SlateOnPrimaryContainerDark,
    secondary = SlateSecondaryDark, onSecondary = SlateOnSecondaryDark,
    secondaryContainer = SlateSecondaryContainerDark, onSecondaryContainer = SlateOnSecondaryContainerDark,
    background = SlateBackgroundDark, onBackground = SlateOnBackgroundDark,
    surface = SlateSurfaceDark, onSurface = SlateOnSurfaceDark,
    surfaceVariant = SlateSurfaceVariantDark, onSurfaceVariant = SlateOnSurfaceVariantDark,
    error = ErrorDark, onError = OnErrorDark
)


@Composable
fun MyApplicationTheme(
    themeColor: String = ThemeManager.COLOR_BRONZE,
    themeMode: String = ThemeManager.MODE_SYSTEM,
    content: @Composable () -> Unit,
) {
    val systemInDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        ThemeManager.MODE_LIGHT -> false
        ThemeManager.MODE_DARK -> true
        else -> systemInDark
    }

    val colorScheme = when (themeColor) {
        ThemeManager.COLOR_OCEAN -> if (isDark) OceanDarkColorScheme else OceanLightColorScheme
        ThemeManager.COLOR_FOREST -> if (isDark) ForestDarkColorScheme else ForestLightColorScheme
        ThemeManager.COLOR_SLATE -> if (isDark) SlateDarkColorScheme else SlateLightColorScheme
        else -> if (isDark) BronzeDarkColorScheme else BronzeLightColorScheme
    }

    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
