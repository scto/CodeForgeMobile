// Modul: :feature:editor
package com.codeforge.feature.editor.palette

import com.codeforge.core.resources.R

/** String-Ressource des Titels einer eingebauten Aktion. */
fun BuiltinPaletteAction.titleRes(): Int = when (this) {
    BuiltinPaletteAction.QUICK_FIX -> R.string.lsp_quick_fix
    BuiltinPaletteAction.REFACTOR -> R.string.palette_refactor
    BuiltinPaletteAction.RENAME -> R.string.lsp_rename
    BuiltinPaletteAction.EXTRACT_STRING -> R.string.hardcoded_strings_extract_action
    BuiltinPaletteAction.ORGANIZE_IMPORTS_SERVER -> R.string.palette_organize_imports_server
    BuiltinPaletteAction.ORGANIZE_IMPORTS_LOCAL -> R.string.editor_organize_imports
    BuiltinPaletteAction.FIX_ALL -> R.string.palette_fix_all
    BuiltinPaletteAction.DEFINITION -> R.string.lsp_definition
    BuiltinPaletteAction.TYPE_DEFINITION -> R.string.lsp_type_definition
    BuiltinPaletteAction.IMPLEMENTATION -> R.string.lsp_implementation
    BuiltinPaletteAction.REFERENCES -> R.string.lsp_references
    BuiltinPaletteAction.OUTLINE -> R.string.lsp_outline
    BuiltinPaletteAction.GO_TO_LINE -> R.string.editor_gehe_zu_zeile
    BuiltinPaletteAction.HOVER -> R.string.lsp_hover
    BuiltinPaletteAction.SIGNATURE_HELP -> R.string.lsp_signature_help
    BuiltinPaletteAction.FORMAT -> R.string.editor_format_document
    BuiltinPaletteAction.SEARCH -> R.string.editor_suchen_ersetzen
    BuiltinPaletteAction.SAVE -> R.string.common_speichern
    BuiltinPaletteAction.LSP_SESSIONS -> R.string.editor_lsp_sessions_title
    BuiltinPaletteAction.AI_ASSISTANT -> R.string.ai_assistant_title
}

/** Tastenkürzel (Hardware-Tastatur) einer eingebauten Aktion, falls vorhanden. */
fun BuiltinPaletteAction.shortcut(): String? = when (this) {
    BuiltinPaletteAction.QUICK_FIX -> "Alt+Enter"
    BuiltinPaletteAction.REFACTOR -> "Strg+Umschalt+R"
    BuiltinPaletteAction.RENAME -> "Umschalt+F6"
    else -> null
}

/** String-Ressource der Überschrift einer Kategorie. */
fun PaletteCategory.titleRes(): Int = when (this) {
    PaletteCategory.REFACTOR -> R.string.palette_cat_refactor
    PaletteCategory.NAVIGATION -> R.string.palette_cat_navigation
    PaletteCategory.CODE -> R.string.palette_cat_code
    PaletteCategory.EDITOR -> R.string.palette_cat_editor
    PaletteCategory.SERVER -> R.string.palette_cat_server
    PaletteCategory.PLUGIN -> R.string.palette_cat_plugin
}

/** Alle Einträge für [context]; [title] übersetzt eine String-Ressource. */
fun buildPaletteItems(context: PaletteContext, title: (Int) -> String): List<PaletteItem> =
    CommandPaletteCatalog.builtinActions(context).map { action ->
        PaletteItem(
            action = PaletteAction.Builtin(action),
            title = title(action.titleRes()),
            category = CommandPaletteCatalog.categoryOf(action),
            shortcut = action.shortcut()
        )
    } + CommandPaletteCatalog.serverItems(context) + CommandPaletteCatalog.pluginItems(context)

/** Rang einer Code-Action-Art für die Sortierung im Refactoring-Sheet. */
fun codeActionKindRank(kind: String?): Int {
    val k = kind.orEmpty()
    return KIND_ORDER.indexOfFirst { k == it || k.startsWith("$it.") }.let { if (it < 0) KIND_ORDER.size else it }
}

/** String-Ressource der Beschriftung einer Code-Action-Art, `null` für unbekannte Arten. */
fun codeActionKindLabelRes(kind: String?): Int? {
    val k = kind ?: return null
    return when {
        k.startsWith("refactor.extract") -> R.string.refactor_kind_extract
        k.startsWith("refactor.inline") -> R.string.refactor_kind_inline
        k.startsWith("refactor.rewrite") -> R.string.refactor_kind_rewrite
        k.startsWith("refactor.move") -> R.string.refactor_kind_move
        k.startsWith("refactor") -> R.string.refactor_kind_other
        k.startsWith("source.organizeImports") -> R.string.refactor_kind_imports
        k.startsWith("source") -> R.string.refactor_kind_source
        k.startsWith("quickfix") -> R.string.refactor_kind_quickfix
        else -> null
    }
}

private val KIND_ORDER = listOf(
    "refactor.extract", "refactor.inline", "refactor.rewrite", "refactor.move", "refactor", "source.organizeImports", "source"
)
