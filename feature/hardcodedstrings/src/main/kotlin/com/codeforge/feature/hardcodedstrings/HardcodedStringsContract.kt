package com.codeforge.feature.hardcodedstrings

import androidx.compose.runtime.Immutable
import com.codeforge.libs.hardcodedstrings.model.ExtractionPlan
import com.codeforge.libs.hardcodedstrings.model.HardcodedStringFinding
import com.codeforge.libs.hardcodedstrings.model.StringConfidence

/** Zustand der Suche nach fest eingebauten Texten. */
@Immutable
data class HardcodedStringsUiState(
    val targetPath: String = "",
    val projectRoot: String = "",
    val isScanning: Boolean = false,
    val isApplying: Boolean = false,
    val findings: List<HardcodedStringFinding> = emptyList(),
    val confidenceFilter: StringConfidence? = null,
    val searchQuery: String = "",
    val selectedIds: Set<String> = emptySet(),
    val customKeys: Map<String, String> = emptyMap(),
    val pendingPlan: ExtractionPlan? = null,
    val isPreviewDialogOpen: Boolean = false,
    val isUndoAvailable: Boolean = false,
    val statusMessage: String? = null
) {
    /** Funde nach Filter und Suche. */
    val filteredFindings: List<HardcodedStringFinding>
        get() = findings.filter { finding ->
            val matchesConfidence = confidenceFilter == null || finding.confidence == confidenceFilter
            val matchesQuery = searchQuery.isBlank() ||
                    finding.rawValue.contains(searchQuery, ignoreCase = true) ||
                    finding.file.name.contains(searchQuery, ignoreCase = true) ||
                    (customKeys[finding.id] ?: finding.suggestedKey).contains(searchQuery, ignoreCase = true)
            matchesConfidence && matchesQuery
        }

    /** Anzahl gewählter Funde. */
    val selectedCount: Int
        get() = selectedIds.size
}

/** Aktionen im Prüfer. */
sealed interface HardcodedStringsUiEvent {
    /** Prüfung starten. */
    data class ScanRequested(val targetPath: String, val projectRoot: String) : HardcodedStringsUiEvent
    /** Sicherheitsfilter geändert. */
    data class ConfidenceFilterChanged(val confidence: StringConfidence?) : HardcodedStringsUiEvent
    /** Suchtext geändert. */
    data class SearchQueryChanged(val query: String) : HardcodedStringsUiEvent
    /** Fund an-/abwählen. */
    data class FindingToggled(val id: String) : HardcodedStringsUiEvent
    /** Alle an-/abwählen. */
    data class SelectAllToggled(val selectAll: Boolean) : HardcodedStringsUiEvent
    /** Ressourcenschlüssel geändert. */
    data class KeyChanged(val id: String, val newKey: String) : HardcodedStringsUiEvent
    /** Fund ignorieren. */
    data class IgnoreFinding(val finding: HardcodedStringFinding) : HardcodedStringsUiEvent
    /** Zur Fundstelle springen. */
    data class JumpToSource(val finding: HardcodedStringFinding) : HardcodedStringsUiEvent
    /** Vorschau anfordern. */
    data object RequestPreview : HardcodedStringsUiEvent
    /** Vorschau schließen. */
    data object DismissPreview : HardcodedStringsUiEvent
    /** Änderungen anwenden. */
    data object ConfirmApply : HardcodedStringsUiEvent
    /** Rückgängig machen. */
    data object RequestUndo : HardcodedStringsUiEvent
    /** Statusmeldung schließen. */
    data object DismissStatusMessage : HardcodedStringsUiEvent
}

/** Einmalige Effekte. */
sealed interface HardcodedStringsUiEffect {
    /** Meldung anzeigen. */
    data class ShowSnackbar(val message: String) : HardcodedStringsUiEffect
    /** Datei an Stelle im Editor öffnen. */
    data class OpenEditorAt(val filePath: String, val line: Int, val column: Int) : HardcodedStringsUiEffect
}
