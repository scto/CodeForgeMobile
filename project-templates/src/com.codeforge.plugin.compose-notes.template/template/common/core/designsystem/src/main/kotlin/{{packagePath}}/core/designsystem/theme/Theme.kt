package {{packageName}}.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val LocalExtendedColors = staticCompositionLocalOf {
    ExtendedColors(Color.Unspecified, Color.Unspecified, Color.Unspecified, Color.Unspecified, Color.Unspecified, Color.Unspecified)
}
private val LocalSpacing = staticCompositionLocalOf { Spacing() }
private val LocalIsDark = staticCompositionLocalOf { false }

/**
 * Eigenes Theme der App: Farben aus [accent], helles oder dunkles Schema, eigene Formen, Typografie,
 * Verläufe und Abstände. Zugriff im Code über [AppTheme.colors], [AppTheme.spacing], [AppTheme.isDark].
 */
@Composable
fun AppTheme(darkTheme: Boolean = isSystemInDarkTheme(), accent: AccentPalette = AccentPalette.VIOLET, content: @Composable () -> Unit) {
    val colorScheme = remember(darkTheme, accent) { colorScheme(darkTheme, accent) }
    val extended = remember(darkTheme, accent) {
        ExtendedColors(
            gradientStart = accent.gradientStart,
            gradientEnd = accent.gradientEnd,
            onGradient = Color.White,
            glass = if (darkTheme) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.72f),
            glassBorder = if (darkTheme) Color.White.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.9f),
            success = if (darkTheme) Color(0xFF7DDC9B) else Color(0xFF1E8E4E)
        )
    }
    CompositionLocalProvider(
        LocalExtendedColors provides extended,
        LocalSpacing provides Spacing(),
        LocalIsDark provides darkTheme
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            shapes = AppShapes,
            typography = AppTypography,
            content = content
        )
    }
}

/** Zugriff auf die Teile des Themes, die Material 3 nicht abdeckt. */
object AppTheme {
    val colors: ExtendedColors
        @Composable @ReadOnlyComposable
        get() = LocalExtendedColors.current

    val spacing: Spacing
        @Composable @ReadOnlyComposable
        get() = LocalSpacing.current

    val isDark: Boolean
        @Composable @ReadOnlyComposable
        get() = LocalIsDark.current
}

private fun colorScheme(dark: Boolean, accent: AccentPalette): ColorScheme = if (dark) {
    darkColorScheme(
        primary = accent.primaryDark,
        onPrimary = Night,
        primaryContainer = accent.gradientStart.copy(alpha = 0.35f).compositeOn(NightSurface),
        onPrimaryContainer = Color.White,
        secondary = accent.gradientEnd,
        tertiary = accent.gradientEnd,
        background = Night,
        onBackground = Color(0xFFE6E1E9),
        surface = Night,
        onSurface = Color(0xFFE6E1E9),
        surfaceVariant = NightSurface,
        surfaceContainer = NightSurface,
        surfaceContainerHigh = Color(0xFF26252C),
        surfaceContainerHighest = Color(0xFF302F37),
        onSurfaceVariant = Color(0xFFC9C4D0),
        outline = Color(0xFF938F99)
    )
} else {
    lightColorScheme(
        primary = accent.primaryLight,
        onPrimary = Color.White,
        primaryContainer = accent.gradientStart.copy(alpha = 0.16f).compositeOn(Paper),
        onPrimaryContainer = Ink,
        secondary = accent.gradientEnd,
        tertiary = accent.gradientEnd,
        background = Paper,
        onBackground = Ink,
        surface = Paper,
        onSurface = Ink,
        surfaceVariant = Color(0xFFF1ECF4),
        surfaceContainer = Color(0xFFF6F1F9),
        surfaceContainerHigh = Color(0xFFF0EBF3),
        surfaceContainerHighest = Color(0xFFEAE5ED),
        onSurfaceVariant = Color(0xFF49454F),
        outline = Color(0xFF79747E)
    )
}

/** Mischt eine halbtransparente Farbe auf einen deckenden Hintergrund. */
private fun Color.compositeOn(background: Color): Color = Color(
    red = red * alpha + background.red * (1 - alpha),
    green = green * alpha + background.green * (1 - alpha),
    blue = blue * alpha + background.blue * (1 - alpha),
    alpha = 1f
)
