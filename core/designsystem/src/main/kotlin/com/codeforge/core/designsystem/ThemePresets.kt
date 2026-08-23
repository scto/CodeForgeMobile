// Modul: :core:designsystem
package com.codeforge.core.designsystem

data class ThemePreset(
    val id: String,
    val label: String,
    val primaryHex: String,
    val secondaryHex: String,
    val tertiaryHex: String
)

/**
 * Vordefinierte Farbschemata, wählbar in :feature:themebuilder. "custom" ist kein
 * Eintrag hier, sondern der Sonderfall, bei dem ThemeConfig.customPalette statt eines
 * Presets verwendet wird (siehe resolveCustomScheme in ColorSchemes.kt).
 */
object ThemePresets {
    val forest = ThemePreset("forest", "Wald", "#2E7D32", "#558B2F", "#00695C")
    val ocean = ThemePreset("ocean", "Ozean", "#0277BD", "#00838F", "#1565C0")
    val sunset = ThemePreset("sunset", "Sonnenuntergang", "#E64A19", "#F9A825", "#C2185B")
    val monochrome = ThemePreset("monochrome", "Monochrom", "#424242", "#616161", "#757575")
    val violet = ThemePreset("violet", "Violett", "#6A1B9A", "#8E24AA", "#5E35B1")

    val all = listOf(forest, ocean, sunset, monochrome, violet)

    fun findById(id: String): ThemePreset? = all.find { it.id == id }
}
