// Modul: :feature:editor
package com.codeforge.feature.editor.palette

import com.codeforge.core.resources.R

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
