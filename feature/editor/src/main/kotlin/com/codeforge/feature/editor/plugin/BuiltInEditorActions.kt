// Modul: :feature:editor
package com.codeforge.feature.editor.plugin

import com.codeforge.core.domain.extension.registry.ExtensionRegistry
import com.codeforge.core.resources.R
import com.codeforge.core.resources.Res
import com.codeforge.libs.plugin_api.EditorAction
import com.codeforge.libs.plugin_api.EditorContext
import com.codeforge.libs.plugin_api.EditorEffect
import com.codeforge.libs.plugin_api.PluginExtensionPoints
import com.codeforge.libs.plugin_api.TextEdit
import com.codeforge.libs.plugin_api.lineEnd
import com.codeforge.libs.plugin_api.lineStart
import java.util.Collections
import java.util.WeakHashMap

/**
 * Eingebaute Zeilen-Aktionen. Sie laufen über dieselbe Andockstelle wie Plugin-Aktionen ([PluginExtensionPoints.EDITOR_ACTION]),
 * damit der Weg für Plugins nicht hinter dem der App zurückbleibt.
 */
object BuiltInEditorActions {

    private val registered: MutableSet<ExtensionRegistry> = Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap()))

    /** Meldet die eingebauten Aktionen einmal je Register an. */
    fun ensureRegistered(registry: ExtensionRegistry) {
        if (!registered.add(registry)) return
        listOf(SortLines, RemoveDuplicateLines, ToggleCase).forEach { registry.register(PluginExtensionPoints.EDITOR_ACTION, it) }
    }

    /** Ausgewählte Zeilen (oder alle) sortieren. */
    internal object SortLines : LineAction("builtin.sortLines", R.string.editor_action_sort_lines) {
        override fun transform(lines: List<String>) = lines.sortedWith(String.CASE_INSENSITIVE_ORDER)
    }

    /** Doppelte Zeilen in Auswahl (oder Datei) entfernen, erste behalten. */
    internal object RemoveDuplicateLines : LineAction("builtin.removeDuplicateLines", R.string.editor_action_remove_duplicates) {
        override fun transform(lines: List<String>) = lines.distinct()
    }

    /** Auswahl in Großbuchstaben bzw., wenn schon groß, in Kleinbuchstaben. */
    internal object ToggleCase : EditorAction {
        override val id = "builtin.toggleCase"
        override val title: String get() = Res.string(R.string.editor_action_toggle_case)
        override val kind = "refactor.rewrite"
        override fun isAvailable(context: EditorContext) = context.hasSelection
        override suspend fun perform(context: EditorContext): List<EditorEffect> {
            val sel = context.selectedText
            val replaced = if (sel == sel.uppercase()) sel.lowercase() else sel.uppercase()
            return listOf(
                EditorEffect.ApplyEdits(listOf(TextEdit(context.selectionStart, context.selectionEnd, replaced))),
                EditorEffect.Select(context.selectionStart, context.selectionStart + replaced.length)
            )
        }
    }

    /** Gemeinsame Basis: wirkt auf die vollständigen Zeilen der Auswahl, ohne Auswahl auf die ganze Datei. */
    internal abstract class LineAction(override val id: String, private val titleRes: Int) : EditorAction {
        override val title: String get() = Res.string(titleRes)
        override val kind = "source"

        /** Neue Zeilenfolge. */
        abstract fun transform(lines: List<String>): List<String>

        override suspend fun perform(context: EditorContext): List<EditorEffect> {
            val start = if (context.hasSelection) context.lineStart(context.selectionStart) else 0
            val endAnchor = if (context.hasSelection) (context.selectionEnd - 1).coerceAtLeast(context.selectionStart) else context.text.length
            val end = if (context.hasSelection) context.lineEnd(endAnchor) else context.text.length
            val block = context.text.substring(start, end)
            val trailingNewline = block.endsWith("\n")
            val lines = block.removeSuffix("\n").split('\n')
            val result = transform(lines).joinToString("\n") + if (trailingNewline) "\n" else ""
            if (result == block) return emptyList()
            return listOf(
                EditorEffect.ApplyEdits(listOf(TextEdit(start, end, result))),
                EditorEffect.Select(start, start + result.length)
            )
        }
    }
}
