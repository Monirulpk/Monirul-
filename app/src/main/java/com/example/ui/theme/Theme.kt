package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = EmeraldPrimary,
    onPrimary = OnEmeraldPrimary,
    primaryContainer = EmeraldContainer,
    onPrimaryContainer = OnEmeraldContainer,
    secondary = CyanSecondary,
    onSecondary = OnCyanSecondary,
    secondaryContainer = CyanContainer,
    onSecondaryContainer = OnCyanContainer,
    tertiary = AmberTertiary,
    onTertiary = OnAmberTertiary,
    tertiaryContainer = AmberContainer,
    onTertiaryContainer = OnAmberContainer,
    background = ObsidianBackground,
    onBackground = ObsidianTextPrimary,
    surface = ObsidianSurface,
    onSurface = ObsidianTextPrimary,
    surfaceVariant = ObsidianSurfaceVariant,
    onSurfaceVariant = ObsidianTextSecondary,
    surfaceContainerLowest = ObsidianBackground,
    surfaceContainerLow = ObsidianSurface,
    surfaceContainer = ObsidianSurfaceVariant,
    surfaceContainerHigh = ObsidianSurfaceHigh,
    outline = ObsidianOutline,
    outlineVariant = ObsidianOutlineVariant,
    error = ErrorDark,
    onError = OnErrorDark,
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark
)

private val LightColorScheme = lightColorScheme(
    primary = LightEmeraldPrimary,
    onPrimary = LightOnEmeraldPrimary,
    primaryContainer = LightEmeraldContainer,
    onPrimaryContainer = LightOnEmeraldContainer,
    secondary = LightCyanSecondary,
    onSecondary = LightOnCyanSecondary,
    secondaryContainer = LightCyanContainer,
    onSecondaryContainer = LightOnCyanContainer,
    tertiary = LightAmberTertiary,
    onTertiary = LightOnAmberTertiary,
    tertiaryContainer = LightAmberContainer,
    onTertiaryContainer = LightOnAmberContainer,
    background = LightPaperBackground,
    onBackground = LightTextPrimary,
    surface = LightPaperSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightPaperSurfaceVariant,
    onSurfaceVariant = LightTextSecondary,
    outline = LightPaperOutline,
    error = ErrorLight,
    onError = OnErrorLight,
    errorContainer = ErrorContainerLight,
    onErrorContainer = OnErrorContainerLight
)

val StudioShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

enum class ThemeMode {
    DARK,
    LIGHT,
    SYSTEM
}

@Composable
fun AiDevStudioTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        isDark -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = StudioShapes,
        content = content
    )
}
