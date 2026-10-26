/**
 * Modul: :feature:composepreview
 * @author Thomas Schmid
 */
package com.codeforge.feature.composepreview

/**
 * Bereitet den Quelltext einer oder mehrerer Dateien für die isolierte Preview-Kompilierung vor.
 *
 * Im Ein-Datei-Modus (Rückfall/Standard):
 *  1. `package` entfällt (Preview-Klasse heißt `PreviewTargetKt`),
 *  2. Activity-Klassen und unauflösbare Imports werden entfernt,
 *  3. Projekt-Themes (`import <projekt>.ui.theme.XyzTheme`) werden durch einen Material3-Stub ersetzt.
 *
 * Im Mehr-Datei-Modus (Task 57):
 *  1. `package` bleibt erhalten, damit dateiübergreifende Bezüge funktionieren.
 *  2. Activity-Klassen und unauflösbare Imports werden entfernt.
 *  3. Echte Projekt-Themes werden mitkompiliert (kein Stub), aber bekannte SideEffect-Blöcke
 *     mit `WindowCompat` und `(view.context as Activity)` werden per Regel entfernt (Weg 2).
 */
internal object PreviewSourcePreparer {

    private val PACKAGE_LINE = Regex("""^\s*package\s+[\w.]+\s*;?\s*$""", RegexOption.MULTILINE)
    private val IMPORT_LINE = Regex("""^\s*import\s+([\w.]+)(?:\s+as\s+(\w+))?\s*;?\s*$""", RegexOption.MULTILINE)
    private val REMOVED_IMPORT_PREFIXES = listOf("androidx.activity.", "androidx.core.", "dagger.", "javax.inject.")
    private val ACTIVITY_CLASS_HEAD = Regex(
        """^[ \t]*(?:@[\w.]+(?:\([^)]*\))?[ \t]*\n?[ \t]*)*(?:(?:open|abstract|private|internal|public|final)[ \t]+)*class[ \t]+\w+[^{\n=]*\bComponentActivity\b|""" +
            """^[ \t]*(?:@[\w.]+(?:\([^)]*\))?[ \t]*\n?[ \t]*)*(?:(?:open|abstract|private|internal|public|final)[ \t]+)*class[ \t]+\w+[^{\n=]*:\s*[\w.]*Activity\b""",
        RegexOption.MULTILINE
    )

    private val EDIT_MODE_SIDE_EFFECT_HEAD = Regex(
        """^[ \t]*if\s*\(!\s*\w+\.isInEditMode\s*\)\s*\{""",
        RegexOption.MULTILINE
    )

    private val SIDE_EFFECT_HEAD = Regex(
        """^[ \t]*SideEffect\s*\{""",
        RegexOption.MULTILINE
    )

    private val LOCAL_VIEW_VAL = Regex(
        """^[ \t]*val\s+(\w+)\s*=\s*(?:androidx\.compose\.ui\.platform\.)?LocalView\.current\s*\n?""",
        RegexOption.MULTILINE
    )

    /**
     * Standard-Aufbereitung (Ein-Datei-Modus mit Stub und ohne Package).
     */
    fun prepare(source: String): String =
        prepare(source = source, keepPackage = false, stubProjectThemes = true, stripThemeSideEffects = true)

    /**
     * Mehrdatei-fähige Aufbereitung:
     * @param keepPackage Wenn true, bleibt die `package`-Zeile unverändert.
     * @param stubProjectThemes Wenn true, wird ein Theme-Stub injiziert. Wenn false, wird das echte Theme erwartet.
     * @param stripThemeSideEffects Wenn true, werden bekannte `WindowCompat`/`Activity`-SideEffects in Theme.kt entfernt.
     */
    fun prepare(
        source: String,
        keepPackage: Boolean = false,
        stubProjectThemes: Boolean = true,
        stripThemeSideEffects: Boolean = true
    ): String {
        var text = if (keepPackage) source else source.replace(PACKAGE_LINE, "")
        text = removeActivityClasses(text)

        if (stripThemeSideEffects) {
            text = removeThemeSideEffects(text)
        }

        val stubs = mutableListOf<String>()
        val rebuilt = StringBuilder()
        var last = 0
        for (m in IMPORT_LINE.findAll(text)) {
            val fqn = m.groupValues[1]
            val alias = m.groupValues[2]
            val simple = alias.ifEmpty { fqn.substringAfterLast('.') }
            val drop = REMOVED_IMPORT_PREFIXES.any { fqn.startsWith(it) }
            val projectTheme = stubProjectThemes && isProjectTheme(fqn, simple) && !declaresFunction(text, simple)

            rebuilt.append(text, last, m.range.first)
            if (projectTheme) {
                stubs += themeStub(simple)
            } else if (!drop) {
                rebuilt.append(m.value)
            }
            last = m.range.last + 1
        }
        rebuilt.append(text, last, text.length)
        val body = rebuilt.toString().trim()
        return if (stubs.isEmpty()) body + "\n" else body + "\n\n" + stubs.distinct().joinToString("\n\n") + "\n"
    }

    private fun isProjectTheme(fqn: String, simple: String): Boolean =
        simple.endsWith("Theme") && simple.first().isUpperCase() &&
            !fqn.startsWith("androidx.") && !fqn.startsWith("android.") && !fqn.startsWith("com.google.") &&
            fqn.contains(".ui.theme.")

