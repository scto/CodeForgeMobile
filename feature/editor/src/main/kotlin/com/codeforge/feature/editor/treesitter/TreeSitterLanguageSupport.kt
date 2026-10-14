/**
 * Modul: :feature:editor
 * @author Thomas Schmid
 *
 * TreeSitter-Sprachunterstützung für sora-editor (language-treesitter). Im Unterschied zu
 * TextMate (regelbasiert, rein JSON) benötigt TreeSitter pro Sprache eine kompilierte native
 * Parser-Bibliothek (libtree-sitter-kotlin.so etc.) — siehe TREESITTER.md, analog zur
 * proot/libtalloc-Problematik in :libs:terminal-engine.
 *
 * TODO(Thomas)-WICHTIG: Zusätzlich zu den Pro-Sprache-Bibliotheken (`libtree-sitter-<sprache>.so`)
 * wird eine gemeinsame CORE-Bibliothek benötigt — die eigentliche JNI-Bridge/Tree-sitter-Runtime,
 * auf der `io.github.rosemoe.sora.langs.treesitter.*` (TsLanguage/TsLanguageSpec) aufsetzt. Das
 * `language-treesitter`-Modul von sora-editor bringt diese NICHT automatisch mit — laut
 * sora-editor-Doku ("du musst die Sprachimplementierungen aus android-tree-sitter beziehen")
 * ist das AndroidIDEOfficial/android-tree-sitter-Projekt (Maven-Gruppe
 * `com.itsaky.androidide.treesitter`) die De-facto-Quelle dafür. Siehe TREESITTER.md für den
 * vollständigen, recherchierten Stand (Artefakt-Koordinaten, welche Sprachen dort bereits
 * vorkompiliert vorliegen). Ohne diese Core-Library schlägt JEDER `loadLibrary`-Aufruf unten
 * fehl (das Pro-Sprache-`.so` hat ungelöste Symbole ohne die Runtime) — das Laden wird daher
 * jetzt zentral in [ensureCoreLibraryLoaded] vorgenommen, bevor eine Pro-Sprache-Bibliothek
 * geladen wird.
 *
 * Diese Klasse kapselt den Verfügbarkeits-Check und liefert `null`, wenn die native
 * Parser-Bibliothek für eine Sprache nicht gebunden ist, sodass [EditorLanguageFactory]
 * transparent auf TextMate zurückfallen kann.
 */
package com.codeforge.feature.editor.treesitter

import android.content.Context
import com.codeforge.core.resources.R
import com.codeforge.core.resources.Res
import com.itsaky.androidide.treesitter.TSLanguage
import io.github.rosemoe.sora.editor.ts.TsLanguage
import io.github.rosemoe.sora.editor.ts.TsLanguageSpec
import io.github.rosemoe.sora.lang.Language

enum class TreeSitterGrammar(val libraryName: String, val queryAssetDir: String) {
    KOTLIN("tree-sitter-kotlin", "treesitter/kotlin"),
    JAVA("tree-sitter-java", "treesitter/java"),
    JSON("tree-sitter-json", "treesitter/json"),
    XML("tree-sitter-xml", "treesitter/xml"),
    CPP("tree-sitter-cpp", "treesitter/cpp"),
    C("tree-sitter-c", "treesitter/c"),
    BASH("tree-sitter-bash", "treesitter/bash"),
    CMAKE("tree-sitter-cmake", "treesitter/cmake"),
    TOML("tree-sitter-toml", "treesitter/toml"),
    YAML("tree-sitter-yaml", "treesitter/yaml");

    companion object {
        fun fromPath(path: String): TreeSitterGrammar? {
            val fileName = path.substringAfterLast('/').substringAfterLast('\\')
            if (fileName.equals("CMakeLists.txt", ignoreCase = true)) {
                return CMAKE
            }
            return when (path.substringAfterLast('.', "")) {
                "kt", "kts" -> KOTLIN
                "java" -> JAVA
                "json" -> JSON
                "xml" -> XML
                "cpp", "cc", "cxx", "hpp", "hh", "hxx" -> CPP
                "c", "h" -> C
                "sh", "bash" -> BASH
                // ✅ gelöst: "CMakeLists.txt" wird oben direkt anhand des Dateinamens erkannt,
                // ".cmake"-Dateien (z. B. Helper-Module) über die Dateiendung.
                "cmake" -> CMAKE
                "toml" -> TOML
                "yml", "yaml" -> YAML
                else -> null
            }
        }
    }
}

