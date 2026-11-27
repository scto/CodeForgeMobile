/**
 * Plugin: String-Editor – String-Dateien im Projekt finden und gruppieren (reines Kotlin, testbar).
 */
package com.codeforge.plugin.strings

import com.codeforge.libs.plugin_api.IdeFileEntry
import java.util.Locale

/** Eine String-Datei, z. B. `app/src/main/res/values-de/strings.xml`. */
data class StringFile(
    val path: String,
    /** Modul relativ zum Projekt, z. B. `app` oder `feature/editor` (leer = Projektwurzel). */
    val module: String,
    /** Quellsatz, z. B. `main`, `debug`. */
    val sourceSet: String,
    /** Qualifier hinter `values`, z. B. `de`, `b+sr+Latn`, `night`; leer = Standard. */
    val qualifier: String,
    val fileName: String,
) {
    /** Ordner `res` dieser Datei. */
    val resDir: String get() = path.substringBeforeLast("/values").let { it }

    /** Gleiche Datei im Standardordner `values/` (Ausgangssprache). */
    val defaultPath: String get() = "$resDir/values/$fileName"

    /** Anzeige des Qualifiers: Sprachname oder Rohwert. */
    fun label(): String = if (qualifier.isEmpty()) "Standard" else languageName(qualifier)?.let { "$it ($qualifier)" } ?: qualifier

    companion object {
        private val NAME = Regex("^strings.*\\.xml$")

        /** Datei aus [path] relativ zu [root], wenn sie eine String-Datei in `res/values*` ist. */
        fun of(root: String, path: String): StringFile? {
            val rel = path.removePrefix(root).trimStart('/')
            val parts = rel.split('/')
            if (parts.size < 3) return null
            val file = parts.last()
            val values = parts[parts.size - 2]
            if (!NAME.matches(file) || !values.startsWith("values") || parts[parts.size - 3] != "res") return null
            val beforeRes = parts.dropLast(3)
            val srcIdx = beforeRes.lastIndexOf("src")
            val module = if (srcIdx >= 0) beforeRes.take(srcIdx).joinToString("/") else beforeRes.joinToString("/")
            val sourceSet = if (srcIdx >= 0) beforeRes.getOrNull(srcIdx + 1).orEmpty() else ""
            return StringFile(path, module, sourceSet, values.removePrefix("values").removePrefix("-"), file)
        }

        /** Sprachname zu einem Qualifier wie `de`, `pt-rBR`, `b+sr+Latn` oder `null` (z. B. bei `night`). */
        fun languageName(qualifier: String): String? {
            val first = qualifier.split('-').first()
            val tag = when {
                first.startsWith("b+") -> first.removePrefix("b+").replace('+', '-')
                first.length in 2..3 && first.all { it.isLowerCase() } -> {
                    val region = qualifier.split('-').getOrNull(1)?.takeIf { it.startsWith("r") && it.length == 3 }?.drop(1)
                    if (region != null) "$first-$region" else first
                }
                else -> return null
            }
            val locale = Locale.forLanguageTag(tag)
            val name = locale.getDisplayName(Locale.GERMAN)
            return name.takeIf { it.isNotBlank() && !it.equals(tag, ignoreCase = true) }
        }
    }
}

/** Sucht String-Dateien unter [root]; [list] liefert den Inhalt eines Ordners (z. B. `ide.files.list`). */
object StringFileScanner {
    private val SKIP = setOf(".git", ".gradle", "build", ".idea", "node_modules", ".cxx", "intermediates", "generated")

    fun scan(root: String, maxDepth: Int = 10, list: (String) -> List<IdeFileEntry>): List<StringFile> {
        val out = ArrayList<StringFile>()
        fun walk(dir: String, depth: Int) {
            if (depth > maxDepth) return
            for (e in runCatching { list(dir) }.getOrDefault(emptyList())) {
                if (e.isDirectory) {
                    if (e.name !in SKIP && !e.name.startsWith(".")) walk(e.path, depth + 1)
                } else {
                    StringFile.of(root, e.path)?.let { out += it }
                }
            }
        }
        walk(root, 0)
        return out.sortedWith(compareBy({ it.module }, { it.sourceSet != "main" }, { it.sourceSet }, { it.qualifier }, { it.fileName }))
    }
}
