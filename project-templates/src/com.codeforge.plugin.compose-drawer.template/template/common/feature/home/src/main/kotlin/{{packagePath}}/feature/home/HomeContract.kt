package {{packageName}}.feature.home

import androidx.compose.runtime.Immutable

/** Zustand der Startseite. */
@Immutable
data class HomeUiState(val noteCount: Int = 0, val latestTitles: List<String> = emptyList(), val isLoading: Boolean = true)
