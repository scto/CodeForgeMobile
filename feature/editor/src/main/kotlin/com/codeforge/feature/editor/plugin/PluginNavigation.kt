// Modul: :feature:editor
package com.codeforge.feature.editor.plugin

import com.codeforge.core.domain.model.LspLocation
import com.codeforge.core.domain.model.LspPosition
import com.codeforge.core.domain.model.LspRange
import com.codeforge.core.domain.model.LspTextEdit
import com.codeforge.core.domain.model.LspWorkspaceEdit
import com.codeforge.libs.plugin_api.EditorContext
import com.codeforge.libs.plugin_api.PluginLanguagePoints
import com.codeforge.libs.plugin_api.PluginLocation

// Navigation und Umbenennen über Plugin-Quellen (für Sprachen ohne Sprachserver); Ergebnisse in LSP-Typen,
// damit Verwendungen-Liste und Umbenennen-Vorschau unverändert weiterarbeiten.

private const val NAV_TIMEOUT_MS = 10_000L
private const val MAX_LOCATIONS = 500
private const val MAX_RENAME_EDITS = 5_000
private const val FILE_URI = "file://"

/** Definitionen aller Quellen (je Quelle höchstens 10 s); leer, wenn keine etwas findet. */
suspend fun PluginEditorBridge.definitions(context: EditorContext): List<LspLocation> =
    registry.contributions(PluginLanguagePoints.DEFINITION_PROVIDER).flatMap { provider ->
        guarded(provider.pluginId, "definition", NAV_TIMEOUT_MS) { provider.value.definition(context) }.getOrDefault(emptyList())
    }.toLspLocations()

/** Verwendungen aller Quellen (je Quelle höchstens 10 s, zusammen höchstens [MAX_LOCATIONS]). */
suspend fun PluginEditorBridge.references(context: EditorContext): List<LspLocation> =
    registry.contributions(PluginLanguagePoints.REFERENCE_PROVIDER).flatMap { provider ->
        guarded(provider.pluginId, "references", NAV_TIMEOUT_MS) { provider.value.references(context) }.getOrDefault(emptyList())
    }.toLspLocations()

/** Umbenennen über die erste zuständige Quelle; `null`, wenn keine Änderungen liefert. */
suspend fun PluginEditorBridge.rename(context: EditorContext, newName: String): LspWorkspaceEdit? {
    val edits = registry.contributions(PluginLanguagePoints.RENAME_PROVIDER).firstNotNullOfOrNull { provider ->
        guarded(provider.pluginId, "rename") { provider.value.rename(context, newName) }.getOrNull()?.takeIf { it.isNotEmpty() }
    } ?: return null
    val valid = edits.filter { it.range.isValid() }.take(MAX_RENAME_EDITS)
    if (valid.isEmpty()) return null
    return LspWorkspaceEdit(
        changes = valid.groupBy({ FILE_URI + it.range.filePath }) { LspTextEdit(it.range.toLspRange(), it.newText) }
    )
}

private fun List<PluginLocation>.toLspLocations(): List<LspLocation> =
    asSequence().filter { it.isValid() }.distinct().take(MAX_LOCATIONS).map { LspLocation(FILE_URI + it.filePath, it.toLspRange()) }.toList()

private fun PluginLocation.isValid(): Boolean =
    filePath.startsWith("/") && line >= 0 && column >= 0 && (endLine > line || (endLine == line && endColumn >= column))

private fun PluginLocation.toLspRange() = LspRange(LspPosition(line, column), LspPosition(endLine, endColumn))