object TreeSitterLanguageSupport {

    private val availability = mutableMapOf<TreeSitterGrammar, Boolean>()

    /**
     * Name der gemeinsamen Core-/Runtime-Bibliothek (JNI-Bridge), die vor jeder
     * Pro-Sprache-Bibliothek geladen werden muss. Siehe Klassen-Kommentar oben sowie
     * TREESITTER.md. Je nach tatsächlich verwendeter `android-tree-sitter`-Version kann der
     * Name abweichen (ältere Versionen: `android-tree-sitter`, neuere: schlicht
     * `tree-sitter`) — PRÜFEN, welcher Name zur tatsächlich eingebundenen .aar-Version passt
     * (z. B. per `unzip -l` auf die aufgelöste .aar im Gradle-Cache), sobald die Dependency
     * aus TREESITTER.md ergänzt ist, und hier ggf. korrigieren.
     */
    private const val CORE_LIBRARY_NAME = "android-tree-sitter"

    private var coreLibraryLoaded: Boolean? = null

    private fun ensureCoreLibraryLoaded(): Boolean = coreLibraryLoaded ?: run {
        val loaded = runCatching { System.loadLibrary(CORE_LIBRARY_NAME) }.isSuccess
        if (!loaded) {
            android.util.Log.w(
                "TreeSitterLanguageSupport",
                Res.string(R.string.editor_core_bibliothek_nicht_gefunden_treesit, CORE_LIBRARY_NAME)
            )
        }
        coreLibraryLoaded = loaded
        loaded
    }

    /**
     * Prüft per `System.loadLibrary` (abgefangen), ob die native Parser-Bibliothek für
     * [grammar] in dieser Build-Variante gebunden ist — erst nachdem die Core-Bibliothek
     * erfolgreich geladen wurde (s. o.). Ergebnis wird pro Prozesslaufzeit zwischengespeichert,
     * da `loadLibrary` nach dem ersten (fehlgeschlagenen) Versuch wiederholbar, aber unnötig
     * teuer ist.
     */
    fun isAvailable(grammar: TreeSitterGrammar): Boolean = availability.getOrPut(grammar) {
        if (!ensureCoreLibraryLoaded()) return@getOrPut false
        runCatching { System.loadLibrary(grammar.libraryName) }.isSuccess
    }

    /**
     * Erzeugt eine TreeSitter-[Language]-Instanz für [grammar], oder `null` falls die
     * native Bibliothek fehlt bzw. die Query-Dateien (highlights.scm etc.) unter
     * `assets/[TreeSitterGrammar.queryAssetDir]` nicht vorhanden sind (siehe TREESITTER.md).
     */
    fun createLanguage(context: Context, grammar: TreeSitterGrammar): Language? {
        if (!isAvailable(grammar)) return null
        return runCatching {
            val highlightScm = context.assets.open("${grammar.queryAssetDir}/highlights.scm")
                .bufferedReader().use { it.readText() }
            val blocksScm = runCatching {
                context.assets.open("${grammar.queryAssetDir}/blocks.scm").bufferedReader().use { it.readText() }
            }.getOrDefault("")
            val tsLang = TSLanguage.loadLanguage(context, grammar.libraryName)
            val spec = TsLanguageSpec(
                language = tsLang,
                highlightScmSource = highlightScm,
                codeBlocksScmSource = blocksScm
            )
            TsLanguage(spec) { }
        }.onFailure {
            android.util.Log.w(
                "TreeSitterLanguageSupport",
                Res.string(R.string.editor_treesitter_query_assets_fuer_fehlen, grammar),
                it
            )
        }.getOrNull()
    }
}
