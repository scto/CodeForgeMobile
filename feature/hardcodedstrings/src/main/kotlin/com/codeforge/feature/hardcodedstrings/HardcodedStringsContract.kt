package com.codeforge.feature.hardcodedstrings

import androidx.compose.runtime.Immutable
import com.codeforge.libs.hardcodedstrings.model.ExtractionPlan
import com.codeforge.libs.hardcodedstrings.model.HardcodedStringFinding
import com.codeforge.libs.hardcodedstrings.model.StringConfidence

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
    val filteredFindings: List<HardcodedStringFinding>
        get() = findings.filter { finding ->
            val matchesConfidence = confidenceFilter == null || finding.confidence == confidenceFilter
            val matchesQuery = searchQuery.isBlank() ||
                    finding.rawValue.contains(searchQuery, ignoreCase = true) ||
                    finding.file.name.contains(searchQuery, ignoreCase = true) ||
                    (customKeys[finding.id] ?: finding.suggestedKey).contains(searchQuery, ignoreCase = true)
            matchesConfidence && matchesQuery
        }

    val selectedCount: Int
        get() = selectedIds.size
}

sealed interface HardcodedStringsUiEvent {
    data class ScanRequested(val targetPath: String, val projectRoot: String) : HardcodedStringsUiEvent
    data class ConfidenceFilterChanged(val confidence: StringConfidence?) : HardcodedStringsUiEvent
    data class SearchQueryChanged(val query: String) : HardcodedStringsUiEvent
    data class FindingToggled(val id: String) : HardcodedStringsUiEvent
    data class SelectAllToggled(val selectAll: Boolean) : HardcodedStringsUiEvent
    data class KeyChanged(val id: String, val newKey: String) : HardcodedStringsUiEvent
    data class IgnoreFinding(val finding: HardcodedStringFinding) : HardcodedStringsUiEvent
    data class JumpToSource(val finding: HardcodedStringFinding) : HardcodedStringsUiEvent
    data object RequestPreview : HardcodedStringsUiEvent
    data object DismissPreview : HardcodedStringsUiEvent
    data object ConfirmApply : HardcodedStringsUiEvent
    data object RequestUndo : HardcodedStringsUiEvent
    data object DismissStatusMessage : HardcodedStringsUiEvent
}

sealed interface HardcodedStringsUiEffect {
    data class ShowSnackbar(val message: String) : HardcodedStringsUiEffect
    data class OpenEditorAt(val filePath: String, val line: Int, val column: Int) : HardcodedStringsUiEffect
}
