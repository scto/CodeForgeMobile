package {{packageName}}.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/** Akzentfarben der App; jede bringt eine Primärfarbe und einen Verlauf mit. */
enum class AccentPalette(val primaryLight: Color, val primaryDark: Color, val gradientStart: Color, val gradientEnd: Color) {
    VIOLET(Color(0xFF6750A4), Color(0xFFD0BCFF), Color(0xFF7F5AF0), Color(0xFFE45AC9)),
    OCEAN(Color(0xFF00658F), Color(0xFF8BCEFF), Color(0xFF0077B6), Color(0xFF00C2A8)),
    SUNSET(Color(0xFFB3261E), Color(0xFFFFB4A9), Color(0xFFFF6B6B), Color(0xFFFFB347)),
    FOREST(Color(0xFF2E6B3A), Color(0xFF94D7A0), Color(0xFF2D9D5C), Color(0xFFB5D96A))
}

/** Farben, die Material 3 nicht kennt: Verläufe, Glas-Flächen, Erfolg. */
@Immutable
data class ExtendedColors(
    val gradientStart: Color,
    val gradientEnd: Color,
    val onGradient: Color,
    val glass: Color,
    val glassBorder: Color,
    val success: Color
)

internal val Ink = Color(0xFF1B1B1F)
internal val Paper = Color(0xFFFFFBFF)
internal val Night = Color(0xFF121218)
internal val NightSurface = Color(0xFF1C1B22)

/** Kartenfarben (z. B. für Notizen) mit heller und dunkler Variante. */
enum class CardTint(val light: Color, val dark: Color) {
    NONE(Color(0xFFF4F1F8), Color(0xFF26252C)),
    ROSE(Color(0xFFFFD9E2), Color(0xFF5E2A3B)),
    PEACH(Color(0xFFFFDBC8), Color(0xFF5C3420)),
    LEMON(Color(0xFFFFF1B8), Color(0xFF4F4515)),
    MINT(Color(0xFFC8F2DA), Color(0xFF1F4A33)),
    SKY(Color(0xFFCDE5FF), Color(0xFF1C3E5C)),
    LAVENDER(Color(0xFFE8DDFF), Color(0xFF3E3163))
}
