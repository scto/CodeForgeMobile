/**
 * Modul: :feature:composepreview
 * @author Thomas Schmid
 */
package com.codeforge.feature.composepreview

/**
 * Bereitet den Quelltext einer Datei für die isolierte Preview-Kompilierung vor.
 *
 * Der Preview-Compiler sieht nur die Compose-Runtime-JARs und android.jar, nicht das übrige Projekt.
 * Eine typische `MainActivity.kt` enthält aber `ComponentActivity` (androidx.activity), das Projekt-Theme
 * (`ui.theme`) usw. -> "unresolved reference". Deshalb:
 *  1. `package` entfällt (die Preview-Klasse heißt immer `PreviewTargetKt`),
 *  2. Activity-Klassen und Imports aus androidx.activity/androidx.core/dagger/hilt werden entfernt,
 *  3. Projekt-Themes (`import <projekt>.ui.theme.XyzTheme`), die nicht in der Datei stehen, werden durch eine
 *     Material3-Ersatzfunktion gleichen Namens ersetzt. Die Preview zeigt dann das Standard-Material3-Theme,
 *     nicht die Farben des Projekt-Themes.
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

    fun prepare(source: String): String {
        var text = source.replace(PACKAGE_LINE, "")
        text = removeActivityClasses(text)

        val stubs = mutableListOf<String>()
        val rebuilt = StringBuilder()
        var last = 0
        for (m in IMPORT_LINE.findAll(text)) {
            val fqn = m.groupValues[1]
            val alias = m.groupValues[2]
            val simple = alias.ifEmpty { fqn.substringAfterLast('.') }
            val drop = REMOVED_IMPORT_PREFIXES.any { fqn.startsWith(it) }
            val projectTheme = isProjectTheme(fqn, simple) && !declaresFunction(text, simple)
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
                // Klasse ohne Rumpf: nur die Kopfzeile entfernen
                result.removeRange(head.range.first, head.range.last + 1)
            }
        }
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
