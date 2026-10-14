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
    val monokai = ThemePreset("monokai", R.string.theme_preset_monokai, "#A6E22E", "#F92672", "#66D9EF")
    val darcula = ThemePreset("darcula", R.string.theme_preset_darcula, "#CC7832", "#6897BB", "#9876AA")
    val quietLight = ThemePreset("quiet_light", R.string.theme_preset_quiet_light, "#7A3E9D", "#4B83CD", "#AA3731")
    val ayuDark = ThemePreset("ayu_dark", R.string.theme_preset_ayu_dark, "#E6B450", "#39BAE6", "#FF3333")
    val solarizedDark = ThemePreset("solarized_dark", R.string.theme_preset_solarized_dark, "#268BD2", "#2AA198", "#859900")
    val githubLight = ThemePreset("github_light", R.string.theme_preset_github_light, "#0969DA", "#1A7F37", "#8250DF")
    val vsCodeDarkPlus = ThemePreset("vscode_dark_plus", R.string.theme_preset_vscode_dark_plus, "#007ACC", "#4EC9B0", "#CE9178")
    val notepadPlusPlus = ThemePreset("notepad_plus_plus", R.string.theme_preset_notepad_plus_plus, "#1E5B99", "#008000", "#A00000")
    val eclipse = ThemePreset("eclipse", R.string.theme_preset_eclipse, "#2C2255", "#F7941E", "#7B58A3")
    val cyberpunkNeon = ThemePreset("cyberpunk_neon", R.string.theme_preset_cyberpunk_neon, "#00F0FF", "#FF007F", "#FFE600")
    val forest = ThemePreset("forest", R.string.theme_preset_forest, "#2E7D32", "#558B2F", "#00695C")
    val ocean = ThemePreset("ocean", R.string.theme_preset_ocean, "#0277BD", "#00838F", "#1565C0")
    val sunset = ThemePreset("sunset", R.string.theme_preset_sunset, "#E64A19", "#F9A825", "#C2185B")
    val monochrome = ThemePreset("monochrome", R.string.theme_preset_monochrome, "#424242", "#616161", "#757575")
    val violet = ThemePreset("violet", R.string.theme_preset_violet, "#6A1B9A", "#8E24AA", "#5E35B1")

    val all = listOf(
        monokai,
        darcula,
        quietLight,
        ayuDark,
        solarizedDark,
        githubLight,
        vsCodeDarkPlus,
        notepadPlusPlus,
        eclipse,
        cyberpunkNeon,
        forest,
        ocean,
        sunset,
        monochrome,
        violet
    )

    fun findById(id: String): ThemePreset? = all.find { it.id == id }
}
