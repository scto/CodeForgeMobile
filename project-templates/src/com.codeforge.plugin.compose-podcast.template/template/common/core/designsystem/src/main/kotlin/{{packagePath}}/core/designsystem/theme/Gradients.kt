package {{packageName}}.core.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** Verläufe der App. */
object AppGradients {
    /** Diagonaler Akzentverlauf für Kopfbereiche und Knöpfe. */
    val accent: Brush
        @Composable @ReadOnlyComposable
        get() = Brush.linearGradient(
            colors = listOf(AppTheme.colors.gradientStart, AppTheme.colors.gradientEnd),
            start = Offset.Zero,
            end = Offset.Infinite
        )

    /** Weicher Verlauf von der Akzentfarbe in den Hintergrund. */
    val fade: Brush
        @Composable @ReadOnlyComposable
        get() = Brush.verticalGradient(
            listOf(AppTheme.colors.gradientStart.copy(alpha = 0.35f), Color.Transparent)
        )

    /** Verlauf einer Akzentfarbe (z. B. für die Farbauswahl in den Einstellungen). */
    fun forPalette(palette: AccentPalette): Brush =
        Brush.linearGradient(listOf(palette.gradientStart, palette.gradientEnd), start = Offset.Zero, end = Offset.Infinite)

    /** Fester, gut unterscheidbarer Verlauf je [seed] – z. B. für Cover ohne Bild. */
    fun forSeed(seed: Int): Brush {
        val pair = SeedPalettes[Math.floorMod(seed, SeedPalettes.size)]
        return Brush.linearGradient(listOf(pair.first, pair.second), start = Offset.Zero, end = Offset.Infinite)
    }

    private val SeedPalettes = listOf(
        Color(0xFF7F5AF0) to Color(0xFFE45AC9),
        Color(0xFF0077B6) to Color(0xFF00C2A8),
        Color(0xFFFF6B6B) to Color(0xFFFFB347),
        Color(0xFF2D9D5C) to Color(0xFFB5D96A),
        Color(0xFF3A1C71) to Color(0xFFD76D77),
        Color(0xFF1D2671) to Color(0xFFC33764),
        Color(0xFF134E5E) to Color(0xFF71B280),
        Color(0xFFF7971E) to Color(0xFFFFD200)
    )
}
