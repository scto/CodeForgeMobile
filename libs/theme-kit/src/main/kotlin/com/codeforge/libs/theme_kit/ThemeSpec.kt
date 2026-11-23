// Modul: :libs:theme-kit
package com.codeforge.libs.theme_kit

import com.codeforge.libs.color_utilities.dynamiccolor.Variant
import kotlinx.serialization.Serializable

/** Palettenstil (wie Material Theme Builder / MaterialKolor). */
@Serializable
enum class ThemeStyle(internal val variant: Variant) {
    TONAL_SPOT(Variant.TONAL_SPOT),
    NEUTRAL(Variant.NEUTRAL),
    VIBRANT(Variant.VIBRANT),
    EXPRESSIVE(Variant.EXPRESSIVE),
    FIDELITY(Variant.FIDELITY),
    CONTENT(Variant.CONTENT),
    RAINBOW(Variant.RAINBOW),
    FRUIT_SALAD(Variant.FRUIT_SALAD),
    MONOCHROME(Variant.MONOCHROME),
}

/** Kontraststufe; [level] wie in material-color-utilities (0 = Standard, 1 = maximal). */
@Serializable
enum class ThemeContrast(val level: Double) {
    STANDARD(0.0),
    MEDIUM(0.5),
    HIGH(1.0),
}

/**
 * Beschreibung eines Themes: alles, was nötig ist, um es jederzeit identisch neu zu erzeugen.
 * Wird im Projekt als `.codeforge/theme.json` gespeichert und vom Theme-Builder wieder geöffnet.
 *
 * Farben als `#RRGGBB`; `null` bei [secondary], [tertiary], [neutral], [error] = aus der Grundfarbe ableiten.
 */
@Serializable
data class ThemeSpec(
    val seed: String = CODEFORGE_SEED,
    val style: ThemeStyle = ThemeStyle.TONAL_SPOT,
    val contrast: ThemeContrast = ThemeContrast.STANDARD,
    val secondary: String? = null,
    val tertiary: String? = null,
    val neutral: String? = null,
    val error: String? = null,
    val name: String = "",
) {
    companion object {
        /** Blau-Lila (Indigo) aus dem CodeForgeMobile-Icon. */
        const val CODEFORGE_SEED = "#6366F1"

        /** Cyan-Akzent aus dem CodeForgeMobile-Icon. */
        const val CODEFORGE_ACCENT = "#06B6D4"

        /** Die Farbgebung von CodeForgeMobile selbst (farbtreu, damit das Indigo des Icons kräftig bleibt). */
        val CODEFORGE = ThemeSpec(seed = CODEFORGE_SEED, style = ThemeStyle.FIDELITY, tertiary = CODEFORGE_ACCENT, name = "CodeForge")
    }
}
