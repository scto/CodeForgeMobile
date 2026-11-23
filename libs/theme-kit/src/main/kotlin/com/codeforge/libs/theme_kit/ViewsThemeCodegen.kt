// Modul: :libs:theme-kit
package com.codeforge.libs.theme_kit

/** Erzeugt XML-Ressourcen für View-basierte Apps und trägt die Farben in den App-Style ein. */
object ViewsThemeCodegen {

    /** Dateiname der erzeugten Farbressourcen (in `values/` und `values-night/`). */
    const val COLORS_FILE = "theme_colors.xml"

    private const val BEGIN = "<!-- CodeForge Theme-Builder: Anfang (wird bei jeder Übernahme ersetzt) -->"
    private const val END = "<!-- CodeForge Theme-Builder: Ende -->"

    private val MATERIAL_COMPONENTS_ROLES = setOf(
        ColorRole.PRIMARY, ColorRole.ON_PRIMARY, ColorRole.SECONDARY, ColorRole.ON_SECONDARY,
        ColorRole.ERROR, ColorRole.ON_ERROR, ColorRole.BACKGROUND, ColorRole.ON_BACKGROUND,
        ColorRole.SURFACE, ColorRole.ON_SURFACE,
    )

    /** Welche Attribute ein Parent-Theme kennt. */
    enum class ThemeFamily {
        /** `Theme.Material3.*`: alle Rollen. */
        MATERIAL3,

        /** `Theme.MaterialComponents.*`: nur die älteren Grundrollen. */
        MATERIAL_COMPONENTS,

        /** AppCompat oder Plattform-Themes: nur `colorPrimary`, `colorAccent` und Hintergrund. */
        BASIC,
    }

    /** Familie aus dem `parent`-Attribut. */
    fun familyOf(parent: String?): ThemeFamily = when {
        parent == null -> ThemeFamily.BASIC
        parent.contains("Material3") -> ThemeFamily.MATERIAL3
        parent.contains("MaterialComponents") -> ThemeFamily.MATERIAL_COMPONENTS
        else -> ThemeFamily.BASIC
    }


    /** `theme_colors.xml` für hell (`values/`) oder dunkel (`values-night/`). */
    fun colorsFile(spec: ThemeSpec, isDark: Boolean): String {
        val roles = ThemeEngine.roles(spec, isDark)
        return buildString {
            appendLine("""<?xml version="1.0" encoding="utf-8"?>""")
            appendLine(ComposeThemeCodegen.header(spec).lines().joinToString("\n") { "<!-- ${it.removePrefix("// ")} -->" })
            appendLine("<resources>")
            for (role in ColorRole.entries) {
                appendLine("""    <color name="${role.resourceName}">${ColorHex.format(roles[role])}</color>""")
            }
            appendLine("</resources>")
        }
    }

    /** Die Style-Einträge für [family]. */
    fun styleItems(family: ThemeFamily): List<Pair<String, String>> = when (family) {
        ThemeFamily.MATERIAL3 -> ColorRole.entries.mapNotNull { r -> r.xmlAttr?.let { it to "@color/${r.resourceName}" } }
        ThemeFamily.MATERIAL_COMPONENTS -> ColorRole.entries
            .filter { it in MATERIAL_COMPONENTS_ROLES }
            .map { r -> r.xmlAttr!! to "@color/${r.resourceName}" }
        ThemeFamily.BASIC -> listOf(
            "colorPrimary" to "@color/${ColorRole.PRIMARY.resourceName}",
            "colorAccent" to "@color/${ColorRole.SECONDARY.resourceName}",
            "android:colorBackground" to "@color/${ColorRole.BACKGROUND.resourceName}",
        )
    }

    /** Ergebnis von [patchThemesFile]. */
    data class StylePatch(val text: String, val found: Boolean, val family: ThemeFamily)

    /**
     * Trägt die Farben in den Style [styleName] von `themes.xml` ein: vorhandene Einträge derselben Attribute werden
     * entfernt, ein markierter Block wird vor `</style>` gesetzt (bei erneuter Übernahme ersetzt). Plattform-Themes
     * (`android:Theme.*`) bekommen `android:`-Attribute.
     */
    fun patchThemesFile(text: String, styleName: String): StylePatch {
        val styleRegex = Regex(
            """<style\s+name\s*=\s*"${Regex.escape(styleName)}"([^>]*?)(/>|>(.*?)</style>)""",
            RegexOption.DOT_MATCHES_ALL,
        )
        val match = styleRegex.find(text) ?: return StylePatch(text, found = false, family = ThemeFamily.BASIC)
        val attrs = match.groupValues[1]
        val parent = Regex("""parent\s*=\s*"([^"]*)"""").find(attrs)?.groupValues?.get(1)
        val family = familyOf(parent)
        val platform = parent?.startsWith("android:") == true || parent?.startsWith("@android:") == true
        val items = styleItems(family).map { (attr, value) ->
            val name = if (platform && family == ThemeFamily.BASIC && !attr.startsWith("android:")) "android:$attr" else attr
            name to value
        }
        val body = if (match.groupValues[2] == "/>") "" else match.groupValues[3]
        val managed = items.map { it.first }.toSet()
        val cleaned = body
            .replace(Regex("""\s*${Regex.escape(BEGIN)}.*?${Regex.escape(END)}""", RegexOption.DOT_MATCHES_ALL), "")
            .lines()
            .filterNot { line ->
                val n = Regex("""<item\s+name\s*=\s*"([^"]+)"""").find(line)?.groupValues?.get(1)
                n != null && n in managed && line.contains("</item>")
            }
            .joinToString("\n")
            .trimEnd()
        val block = buildString {
            appendLine()
            appendLine("        $BEGIN")
            items.forEach { (n, v) -> appendLine("""        <item name="$n">$v</item>""") }
            appendLine("        $END")
            append("    ")
        }
        val newStyle = """<style name="$styleName"$attrs>${cleaned.ifBlank { "" }}$block</style>"""
        return StylePatch(text.replaceRange(match.range, newStyle), found = true, family = family)
    }
}
