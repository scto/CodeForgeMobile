// Modul: :core:designsystem
package com.codeforge.core.designsystem

import com.codeforge.core.domain.extension.ExtensionColorTheme

/**
 * Überführt Farbauswahlen aus Versionen bis 3.2: Die 15 eingebauten Presets gibt es nur noch als Store-Pakete
 * „(Farbschema)“, die eigene Palette nur noch über den Theme-Builder („Als App-Farbschema installieren“).
 */
object LegacyThemeMigration {
    private const val CUSTOM = "custom"
    private val PRESET_IDS = setOf(
        "monokai", "darcula", "quiet_light", "ayu_dark", "solarized_dark", "github_light", "vscode_dark_plus",
        "notepad_plus_plus", "eclipse", "cyberpunk_neon", "forest", "ocean", "sunset", "monochrome", "violet",
    )

    /** Store-Plugin, das ein früheres Preset ersetzt (`ayu_dark` → `com.codeforge.plugin.ayu-dark.theme`). */
    fun pluginIdFor(presetId: String): String = "com.codeforge.plugin.${presetId.replace('_', '-')}.theme"

    /** Ob [colorSchemeId] ein Wert aus der Zeit vor dem Umbau ist. */
    fun isLegacy(colorSchemeId: String): Boolean = colorSchemeId == CUSTOM || colorSchemeId in PRESET_IDS

    /**
     * Neuer Wert für `color_scheme_id` oder `null`, wenn (noch) nichts zu ändern ist.
     *
     * - eigene Palette → `""` (CodeForge-Standard)
     * - Preset mit installiertem Store-Paket → dessen Variante in der passenden Helligkeit
     * - Preset ohne Paket → `null`: bleibt gespeichert und wird übernommen, sobald das Paket installiert ist;
     *   bis dahin gilt der CodeForge-Standard.
     */
    fun migrate(colorSchemeId: String, available: List<ExtensionColorTheme>, wantDark: Boolean): String? {
        if (colorSchemeId == CUSTOM) return ""
        if (colorSchemeId !in PRESET_IDS) return null
        val variants = available.filter { it.pluginId == pluginIdFor(colorSchemeId) }
        return (variants.firstOrNull { it.isDark == wantDark } ?: variants.firstOrNull())?.key
    }
}
