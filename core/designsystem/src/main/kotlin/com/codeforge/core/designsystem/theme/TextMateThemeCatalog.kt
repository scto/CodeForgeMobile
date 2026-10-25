package com.codeforge.core.designsystem.theme

import com.codeforge.core.datastore.proto.ThemeMode
import java.io.File

data class TextMateThemeItem(
    val id: String,
    val displayName: String,
    val isDark: Boolean = true
)

object TextMateThemeCatalog {
    const val AUTO_THEME_ID = "auto"
    const val DEFAULT_DARK_THEME = "dark_plus"
    const val DEFAULT_LIGHT_THEME = "light_plus"

    val BUNDLED_THEMES: List<TextMateThemeItem> = listOf(
        TextMateThemeItem(DEFAULT_DARK_THEME, "Dark+ (Standard Dunkel)", isDark = true),
        TextMateThemeItem(DEFAULT_LIGHT_THEME, "Light+ (Standard Hell)", isDark = false)
    )

    fun getAvailableThemes(extensionsThemesDir: File? = null): List<TextMateThemeItem> {
        val result = mutableListOf<TextMateThemeItem>()
        result.addAll(BUNDLED_THEMES)

        if (extensionsThemesDir != null && extensionsThemesDir.isDirectory) {
            val installedFiles = extensionsThemesDir.listFiles { file ->
                file.isFile && (file.name.endsWith(".json") || file.name.endsWith(".tmTheme"))
            }
            if (installedFiles != null) {
                for (file in installedFiles) {
                    val id = file.nameWithoutExtension
                    if (result.none { it.id.equals(id, ignoreCase = true) }) {
                        result.add(
                            TextMateThemeItem(
                                id = id,
                                displayName = id.replace('_', ' ').replace('-', ' ')
                                    .split(' ')
                                    .filter { it.isNotBlank() }
                                    .joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } },
                                isDark = !id.lowercase().contains("light")
                            )
                        )
                    }
                }
            }
        }

        return result
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
