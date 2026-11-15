// Modul: :core:domain
package com.codeforge.core.domain.refactoring

/**
 * Ersatz für `workspace/willRenameFiles`, wenn kein Sprachserver Folgeänderungen liefert: Wird ein Ordner in einem
 * Quellverzeichnis (`src/<set>/java|kotlin/…`) umbenannt oder verschoben, ändert sich sein Package. Angepasst werden
 * - `package`-Zeilen der Dateien *in* diesem Ordner (auch Unterordner),
 * - `import`-Zeilen in allen Kotlin-/Java-Dateien,
 * - voll qualifizierte Namen in XML (`android:name="alt.pkg.X"`, `<alt.pkg.CustomView`).
 *
 * Nicht erfasst: voll qualifizierte Namen im Code ohne `import` und Klassen desselben alten Packages in anderen Ordnern,
 * die die verschobenen Klassen ohne `import` nutzten.
 */
object PackageRenamePlanner {

    private val SOURCE_ROOT = Regex("""/src/[^/]+/(?:java|kotlin)(?=/|$)""")
    private val CODE_EXTENSIONS = setOf("kt", "kts", "java")

    /** Package eines Ordners in einem Quellverzeichnis, sonst `null` (auch für das Quellverzeichnis selbst). */
    fun packageOf(directory: String): String? {
        val match = SOURCE_ROOT.findAll(directory.trimEnd('/')).lastOrNull() ?: return null
        val rest = directory.trimEnd('/').substring(match.range.last + 1).trim('/')
        return rest.replace('/', '.').takeIf { it.isNotEmpty() && it.split('.').all(::isIdentifier) }
    }

    /** Altes und neues Package, wenn [oldDir] → [newDir] ein Package ändert; sonst `null`. */
    fun packageChange(oldDir: String, newDir: String): Pair<String, String>? {
        val old = packageOf(oldDir) ?: return null
        val new = packageOf(newDir) ?: return null
        return if (old == new) null else old to new
    }

    /** Dateien, deren Inhalt geprüft werden muss. */
    fun isCandidate(path: String): Boolean = path.substringAfterLast('.', "").lowercase() in CODE_EXTENSIONS + "xml"

    /**
     * Neuer Inhalt von [path] für das Package-Umbenennen [oldPackage] → [newPackage]; `null`, wenn unverändert.
     * [insideRenamedDir] = Datei liegt im umbenannten Ordner (nur dann wird ihre `package`-Zeile angepasst).
     */
    fun rewrite(
        path: String,
        content: String,
        oldPackage: String,
        newPackage: String,
        insideRenamedDir: Boolean
    ): RenameFollowUp? {
        val old = Regex.escape(oldPackage)
        var edits = 0
        fun Regex.swap(text: String, transform: (MatchResult) -> String): String =
            replace(text) { m -> edits++; transform(m) }

        var result = content
        if (path.substringAfterLast('.', "").lowercase() in CODE_EXTENSIONS) {
            if (insideRenamedDir) {
                result = Regex("""(?m)^(\s*package\s+)$old(?=[.;\s]|$)""").swap(result) { it.groupValues[1] + newPackage }
            }
            result = Regex("""(?m)^(\s*import\s+(?:static\s+)?)$old(?=\.)""").swap(result) { it.groupValues[1] + newPackage }
        } else {
            result = Regex("""(["<])$old(?=\.[A-Za-z_])""").swap(result) { it.groupValues[1] + newPackage }
        }
        return if (edits == 0) null else RenameFollowUp(path, result, edits)
    }

    private fun isIdentifier(segment: String): Boolean =
        segment.isNotEmpty() && (segment[0].isLetter() || segment[0] == '_') && segment.all { it.isLetterOrDigit() || it == '_' }
}
