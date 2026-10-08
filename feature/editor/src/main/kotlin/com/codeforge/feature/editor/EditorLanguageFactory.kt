/**
 * Modul: :feature:editor
 * @author Thomas Schmid
 */
package com.codeforge.feature.editor

import android.content.Context
import com.codeforge.feature.editor.lsp.LspCompletionProvider
import com.codeforge.feature.editor.treesitter.TreeSitterGrammar
import com.codeforge.feature.editor.treesitter.TreeSitterLanguageSupport
import io.github.rosemoe.sora.lang.Language
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage

enum class EditorLanguageType(val scopeName: String) {
    KOTLIN("source.kotlin"),
    JAVA("source.java"),
    XML("text.xml"),
    GRADLE_KTS("source.kotlin"),
    JSON("source.json"),
    PLAIN("text.plain");

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

    fun create(
        context: Context,
        path: String,
        languageType: EditorLanguageType,
        useTreeSitter: Boolean,
        completionProvider: LspCompletionProvider?
    ): Language {
        if (useTreeSitter) {
            TreeSitterGrammar.fromPath(path)?.let { grammar ->
                TreeSitterLanguageSupport.createLanguage(context, grammar)?.let { return it }
            }
        }

        // TextMateLanguage.create(scope, autoCompleteSymbols) — zweiter Parameter aktiviert
        // sora-editors eingebaute Symbol-Vervollständigung (Klammern/Bezeichner aus dem
        // aktuellen Dokument). Die LSP-gestützte Vervollständigung (Snippets, Member-Access)
        // wird zusätzlich darüber gelegt, siehe [LspAwareLanguage] / [SoraCodeEditor].
        val base = TextMateLanguage.create(languageType.scopeName, true)
        return if (completionProvider != null) LspAwareLanguage(base, completionProvider) else base
    }
}