    private fun declaresFunction(text: String, name: String): Boolean =
        Regex("""\bfun\s+$name\s*\(""").containsMatchIn(text)

    private fun themeStub(name: String): String = """
        @androidx.compose.runtime.Composable
        fun $name(content: @androidx.compose.runtime.Composable () -> Unit) {
            androidx.compose.material3.MaterialTheme(content = content)
        }
    """.trimIndent()

    private fun removeActivityClasses(text: String): String {
        var result = text
        while (true) {
            val head = ACTIVITY_CLASS_HEAD.find(result) ?: return result
            val open = result.indexOf('{', head.range.last)
            val end = if (open >= 0) matchingBrace(result, open) else -1
            result = if (end >= 0) {
                result.removeRange(head.range.first, end + 1)
            } else {
                result.removeRange(head.range.first, head.range.last + 1)
            }
        }
    }

    /**
     * Weg 2: Entfernt bekannte `SideEffect`-Blöcke mit `WindowCompat` oder `(view.context as Activity)`
     * aus Theme-Dateien, damit kein `unresolved reference: WindowCompat` oder Klassenfehler auftritt.
     */
    internal fun removeThemeSideEffects(text: String): String {
        var result = text

        // 1. Suche nach if (!view.isInEditMode) { ... } Blöcken, die SideEffect / WindowCompat enthalten
        while (true) {
            val head = EDIT_MODE_SIDE_EFFECT_HEAD.find(result) ?: break
            val open = result.indexOf('{', head.range.first)
            if (open < 0) break
            val end = matchingBrace(result, open)
            if (end < 0) break
            val blockContent = result.substring(head.range.first, end + 1)
            if (blockContent.contains("SideEffect") || blockContent.contains("WindowCompat") || blockContent.contains("Activity")) {
                result = result.removeRange(head.range.first, end + 1)
            } else {
                break
            }
        }

        // 2. Suche nach eigenständigen SideEffect { ... } Blöcken mit WindowCompat oder (as Activity)
        while (true) {
            val head = SIDE_EFFECT_HEAD.find(result) ?: break
            val open = result.indexOf('{', head.range.first)
            if (open < 0) break
            val end = matchingBrace(result, open)
            if (end < 0) break
            val blockContent = result.substring(head.range.first, end + 1)
            if (blockContent.contains("WindowCompat") || blockContent.contains("as Activity") || blockContent.contains("statusBarColor")) {
                result = result.removeRange(head.range.first, end + 1)
            } else {
                break
            }
        }

        // 3. Wenn "val view = LocalView.current" nun unbenutzt ist, entfernen
        val viewMatch = LOCAL_VIEW_VAL.find(result)
        if (viewMatch != null) {
            val varName = viewMatch.groupValues[1]
            val remainderWithoutVal = result.removeRange(viewMatch.range.first, viewMatch.range.last + 1)
            val bodyOnly = remainderWithoutVal.lines()
                .filterNot { it.trimStart().startsWith("import ") }
                .joinToString("\n")
            val isUsedElsewhere = Regex("""\b$varName\b""").containsMatchIn(bodyOnly)
            if (!isUsedElsewhere) {
                result = remainderWithoutVal
            }
        }

        // 4. Unbenutzte Imports wie SideEffect oder LocalView entfernen
        if (!Regex("""\bSideEffect\b""").containsMatchIn(result.replace(Regex("""^\s*import\s+.*$""", RegexOption.MULTILINE), ""))) {
            result = result.replace(Regex("""^\s*import\s+androidx\.compose\.runtime\.SideEffect\s*;?\s*\n?""", RegexOption.MULTILINE), "")
        }
        if (!Regex("""\bLocalView\b""").containsMatchIn(result.replace(Regex("""^\s*import\s+.*$""", RegexOption.MULTILINE), ""))) {
            result = result.replace(Regex("""^\s*import\s+androidx\.compose\.ui\.platform\.LocalView\s*;?\s*\n?""", RegexOption.MULTILINE), "")
        }

        return result
    }

    /** Index der zur `{` an [open] passenden `}`; berücksichtigt Strings, Zeichen und Kommentare. */
    internal fun matchingBrace(s: String, open: Int): Int {
        var depth = 0
        var i = open
        while (i < s.length) {
            val c = s[i]
            when {
                s.startsWith("//", i) -> i = s.indexOf('\n', i).let { if (it < 0) s.length else it }
                s.startsWith("/*", i) -> i = s.indexOf("*/", i).let { if (it < 0) s.length else it + 1 }
                s.startsWith("\"\"\"", i) -> i = s.indexOf("\"\"\"", i + 3).let { if (it < 0) s.length else it + 2 }
                c == '"' -> {
                    i++
                    while (i < s.length && s[i] != '"') { if (s[i] == '\\') i++; i++ }
                }
                c == '\'' -> {
                    i++
                    while (i < s.length && s[i] != '\'') { if (s[i] == '\\') i++; i++ }
                }
                c == '{' -> depth++
                c == '}' -> { depth--; if (depth == 0) return i }
            }
            i++
        }
        return -1
    }
}
