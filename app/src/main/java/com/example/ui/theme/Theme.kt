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

private val DarkColorScheme = darkColorScheme(
    primary = LsPrimaryDark,
    onPrimary = LsOnPrimaryDark,
    primaryContainer = LsPrimaryContainerDark,
    onPrimaryContainer = LsOnPrimaryContainerDark,
    secondary = LsSecondaryDark,
    onSecondary = LsOnSecondaryDark,
    secondaryContainer = LsSecondaryContainerDark,
    onSecondaryContainer = LsOnSecondaryContainerDark,
    tertiary = LsTertiaryDark,
    onTertiary = LsOnTertiaryDark,
    tertiaryContainer = LsTertiaryContainerDark,
    onTertiaryContainer = LsOnTertiaryContainerDark,
    background = LsBackgroundDark,
    onBackground = LsOnBackgroundDark,
    surface = LsSurfaceDark,
    onSurface = LsOnSurfaceDark,
    surfaceVariant = LsSurfaceVariantDark,
    onSurfaceVariant = LsOnSurfaceVariantDark
)

private val LightColorScheme = lightColorScheme(
    primary = LsPrimaryLight,
    onPrimary = LsOnPrimaryLight,
    primaryContainer = LsPrimaryContainerLight,
    onPrimaryContainer = LsOnPrimaryContainerLight,
    secondary = LsSecondaryLight,
    onSecondary = LsOnSecondaryLight,
    secondaryContainer = LsSecondaryContainerLight,
    onSecondaryContainer = LsOnSecondaryContainerLight,
    tertiary = LsTertiaryLight,
    onTertiary = LsOnTertiaryLight,
    tertiaryContainer = LsTertiaryContainerLight,
    onTertiaryContainer = LsOnTertiaryContainerLight,
    background = LsBackgroundLight,
    onBackground = LsOnBackgroundLight,
    surface = LsSurfaceLight,
    onSurface = LsOnSurfaceLight,
    surfaceVariant = LsSurfaceVariantLight,
    onSurfaceVariant = LsOnSurfaceVariantLight
)

@Composable
fun LsFilesTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Brand palette as default; opt-in dynamic color
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
