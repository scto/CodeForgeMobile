/**
 * Modul: :feature:search
 * @author Thomas Schmid
 */
package com.codeforge.feature.search

import androidx.compose.runtime.Immutable
import com.codeforge.libs.code_tools.search.ProjectSearchResult
import com.codeforge.libs.code_tools.search.TextMatch

/** Zustand der Projektsuche. */
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
    /** Ob „Ersetzen“ möglich ist (Treffer vorhanden, nichts läuft). */
    val canReplace: Boolean get() = query.isNotEmpty() && result?.totalMatches?.let { it > 0 } == true && !isReplacing
}

/** Aktionen der Projektsuche. */
sealed interface SearchUiEvent {
    /** Wurzelordner setzen. */
    data class Initialize(val rootPath: String) : SearchUiEvent
    /** Suchtext geändert. */
    data class QueryChanged(val value: String) : SearchUiEvent
    /** Ersetzungstext geändert. */
    data class ReplacementChanged(val value: String) : SearchUiEvent
    /** Einschlussmuster geändert. */
    data class IncludeChanged(val value: String) : SearchUiEvent
    /** Ausschlussmuster geändert. */
    data class ExcludeChanged(val value: String) : SearchUiEvent
    /** Groß-/Kleinschreibung beachten. */
    data object ToggleCase : SearchUiEvent
    /** Regulären Ausdruck verwenden. */
    data object ToggleRegex : SearchUiEvent
    /** Nur ganze Wörter. */
    data object ToggleWholeWord : SearchUiEvent
    /** Ersetzen-Leiste ein-/ausblenden. */
    data object ToggleReplace : SearchUiEvent
    /** Filter ein-/ausblenden. */
    data object ToggleFilters : SearchUiEvent
    /** Datei auf-/zuklappen. */
    data class ToggleFile(val relativePath: String) : SearchUiEvent
    /** Treffer im Editor öffnen. */
    data class OpenMatch(val path: String, val match: TextMatch) : SearchUiEvent
    /** Einen Treffer ersetzen. */
    data class ReplaceOne(val path: String, val match: TextMatch) : SearchUiEvent
    /** Alle Treffer einer Datei ersetzen. */
    data class ReplaceInFile(val path: String) : SearchUiEvent
    /** „Alle ersetzen“ anfragen. */
    data object RequestReplaceAll : SearchUiEvent
    /** „Alle ersetzen“ abbrechen. */
    data object DismissReplaceAll : SearchUiEvent
    /** „Alle ersetzen“ bestätigen. */
    data object ConfirmReplaceAll : SearchUiEvent
    /** Suche wiederholen. */
    data object Rerun : SearchUiEvent
}

/** Einmalige Effekte. */
sealed interface SearchUiEffect {
    /** Meldung anzeigen. */
    data class ShowSnackbar(val message: String) : SearchUiEffect
    /** Treffer wurde im Editor geöffnet (Drawer kann sich schließen). */
    data object MatchOpened : SearchUiEffect
}
