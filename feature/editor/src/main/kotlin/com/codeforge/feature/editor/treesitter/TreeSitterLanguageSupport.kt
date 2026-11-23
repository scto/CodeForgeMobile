/**
 * Modul: :feature:editor
 * @author Thomas Schmid
 *
 * TreeSitter-Sprachunterstützung für sora-editor (language-treesitter). Im Unterschied zu
 * TextMate (regelbasiert, rein JSON) benötigt TreeSitter pro Sprache eine kompilierte native
 * Parser-Bibliothek (libtree-sitter-kotlin.so etc.) — siehe TREESITTER.md, analog zur
 * proot/libtalloc-Problematik in :libs:terminal-engine.
 *
 * Die Core-Laufzeit (`libandroid-tree-sitter.so`) und die Parser für Kotlin, Java, JSON, XML, C, C++ kommen als AARs
 * aus `com.itsaky.androidide.treesitter`, Bash/CMake/TOML/YAML aus `src/main/jniLibs` (siehe TREESITTER.md). Farben:
 * [TsCaptureMapping] + [TreeSitterColors]; zusätzliche Prädikate: [ExtraPredicate].
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
import io.github.rosemoe.sora.editor.ts.predicate.builtin.MatchPredicate
import io.github.rosemoe.sora.editor.ts.predicate.builtin.SetCapturePredicate
import io.github.rosemoe.sora.lang.Language

/** Unterstützte Tree-sitter-Grammatiken. */
enum class TreeSitterGrammar(
    val langName: String,
    val queryAssetDir: String,
    val libraryName: String = "tree-sitter-$langName"
) {
    KOTLIN("kotlin", "treesitter/kotlin"),
    JAVA("java", "treesitter/java"),
    JSON("json", "treesitter/json"),
    XML("xml", "treesitter/xml"),
    CPP("cpp", "treesitter/cpp"),
    C("c", "treesitter/c"),
    BASH("bash", "treesitter/bash"),
    CMAKE("cmake", "treesitter/cmake"),
    TOML("toml", "treesitter/toml"),
    YAML("yaml", "treesitter/yaml");

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

/** Tree-sitter-Unterstützung für den Editor. */
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
            runCatching {
                android.util.Log.w(
                    "TreeSitterLanguageSupport",
                    Res.string(R.string.editor_core_bibliothek_nicht_gefunden_treesit, CORE_LIBRARY_NAME)
                )
            }
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

    private fun loadTsLanguage(context: Context, grammar: TreeSitterGrammar): TSLanguage {
        val precompiled = runCatching {
            val className = when (grammar) {
                TreeSitterGrammar.KOTLIN -> "com.itsaky.androidide.treesitter.kotlin.TSLanguageKotlin"
                TreeSitterGrammar.JAVA -> "com.itsaky.androidide.treesitter.java.TSLanguageJava"
                TreeSitterGrammar.JSON -> "com.itsaky.androidide.treesitter.json.TSLanguageJson"
                TreeSitterGrammar.XML -> "com.itsaky.androidide.treesitter.xml.TSLanguageXml"
                TreeSitterGrammar.CPP -> "com.itsaky.androidide.treesitter.cpp.TSLanguageCpp"
                TreeSitterGrammar.C -> "com.itsaky.androidide.treesitter.c.TSLanguageC"
                else -> null
            }
            className?.let { name ->
                val clazz = Class.forName(name)
                val method = clazz.getMethod("getInstance")
                method.invoke(null) as? TSLanguage
            }
        }.getOrNull()

        if (precompiled != null) {
            return precompiled
        }

        return TSLanguage.loadLanguage(context, grammar.langName)
            ?: throw IllegalStateException("TSLanguage für ${grammar.langName} konnte nicht geladen werden")
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
            val localsScm = runCatching {
                context.assets.open("${grammar.queryAssetDir}/locals.scm").bufferedReader().use { it.readText() }
            }.getOrDefault("")
            val tsLang = loadTsLanguage(context, grammar)
            val spec = TsLanguageSpec(
                language = tsLang,
                highlightScmSource = highlightScm,
                codeBlocksScmSource = blocksScm,
                bracketsScmSource = "",
                localsScmSource = localsScm,
                predicates = listOf(MatchPredicate, SetCapturePredicate, ExtraPredicate),
            )
            // Jede Capture-Gruppe bekommt ihre Farbkennung; die Farben setzt TreeSitterColors.apply je Theme.
            TsLanguage(spec) {
                for ((capture, group) in TsCaptureMapping.rules) {
                    group.style applyTo capture
                }
            }
        }.onFailure {
            android.util.Log.w(
                "TreeSitterLanguageSupport",
                Res.string(R.string.editor_treesitter_query_assets_fuer_fehlen, grammar),
                it
            )
        }.getOrNull()
    }
}

