/**
 * Modul: :feature:editor
 * @author Thomas Schmid
 */
package com.codeforge.feature.editor

import android.content.Context
import android.util.Log
import com.codeforge.feature.editor.lsp.LspAwareLanguage
import com.codeforge.feature.editor.lsp.LspCompletionProvider
import com.codeforge.feature.editor.treesitter.TreeSitterGrammar
import com.codeforge.feature.editor.treesitter.TreeSitterLanguageSupport
import io.github.rosemoe.sora.lang.EmptyLanguage
import io.github.rosemoe.sora.lang.Language
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage

enum class EditorLanguageType(val scopeName: String) {
    KOTLIN("source.kotlin"),
    JAVA("source.java"),
    XML("text.xml"),
    GRADLE_KTS("source.kotlin"),
    JSON("source.json"),
    PLAIN("text.plain");

    /**
     * `true`, wenn in `assets/textmate/languages.json` eine Grammatik für [scopeName] registriert ist.
     * [PLAIN] hat bewusst keine (kein TextMate-Scope „text.plain“): `TextMateLanguage.create` wirft dafür
     * eine [IllegalArgumentException] („Language … scope name not found“).
     */
    val hasGrammar: Boolean get() = this != PLAIN

    companion object {
        fun fromPath(path: String): EditorLanguageType = when (path.substringAfterLast('.', "")) {
            "kt" -> KOTLIN
            "kts" -> GRADLE_KTS
            "java" -> JAVA
            "xml" -> XML
            "json" -> JSON
            else -> PLAIN
        }
    }
}

/**
 * Entscheidet pro Datei, ob TreeSitter (präziser, benötigt native Libs — siehe
 * TREESITTER.md) oder TextMate (regelbasiert, immer verfügbar) als
 * [io.github.rosemoe.sora.lang.Language]-Backend verwendet wird, und verdrahtet in
 * jedem Fall den LSP-gestützten [LspCompletionProvider] für Auto-Completion-Snippets.
 */
object EditorLanguageFactory {

    private const val TAG = "EditorLanguageFactory"

    fun create(
        context: Context,
        path: String,
        languageType: EditorLanguageType,
        useTreeSitter: Boolean,
        completionProvider: LspCompletionProvider?,
        /** Scope einer Plugin-Grammatik (nur relevant für Dateien ohne eingebaute Grammatik, d. h. [EditorLanguageType.PLAIN]). */
        extensionScope: String? = null
    ): Language {
        // Tree-sitter nur, wenn die Sprache ein Farbschema bekommt: TsLanguage wird unten mit leerem
        // Theme-Block erzeugt -> alle Token in Standardfarbe (kein Highlighting). Bis ein tsTheme-Mapping
        // existiert, übernimmt TextMate (TextMateColorScheme) das Highlighting.
        if (useTreeSitter && TREE_SITTER_THEMED) {
            TreeSitterGrammar.fromPath(path)?.let { grammar ->
                TreeSitterLanguageSupport.createLanguage(context, grammar)?.let { return it }
            }
        }

        // TextMateLanguage.create(scope, autoCompleteSymbols) — zweiter Parameter aktiviert
        // sora-editors eingebaute Symbol-Vervollständigung (Klammern/Bezeichner aus dem
        // aktuellen Dokument). Die LSP-gestützte Vervollständigung (Snippets, Member-Access)
        // wird zusätzlich darüber gelegt, siehe [LspAwareLanguage] / [SoraCodeEditor].
        // Dateien ohne Grammatik (PLAIN: .md, .txt, .toml, .yml, .sh, .gradle …) oder ein nicht
        // registrierter Scope dürfen den Editor nie zum Absturz bringen: Rückfall auf EmptyLanguage
        // (kein Highlighting, Text bleibt bearbeitbar).
        val base: Language = if (languageType == EditorLanguageType.PLAIN && extensionScope != null) {
            runCatching { TextMateLanguage.create(extensionScope, true) }
                .onFailure { Log.w(TAG, "Plugin-Grammatik '$extensionScope' nicht nutzbar – Rückfall auf Klartext", it) }
                .getOrNull() ?: EmptyLanguage()
        } else if (languageType.hasGrammar) {
            runCatching { TextMateLanguage.create(languageType.scopeName, true) }
                .onFailure {
                    Log.w(TAG, "Keine TextMate-Grammatik für Scope '${languageType.scopeName}' – Rückfall auf Klartext", it)
                }
                .getOrNull() ?: EmptyLanguage()
        } else {
            EmptyLanguage()
        }
        val languageId = when (languageType) {
            EditorLanguageType.KOTLIN, EditorLanguageType.GRADLE_KTS -> "kotlin"
            EditorLanguageType.JAVA -> "java"
            EditorLanguageType.XML -> "xml"
            EditorLanguageType.JSON -> "json"
            EditorLanguageType.PLAIN -> "plaintext"
        }
        return if (completionProvider != null) LspAwareLanguage(base, completionProvider, languageId) else base
    }
}

/** `true`, sobald TreeSitterLanguageSupport ein tsTheme-Mapping (Farben je Capture) setzt. */
private const val TREE_SITTER_THEMED = false
