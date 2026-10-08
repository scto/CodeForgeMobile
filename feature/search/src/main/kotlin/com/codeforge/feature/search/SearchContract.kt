/**
 * Modul: :feature:search
 * @author Thomas Schmid
 */
package com.codeforge.feature.search

import androidx.compose.runtime.Immutable
import com.codeforge.libs.code_tools.search.ProjectSearchResult
import com.codeforge.libs.code_tools.search.TextMatch

@Immutable
data class SearchUiState(
    val rootPath: String = "",
    val query: String = "",
    val replacement: String = "",
    val caseSensitive: Boolean = false,
    val regex: Boolean = false,
    val wholeWord: Boolean = false,
    val showReplace: Boolean = false,
    val showFilters: Boolean = false,
    /** Kommagetrennte Glob-Muster, z. B. `*.kt, *.kts`. */
    val includeGlobs: String = "",
    val excludeGlobs: String = "",
    val isSearching: Boolean = false,
    val isReplacing: Boolean = false,
    val result: ProjectSearchResult? = null,
    val error: String? = null,
    /** Relative Pfade eingeklappter Dateigruppen. */
    val collapsed: Set<String> = emptySet(),
    val confirmReplaceAll: Boolean = false,
) {
    val canReplace: Boolean get() = query.isNotEmpty() && result?.totalMatches?.let { it > 0 } == true && !isReplacing
}

sealed interface SearchUiEvent {
    data class Initialize(val rootPath: String) : SearchUiEvent
    data class QueryChanged(val value: String) : SearchUiEvent
    data class ReplacementChanged(val value: String) : SearchUiEvent
    data class IncludeChanged(val value: String) : SearchUiEvent
    data class ExcludeChanged(val value: String) : SearchUiEvent
    data object ToggleCase : SearchUiEvent
    data object ToggleRegex : SearchUiEvent
    data object ToggleWholeWord : SearchUiEvent
    data object ToggleReplace : SearchUiEvent
    data object ToggleFilters : SearchUiEvent
    data class ToggleFile(val relativePath: String) : SearchUiEvent
    data class OpenMatch(val path: String, val match: TextMatch) : SearchUiEvent
    data class ReplaceOne(val path: String, val match: TextMatch) : SearchUiEvent
    data class ReplaceInFile(val path: String) : SearchUiEvent
    data object RequestReplaceAll : SearchUiEvent
    data object DismissReplaceAll : SearchUiEvent
    data object ConfirmReplaceAll : SearchUiEvent
    data object Rerun : SearchUiEvent
}

sealed interface SearchUiEffect {
    data class ShowSnackbar(val message: String) : SearchUiEffect
    /** Treffer wurde im Editor geöffnet (Drawer kann sich schließen). */
    data object MatchOpened : SearchUiEffect
}
