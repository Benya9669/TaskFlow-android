package ru.taskflow.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val ColorAccentLight = androidx.compose.ui.graphics.Color(0xFFC9C2FF)
private val ColorErrorLight = androidx.compose.ui.graphics.Color(0xFFFFB4AB)

private val LightColors = lightColorScheme(
    primary = Accent,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    primaryContainer = AccentContainer,
    onPrimaryContainer = Ink,
    background = CanvasLight,
    onBackground = Ink,
    surface = SurfaceLight,
    onSurface = Ink,
    surfaceVariant = SurfaceMutedLight,
    onSurfaceVariant = MutedInk,
    error = Danger,
)

private val DarkColors = darkColorScheme(
    primary = ColorAccentLight,
    onPrimary = Ink,
    primaryContainer = Accent,
    onPrimaryContainer = InkDark,
    background = CanvasDark,
    onBackground = InkDark,
    surface = SurfaceDark,
    onSurface = InkDark,
    surfaceVariant = SurfaceMutedDark,
    onSurfaceVariant = MutedInkDark,
    error = ColorErrorLight,
)

@Composable
fun TaskFlowTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = TaskFlowTypography,
        shapes = TaskFlowShapes,
        content = content,
    )
}
