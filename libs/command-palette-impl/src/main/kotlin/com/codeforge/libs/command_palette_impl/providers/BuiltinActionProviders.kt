/**
 * Modul: :libs:command-palette-impl
 *
 * Anbieter für die eingebauten Editor-Aktionen, je Bereich einer. Jede Aktion hat eine Regel, wann sie in der
 * aktuellen Lage funktioniert (Sprachserver-Fähigkeiten, Dateityp). Unbekannte Fähigkeiten (`capabilities == null`
 * bei laufendem Server) gelten als vorhanden – wie in der Werkzeugleiste.
 */
package com.codeforge.libs.command_palette_impl.providers

import com.codeforge.core.domain.model.LspServerCapabilities
import com.codeforge.core.domain.model.supportsCodeActionKind
import com.codeforge.core.resources.R
import com.codeforge.core.resources.Res
import com.codeforge.libs.command_palette_api.BuiltinPaletteAction
import com.codeforge.libs.command_palette_api.BuiltinPaletteAction.AI_ASSISTANT
import com.codeforge.libs.command_palette_api.BuiltinPaletteAction.DEFINITION
import com.codeforge.libs.command_palette_api.BuiltinPaletteAction.EXTRACT_STRING
import com.codeforge.libs.command_palette_api.BuiltinPaletteAction.FIX_ALL
import com.codeforge.libs.command_palette_api.BuiltinPaletteAction.FOLD_ALL
import com.codeforge.libs.command_palette_api.BuiltinPaletteAction.FOLD_TOGGLE
import com.codeforge.libs.command_palette_api.BuiltinPaletteAction.FORMAT
import com.codeforge.libs.command_palette_api.BuiltinPaletteAction.GO_TO_LINE
import com.codeforge.libs.command_palette_api.BuiltinPaletteAction.HOVER
import com.codeforge.libs.command_palette_api.BuiltinPaletteAction.IMPLEMENTATION
import com.codeforge.libs.command_palette_api.BuiltinPaletteAction.LSP_SESSIONS
import com.codeforge.libs.command_palette_api.BuiltinPaletteAction.ORGANIZE_IMPORTS_LOCAL
import com.codeforge.libs.command_palette_api.BuiltinPaletteAction.ORGANIZE_IMPORTS_SERVER
import com.codeforge.libs.command_palette_api.BuiltinPaletteAction.OUTLINE
import com.codeforge.libs.command_palette_api.BuiltinPaletteAction.QUICK_FIX
import com.codeforge.libs.command_palette_api.BuiltinPaletteAction.REFACTOR
import com.codeforge.libs.command_palette_api.BuiltinPaletteAction.REFERENCES
import com.codeforge.libs.command_palette_api.BuiltinPaletteAction.RENAME
import com.codeforge.libs.command_palette_api.BuiltinPaletteAction.SAVE
import com.codeforge.libs.command_palette_api.BuiltinPaletteAction.SEARCH
import com.codeforge.libs.command_palette_api.BuiltinPaletteAction.SIGNATURE_HELP
import com.codeforge.libs.command_palette_api.BuiltinPaletteAction.TYPE_DEFINITION
import com.codeforge.libs.command_palette_api.BuiltinPaletteAction.UNFOLD_ALL
import com.codeforge.libs.command_palette_api.PaletteAction
import com.codeforge.libs.command_palette_api.PaletteCategory
import com.codeforge.libs.command_palette_api.PaletteContext
import com.codeforge.libs.command_palette_api.PaletteItem
import com.codeforge.libs.command_palette_api.PaletteProvider

/** Aus [PaletteContext] abgeleitete Fakten für die Regeln. */
class PaletteFacts(context: PaletteContext) {
    val ext: String = context.extension
    val hasFile: Boolean = context.filePath != null
    val serverRunning: Boolean = context.isLspConnected
    private val lsp = context.isLspConnected && hasFile
    private val caps = context.capabilities

    /** Server läuft, Datei offen und [flag] gemeldet (oder Fähigkeiten unbekannt). */
    fun has(flag: (LspServerCapabilities) -> Boolean): Boolean = lsp && (caps == null || flag(caps))

    /** Wie [has] für eine Code-Action-Art. */
    fun kind(kind: String): Boolean = lsp && (caps == null || caps.supportsCodeActionKind(kind))

    companion object {
        val STRING_EXTRACT_EXTENSIONS = setOf("kt", "java", "xml")
        val KOTLIN_EXTENSIONS = setOf("kt", "kts")
    }
}

/** Regel: wann eine eingebaute Aktion angeboten wird. */
typealias PaletteRule = PaletteFacts.() -> Boolean

/** Anbieter eines Bereichs mit eingebauten Aktionen; die Reihenfolge von [rules] ist die Anzeige-Reihenfolge. */
open class BuiltinActionProvider(
    override val id: String,
    private val category: PaletteCategory,
    private val rules: List<Pair<BuiltinPaletteAction, PaletteRule>>,
    override val order: Int = PaletteProvider.DEFAULT_ORDER,
) : PaletteProvider {

    /** Aktionen dieses Bereichs, die in [context] verfügbar sind. */
    fun actions(context: PaletteContext): List<BuiltinPaletteAction> {
        val facts = PaletteFacts(context)
        return rules.filter { (_, applies) -> facts.applies() }.map { it.first }
    }

    override fun items(context: PaletteContext): List<PaletteItem> = actions(context).map { action ->
        PaletteItem(PaletteAction.Builtin(action), Res.string(BuiltinTitles.titleRes(action)), category, shortcut = BuiltinTitles.shortcut(action))
    }
}

