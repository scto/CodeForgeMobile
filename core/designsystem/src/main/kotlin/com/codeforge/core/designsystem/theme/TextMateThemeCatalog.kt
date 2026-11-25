package com.codeforge.core.designsystem.theme

import com.codeforge.core.datastore.proto.ThemeMode
import com.codeforge.core.domain.extension.ExtensionColorTheme

/**
 * Ein Editor-Theme (TextMate) mit ID und Anzeigename. Themes aus Plugins haben als [id] den Schlüssel
 * `ext:<plugin>/<theme>` und in [filePath] die TextMate-Datei.
 */
data class TextMateThemeItem(
    val id: String,
    val displayName: String,
    val isDark: Boolean = true,
    val filePath: String? = null
)

/** Verfügbare Editor-Themes: eingebaute und die aus installierten Plugins (Store). */
object TextMateThemeCatalog {
    const val AUTO_THEME_ID = "auto"
    const val DEFAULT_DARK_THEME = "dark_plus"
    const val DEFAULT_LIGHT_THEME = "light_plus"

    /** Eingebaute Themes. */
    val BUNDLED_THEMES: List<TextMateThemeItem> = listOf(
        TextMateThemeItem(DEFAULT_DARK_THEME, "Dark+ (Standard Dunkel)", isDark = true),
        TextMateThemeItem(DEFAULT_LIGHT_THEME, "Light+ (Standard Hell)", isDark = false)
    )

    /** Eingebaute Themes und alle Plugin-Farbschemata mit TextMate-Editor-Theme ([pluginThemes]). */
    fun getAvailableThemes(pluginThemes: List<ExtensionColorTheme> = emptyList()): List<TextMateThemeItem> =
        BUNDLED_THEMES + pluginThemes.mapNotNull { theme ->
            theme.textMateThemePath?.let { TextMateThemeItem(theme.key, theme.label, theme.isDark, it) }
        }

    /**
     * Löst das anzuwendende TextMate-Theme auf:
     * - "auto" oder leer: abhängig von [themeMode] und [systemDark] -> default dark oder light
     * - vorhandenes Theme: exakt dieser Wert
     * - unbekannter / nicht mehr vorhandener Wert: Rückfall auf "auto", ohne Absturz
     */
    fun resolveTheme(
        storedTheme: String,
        themeMode: ThemeMode,
        systemDark: Boolean,
        availableThemes: List<TextMateThemeItem> = BUNDLED_THEMES
    ): String {
        val isEffectiveDark = when (themeMode) {
            ThemeMode.DARK -> true
            ThemeMode.LIGHT -> false
            ThemeMode.SYSTEM, ThemeMode.UNRECOGNIZED -> systemDark
        }

        if (storedTheme.isBlank() || storedTheme.equals(AUTO_THEME_ID, ignoreCase = true)) {
            return if (isEffectiveDark) DEFAULT_DARK_THEME else DEFAULT_LIGHT_THEME
        }

        val exists = availableThemes.any { it.id.equals(storedTheme, ignoreCase = true) }
        return if (exists) {
            storedTheme
        } else {
            if (isEffectiveDark) DEFAULT_DARK_THEME else DEFAULT_LIGHT_THEME
        }
    }
}
