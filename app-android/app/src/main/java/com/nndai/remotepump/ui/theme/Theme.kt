package com.nndai.remotepump.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = CyanBlue,
    onPrimary = DeepNavy,
    primaryContainer = CyanBlueVariant,
    onPrimaryContainer = LightText,
    secondary = GreenOk,
    onSecondary = DeepNavy,
    secondaryContainer = GreenDark,
    onSecondaryContainer = LightText,
    error = RedError,
    onError = DeepNavy,
    background = DeepNavy,
    onBackground = LightText,
    surface = CardSurface,
    onSurface = LightText,
    surfaceVariant = ElevatedSurface,
    onSurfaceVariant = SecondaryText,
    outline = DividerColor,
    outlineVariant = DimText
)

@Composable
fun RemotePumpTheme(
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = DeepNavy.toArgb()
            window.navigationBarColor = DeepNavy.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
