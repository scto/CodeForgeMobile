// Modul: :libs:theme-kit
package com.codeforge.libs.theme_kit

/** Erzeugt Compose-Quelltext für ein Theme und passt eine vorhandene `Theme.kt` an. */
object ComposeThemeCodegen {

    /** Dateiname der erzeugten Farbdatei im Ordner `ui/theme`. */
    const val COLORS_FILE = "ThemeColors.kt"

    private const val LIGHT_FACTORY = "lightColorScheme"
    private const val DARK_FACTORY = "darkColorScheme"
    private const val LIGHT_SCHEME = "AppLightColorScheme"
    private const val DARK_SCHEME = "AppDarkColorScheme"

    /**
     * `ThemeColors.kt`: alle Rollen als `Color`-Konstanten (`primaryLight`, `primaryDark`, …) und die beiden
     * Schemata `AppLightColorScheme` / `AppDarkColorScheme`. Die Datei gehört dem Theme-Builder und wird bei jeder
     * Übernahme ersetzt.
     */
    fun colorsFile(packageName: String, spec: ThemeSpec): String {
        val light = ThemeEngine.roles(spec, isDark = false)
        val dark = ThemeEngine.roles(spec, isDark = true)
        return buildString {
            appendLine(header(spec))
            appendLine("package $packageName")
            appendLine()
            appendLine("import androidx.compose.material3.darkColorScheme")
            appendLine("import androidx.compose.material3.lightColorScheme")
            appendLine("import androidx.compose.ui.graphics.Color")
            appendLine()
            for (role in ColorRole.entries) {
                appendLine("val ${role.key}Light = Color(${ColorHex.kotlinLiteral(light[role])})")
            }
            appendLine()
            for (role in ColorRole.entries) {
                appendLine("val ${role.key}Dark = Color(${ColorHex.kotlinLiteral(dark[role])})")
            }
            appendLine()
            appendScheme(LIGHT_SCHEME, LIGHT_FACTORY, "Light")
            appendLine()
            appendScheme(DARK_SCHEME, DARK_FACTORY, "Dark")
        }
    }

    private fun StringBuilder.appendScheme(name: String, factory: String, suffix: String) {
        appendLine("val $name = $factory(")
        for (role in ColorRole.entries) appendLine("    ${role.key} = ${role.key}$suffix,")
        appendLine(")")
    }

    /** Ergebnis von [patchThemeFile]. */
    data class ThemePatch(val text: String, val schemesReplaced: Boolean, val dynamicColorDisabled: Boolean)

    /**
     * Ersetzt in `Theme.kt` die Aufrufe `lightColorScheme(…)` / `darkColorScheme(…)` durch die erzeugten Schemata und
     * stellt `dynamicColor` standardmäßig aus (sonst sähe man das Theme ab Android 12 nie). Alles andere bleibt.
     */
    fun patchThemeFile(text: String): ThemePatch {
        var out = text
        var replaced = 0
        for ((factory, target) in listOf(LIGHT_FACTORY to LIGHT_SCHEME, DARK_FACTORY to DARK_SCHEME)) {
            val call = findBalancedCall(out, factory) ?: continue
            out = out.replaceRange(call, target)
            replaced++
        }
        val dynamicDefault = Regex("""(dynamicColor\s*:\s*Boolean\s*=\s*)true""")
        val dynamicChanged = dynamicDefault.containsMatchIn(out)
        out = dynamicDefault.replace(out) { it.groupValues[1] + "false" }
        out = removeImport(out, "androidx.compose.material3.$LIGHT_FACTORY")
        out = removeImport(out, "androidx.compose.material3.$DARK_FACTORY")
        val alreadyPatched = LIGHT_SCHEME in out && DARK_SCHEME in out
        return ThemePatch(out, schemesReplaced = replaced == 2 || alreadyPatched, dynamicColorDisabled = dynamicChanged)
    }

    // Bereich `name(…)` mit ausgeglichenen Klammern; nur echte Aufrufe, keine Importe.
    private fun findBalancedCall(text: String, name: String): IntRange? {
        val m = Regex("""(?<![\w.])$name\s*\(""").find(text) ?: return null
        var depth = 0
        for (i in m.range.last until text.length) {
            when (text[i]) {
                '(' -> depth++
                ')' -> if (--depth == 0) return m.range.first..i
            }
        }
        return null
    }

    private fun removeImport(text: String, fqName: String): String =
        if (Regex("""(?<![\w.])${fqName.substringAfterLast('.')}\s*\(""").containsMatchIn(text)) {
            text
        } else {
            text.replace(Regex("""(?m)^import ${Regex.escape(fqName)}\s*\n"""), "")
        }

    /** Erste Zeile jeder erzeugten Datei: woher sie kommt und wie man sie neu erzeugt. */
    fun header(spec: ThemeSpec): String =
        "// Erzeugt vom CodeForgeMobile Theme-Builder – nicht von Hand ändern, sondern im Theme-Builder bearbeiten.\n" +
            "// Grundfarbe ${spec.seed}, Stil ${spec.style}, Kontrast ${spec.contrast}" +
            listOfNotNull(
                spec.secondary?.let { "Sekundär $it" },
                spec.tertiary?.let { "Tertiär $it" },
                spec.neutral?.let { "Neutral $it" },
                spec.error?.let { "Fehler $it" },
            ).joinToString("") { ", $it" } + "."
}
