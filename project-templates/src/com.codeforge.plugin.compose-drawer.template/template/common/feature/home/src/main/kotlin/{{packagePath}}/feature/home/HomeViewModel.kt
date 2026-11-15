package {{packageName}}.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import {{packageName}}.core.domain.usecase.ObserveNotesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Fasst die Notizen für die Startseite zusammen. */
@HiltViewModel
class HomeViewModel @Inject constructor(observeNotes: ObserveNotesUseCase) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = observeNotes()
        .map { notes ->
            HomeUiState(
                noteCount = notes.size,
                latestTitles = notes.take(LATEST_COUNT).map { it.title },
                isLoading = false
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), HomeUiState())

    private companion object {
        const val LATEST_COUNT = 3
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
