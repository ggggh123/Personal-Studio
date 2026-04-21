package com.example.personal_studio.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColors = darkColorScheme(
    primary = BrandBlue,
    onPrimary = Ink950,
    secondary = BrandYellow,
    onSecondary = Ink950,
    tertiary = BrandPurple,
    background = Ink950,
    onBackground = Ink100,
    surface = Ink900,
    onSurface = Ink100,
    surfaceVariant = Ink800,
    onSurfaceVariant = Ink100,
    error = BrandRed,
    onError = Ink950,
)

private val LightColors = lightColorScheme(
    primary = BrandBlue,
    onPrimary = Ink950,
    secondary = BrandYellow,
    onSecondary = Ink950,
    tertiary = BrandPurple,
    background = Ink050,
    onBackground = Ink950,
    surface = Ink050,
    onSurface = Ink950,
    surfaceVariant = Ink100,
    onSurfaceVariant = Ink950,
    error = BrandRed,
    onError = Ink050,
)

@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,  // keep brand identity; allow dynamic color later
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content,
    )
}
