// Modul: :libs:theme-kit
package com.codeforge.libs.theme_kit

import com.codeforge.libs.color_utilities.contrast.Contrast
import com.codeforge.libs.color_utilities.dynamiccolor.ColorSpecs
import com.codeforge.libs.color_utilities.dynamiccolor.DynamicScheme
import com.codeforge.libs.color_utilities.hct.Hct
import com.codeforge.libs.color_utilities.palettes.TonalPalette
import com.codeforge.libs.color_utilities.utils.ColorUtils

/** Alle Farbrollen eines Schemas als ARGB, in der Reihenfolge von [ColorRole]. */
data class ColorRoles(val isDark: Boolean, val values: Map<ColorRole, Int>) {
    /** ARGB-Wert der Rolle. */
    operator fun get(role: ColorRole): Int = values.getValue(role)

    /** Rollen-Schlüssel → `#RRGGBB` (Format der Plugin-Farbschemata). */
    fun toHexMap(): Map<String, String> = ColorRole.entries.associate { it.key to ColorHex.format(get(it)) }
}

/** Erzeugt Material-3-Farbschemata aus einer [ThemeSpec] mit Googles material-color-utilities. */
object ThemeEngine {

    /** Schema für hell oder dunkel. Ungültige Grundfarbe → CodeForge-Indigo. */
    fun scheme(spec: ThemeSpec, isDark: Boolean): DynamicScheme {
        val sourceArgb = ColorHex.parse(spec.seed) ?: ColorHex.parse(ThemeSpec.CODEFORGE_SEED)!!
        val source = Hct.fromInt(sourceArgb)
        val variant = spec.style.variant
        val level = spec.contrast.level
        val colorSpec = ColorSpecs.get(DynamicScheme.DEFAULT_SPEC_VERSION)
        val platform = DynamicScheme.DEFAULT_PLATFORM
        fun palette(override: String?, derived: () -> TonalPalette): TonalPalette =
            ColorHex.parse(override)?.let { TonalPalette.fromInt(it) } ?: derived()
        return DynamicScheme(
            sourceColorHct = source,
            variant = variant,
            isDark = isDark,
            contrastLevel = level,
            platform = platform,
            specVersion = DynamicScheme.DEFAULT_SPEC_VERSION,
            primaryPalette = colorSpec.getPrimaryPalette(variant, source, isDark, platform, level),
            secondaryPalette = palette(spec.secondary) {
                colorSpec.getSecondaryPalette(variant, source, isDark, platform, level)
            },
            tertiaryPalette = palette(spec.tertiary) {
                colorSpec.getTertiaryPalette(variant, source, isDark, platform, level)
            },
            neutralPalette = palette(spec.neutral) {
                colorSpec.getNeutralPalette(variant, source, isDark, platform, level)
            },
            neutralVariantPalette = palette(spec.neutral) {
                colorSpec.getNeutralVariantPalette(variant, source, isDark, platform, level)
            },
            errorPalette = palette(spec.error) {
                colorSpec.getErrorPalette(variant, source, isDark, platform, level)
            },
        )
    }

    /** Alle 36 Rollen für hell oder dunkel. */
    fun roles(spec: ThemeSpec, isDark: Boolean): ColorRoles {
        val s = scheme(spec, isDark)
        return ColorRoles(isDark, ColorRole.entries.associateWith { it.read(s) })
    }

    /** Kontrastverhältnis zweier Farben (1 bis 21; 4,5 = WCAG AA für Text). */
    fun contrastRatio(foreground: Int, background: Int): Double =
        Contrast.ratioOfTones(ColorUtils.lstarFromArgb(foreground), ColorUtils.lstarFromArgb(background))
}
