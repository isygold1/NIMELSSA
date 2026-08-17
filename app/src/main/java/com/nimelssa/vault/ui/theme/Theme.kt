package com.nimelssa.vault.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val NimelssaColorScheme = lightColorScheme(
    primary = Teal700,
    onPrimary = White,
    primaryContainer = Teal50,
    onPrimaryContainer = Teal700,
    secondary = Blue600,
    onSecondary = White,
    secondaryContainer = Blue50,
    error = Red600,
    errorContainer = Red50,
    background = Slate50,
    onBackground = Slate900,
    surface = White,
    onSurface = Slate900,
    surfaceVariant = Slate100,
    onSurfaceVariant = Slate500,
    outline = Slate200,
    outlineVariant = Slate100
)

private val NimelssaDarkColorScheme = darkColorScheme(
    primary = Teal300,
    onPrimary = Slate950,
    primaryContainer = Teal900,
    onPrimaryContainer = Teal300,
    secondary = Blue300,
    onSecondary = Slate950,
    secondaryContainer = Blue900,
    onSecondaryContainer = Blue300,
    error = Red300,
    errorContainer = Red900,
    background = Slate950,
    onBackground = Slate100,
    surface = Slate900,
    onSurface = Slate100,
    surfaceVariant = Slate800,
    onSurfaceVariant = Slate300,
    outline = Slate700,
    outlineVariant = Slate800
)

@Composable
fun NIMELSSATheme(
    content: @Composable () -> Unit
) {
    val themeMode by ThemeManager.mode.collectAsState()
    val darkTheme = when (themeMode) {
        ThemeMode.AUTO -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val colorScheme = if (darkTheme) NimelssaDarkColorScheme else NimelssaColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Slate900.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
