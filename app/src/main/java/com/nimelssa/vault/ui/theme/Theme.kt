package com.nimelssa.vault.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
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

@Composable
fun NIMELSSATheme(
    content: @Composable () -> Unit
) {
    val colorScheme = NimelssaColorScheme
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
