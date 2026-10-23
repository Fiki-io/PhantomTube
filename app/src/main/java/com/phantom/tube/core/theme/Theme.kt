package com.phantom.tube.core.theme

import android.app.Activity
import com.phantom.tube.core.util.findActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = YouTubeRed,
    onPrimary = Color.White,
    secondary = YouTubeSurfaceLight,
    onSecondary = TextPrimary,
    tertiary = YouTubeSurfaceLight,
    background = YouTubeDark,
    onBackground = TextPrimary,
    surface = YouTubeSurface,
    onSurface = TextPrimary,
    surfaceVariant = YouTubeSurfaceLight,
    onSurfaceVariant = TextSecondary,
    surfaceTint = Color.Transparent,
    surfaceContainerLowest = YouTubeDark,
    surfaceContainerLow = YouTubeSurface,
    surfaceContainer = YouTubeSurface,
    surfaceContainerHigh = YouTubeSurface,
    surfaceContainerHighest = YouTubeSurfaceLight
)

@Composable
fun PhantomTheme(
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context.findActivity())?.window
            if (window != null) {
                window.statusBarColor = Color.Transparent.toArgb()
                window.navigationBarColor = Color.Transparent.toArgb()
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = false
                controller.isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = PhantomTypography,
        content = content
    )
}
