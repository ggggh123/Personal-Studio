package com.example.personal_studio.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val TerminalDarkScheme = darkColorScheme(
    primary = Phosphor,
    onPrimary = Void,
    secondary = Amber,
    onSecondary = Void,
    tertiary = Cyan,
    onTertiary = Void,
    background = Void,
    onBackground = Foam,
    surface = Void,
    onSurface = Foam,
    surfaceVariant = Deep,
    onSurfaceVariant = FoamMute,
    outline = Rule,
    outlineVariant = Dim,
    error = Carmine,
    onError = Void,
)

/**
 * The app is intentionally dark-only. We ignore the system dark-mode flag —
 * "夜机 · Terminal" has a single canonical aesthetic; a future light variant
 * (amber phosphor on ivory) may be added in P6.
 */
@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Void.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = TerminalDarkScheme,
        typography = TerminalTypography,
        shapes = TerminalShapes,
        content = content,
    )
}
