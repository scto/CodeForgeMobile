// Modul: :core:designsystem
package com.codeforge.core.designsystem

import androidx.annotation.StringRes
import com.codeforge.core.resources.R

data class ThemePreset(
    val id: String,
    @StringRes val labelRes: Int,
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
    val forest = ThemePreset("forest", R.string.theme_preset_forest, "#2E7D32", "#558B2F", "#00695C")
    val ocean = ThemePreset("ocean", R.string.theme_preset_ocean, "#0277BD", "#00838F", "#1565C0")
    val sunset = ThemePreset("sunset", R.string.theme_preset_sunset, "#E64A19", "#F9A825", "#C2185B")
    val monochrome = ThemePreset("monochrome", R.string.theme_preset_monochrome, "#424242", "#616161", "#757575")
    val violet = ThemePreset("violet", R.string.theme_preset_violet, "#6A1B9A", "#8E24AA", "#5E35B1")

    val all = listOf(forest, ocean, sunset, monochrome, violet)

    fun findById(id: String): ThemePreset? = all.find { it.id == id }
}