/** Umbau: Schnellkorrektur, Refactoring, Umbenennen, String auslagern. */
object RefactorPaletteProvider : BuiltinActionProvider(
    "refactor", PaletteCategory.REFACTOR,
    listOf(
        QUICK_FIX to { has { it.codeActionProvider } },
        REFACTOR to { kind("refactor") || (hasFile && ext in PaletteFacts.STRING_EXTRACT_EXTENSIONS) },
        RENAME to { has { it.renameProvider } },
        EXTRACT_STRING to { hasFile && ext in PaletteFacts.STRING_EXTRACT_EXTENSIONS },
    ),
    order = 10,
)

/** Navigation: Definition, Typ, Implementierung, Verwendungen, Gliederung, Zeile. */
object NavigationPaletteProvider : BuiltinActionProvider(
    "navigation", PaletteCategory.NAVIGATION,
    listOf(
        DEFINITION to { has { it.definitionProvider } },
        TYPE_DEFINITION to { has { it.typeDefinitionProvider } },
        IMPLEMENTATION to { has { it.implementationProvider } },
        REFERENCES to { has { it.referencesProvider } },
        OUTLINE to { hasFile },
        GO_TO_LINE to { hasFile },
    ),
    order = 20,
)

/** Code: Imports, alles korrigieren, Hover, Signatur, Formatieren. */
object CodePaletteProvider : BuiltinActionProvider(
    "code", PaletteCategory.CODE,
    listOf(
        ORGANIZE_IMPORTS_SERVER to { kind("source.organizeImports") },
        ORGANIZE_IMPORTS_LOCAL to { hasFile && ext in PaletteFacts.KOTLIN_EXTENSIONS },
        FIX_ALL to { kind("source.fixAll") },
        HOVER to { has { it.hoverProvider } },
        SIGNATURE_HELP to { has { it.signatureHelpProvider } },
        FORMAT to { hasFile },
    ),
    order = 30,
)

/** Editor: Suchen, Speichern, Falten. */
object EditorPaletteProvider : BuiltinActionProvider(
    "editor", PaletteCategory.EDITOR,
    listOf(
        SEARCH to { hasFile },
        SAVE to { hasFile },
        FOLD_TOGGLE to { hasFile },
        FOLD_ALL to { hasFile },
        UNFOLD_ALL to { hasFile },
    ),
    order = 40,
)

/** Agent/KI: Assistent. */
object AgentAiPaletteProvider : BuiltinActionProvider(
    "agent-ai", PaletteCategory.AGENT_AI,
    listOf(AI_ASSISTANT to { true }),
    order = 90,
)

/** Titel und Tastenkürzel der eingebauten Aktionen. */
object BuiltinTitles {
    /** String-Ressource des Titels. */
    @Suppress("CyclomaticComplexMethod")
    fun titleRes(action: BuiltinPaletteAction): Int = when (action) {
        QUICK_FIX -> R.string.lsp_quick_fix
        REFACTOR -> R.string.palette_refactor
        RENAME -> R.string.lsp_rename
        EXTRACT_STRING -> R.string.hardcoded_strings_extract_action
        ORGANIZE_IMPORTS_SERVER -> R.string.palette_organize_imports_server
        ORGANIZE_IMPORTS_LOCAL -> R.string.editor_organize_imports
        FIX_ALL -> R.string.palette_fix_all
        DEFINITION -> R.string.lsp_definition
        TYPE_DEFINITION -> R.string.lsp_type_definition
        IMPLEMENTATION -> R.string.lsp_implementation
        REFERENCES -> R.string.lsp_references
        OUTLINE -> R.string.lsp_outline
        GO_TO_LINE -> R.string.editor_gehe_zu_zeile
        HOVER -> R.string.lsp_hover
        SIGNATURE_HELP -> R.string.lsp_signature_help
        FORMAT -> R.string.editor_format_document
        SEARCH -> R.string.editor_suchen_ersetzen
        SAVE -> R.string.common_speichern
        LSP_SESSIONS -> R.string.editor_lsp_sessions_title
        AI_ASSISTANT -> R.string.ai_assistant_title
        FOLD_TOGGLE -> R.string.editor_fold_toggle
        FOLD_ALL -> R.string.editor_fold_all
        UNFOLD_ALL -> R.string.editor_unfold_all
    }

    /** Tastenkürzel (Hardware-Tastatur), falls vorhanden. */
    fun shortcut(action: BuiltinPaletteAction): String? = when (action) {
        QUICK_FIX -> "Alt+Enter"
        REFACTOR -> "Strg+Umschalt+R"
        RENAME -> "Umschalt+F6"
        else -> null
    }
}
