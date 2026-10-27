/**
 * Modul: :core:domain
 * @author Thomas Schmid
 */
package com.codeforge.core.domain.extension

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Inhalt einer Farbschema-Datei eines Plugins (`contributes.colorThemes[].path`):
 * ```
 * { "colors": { "primary": "#88C0D0", "background": "#2E3440", ... },
 *   "textmateTheme": "syntax/nord.json" }
 * ```
 * [colors] bildet Material-3-Rollennamen auf ARGB ab; nicht angegebene Rollen werden aus dem Standardschema abgeleitet.
 * Unbekannte Rollennamen werden ignoriert (vorwärtskompatibel), ungültige Hex-Werte sind ein Fehler.
 */
data class ColorThemeFile(
    val colors: Map<String, Long>,
    val textMateTheme: String? = null
)

/** Ein verfügbares Plugin-Farbschema samt aufgelöster Dateien (Schlüssel für `ThemeConfig.color_scheme_id`: [key]). */
data class ExtensionColorTheme(
    val pluginId: String,
    val themeId: String,
    val label: String,
    val isDark: Boolean,
    val colors: Map<String, Long>,
    /** Absoluter Pfad einer TextMate-Theme-Datei für den Editor, falls das Plugin eine mitliefert und sie existiert. */
    val textMateThemePath: String? = null
) {
    val key: String get() = keyOf(pluginId, themeId)

    companion object {
        const val PREFIX = "ext:"
        fun keyOf(pluginId: String, themeId: String) = "$PREFIX$pluginId/$themeId"
        fun isExtensionKey(id: String) = id.startsWith(PREFIX)
    }
}

object ColorThemeFileParser {

    val ROLES: Set<String> = setOf(
        "primary", "onPrimary", "primaryContainer", "onPrimaryContainer",
        "secondary", "onSecondary", "secondaryContainer", "onSecondaryContainer",
        "tertiary", "onTertiary", "tertiaryContainer", "onTertiaryContainer",
        "error", "onError", "errorContainer", "onErrorContainer",
        "background", "onBackground", "surface", "onSurface", "surfaceVariant", "onSurfaceVariant",
        "outline", "outlineVariant", "inverseSurface", "inverseOnSurface", "inversePrimary",
        "surfaceTint", "scrim", "surfaceBright", "surfaceDim",
        "surfaceContainerLowest", "surfaceContainerLow", "surfaceContainer",
        "surfaceContainerHigh", "surfaceContainerHighest"
    )

    /** `#RGB`, `#RRGGBB` oder `#AARRGGBB` → ARGB (alpha 0xFF bei 3/6 Stellen); sonst null. */
    fun parseHex(value: String): Long? {
        val h = value.trim().removePrefix("#")
        val expanded = when (h.length) {
            3 -> h.map { "$it$it" }.joinToString("")
            6, 8 -> h
            else -> return null
        }
        val n = expanded.toLongOrNull(16) ?: return null
        return if (expanded.length == 6) 0xFF000000L or n else n
    }

    fun parse(text: String): Result<ColorThemeFile> = runCatching {
        val root = Json.parseToJsonElement(text) as? JsonObject
            ?: throw IllegalArgumentException("Farbschema muss ein JSON-Objekt sein")
        val colorsObj = root["colors"] as? JsonObject
            ?: throw IllegalArgumentException("Farbschema: 'colors' fehlt")
        val colors = LinkedHashMap<String, Long>()
        for ((role, v) in colorsObj) {
            if (role !in ROLES) continue
            val raw = (v as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
                ?: throw IllegalArgumentException("Farbe '$role' muss ein Text sein")
            colors[role] = parseHex(raw) ?: throw IllegalArgumentException("Ungültige Farbe für '$role': $raw")
        }
        require(colors.isNotEmpty()) { "Farbschema enthält keine bekannten Farbrollen" }
        val tm = (root["textmateTheme"] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
        if (tm != null) {
            require(!tm.startsWith("/") && !tm.startsWith("\\") && tm.split('/', '\\').none { it == ".." }) {
                "Ungültiger Pfad für textmateTheme: $tm"
            }
        }
        ColorThemeFile(colors, tm)
    }
}

/** Alle Farbschemata aktivierter Plugins (Dateien bereits gelesen/validiert; defekte werden übersprungen). */
interface ExtensionThemeRepository {
    val themes: kotlinx.coroutines.flow.StateFlow<List<ExtensionColorTheme>>
    val iconThemes: kotlinx.coroutines.flow.StateFlow<List<ExtensionIconTheme>>
}
