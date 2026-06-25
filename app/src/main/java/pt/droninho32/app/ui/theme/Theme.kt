package pt.droninho32.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = DnBlueLight,
    onPrimary = Color(0xFF001A41),
    primaryContainer = DnBlueDark,
    onPrimaryContainer = DnOnDark,
    secondary = DnCyan,
    error = DnRed,
    background = DnBackgroundDark,
    onBackground = DnOnDark,
    surface = DnSurfaceDark,
    onSurface = DnOnDark,
)

private val LightColors = lightColorScheme(
    primary = DnBlue,
    onPrimary = Color.White,
    primaryContainer = DnBlueLight,
    onPrimaryContainer = DnBlueDark,
    secondary = DnCyan,
    error = DnRed,
    background = DnBackgroundLight,
    onBackground = DnOnLight,
    surface = DnSurfaceLight,
    onSurface = DnOnLight,
)

@Composable
fun Droninho32Theme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        typography = DnTypography,
        content = content,
    )
}
