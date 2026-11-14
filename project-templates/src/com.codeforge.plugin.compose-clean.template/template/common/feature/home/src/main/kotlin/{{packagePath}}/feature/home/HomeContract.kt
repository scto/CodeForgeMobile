package {{packageName}}.feature.home

import androidx.compose.runtime.Immutable

/** Zustand des Startbildschirms. */
@Immutable
data class HomeUiState(
    val greeting: String = "",
    val count: Int = 0,
    val isLoading: Boolean = true
)

/** Aktionen des Nutzers. */
sealed interface HomeUiEvent {
    /** Zähler erhöhen. */
    data object IncrementClicked : HomeUiEvent
}
