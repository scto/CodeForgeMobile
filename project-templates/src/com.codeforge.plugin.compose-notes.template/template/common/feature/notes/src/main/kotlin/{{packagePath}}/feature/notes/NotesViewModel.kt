package {{packageName}}.feature.notes

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import {{packageName}}.core.domain.model.Note
import {{packageName}}.core.domain.model.NoteLayout
import {{packageName}}.core.domain.usecase.ObserveNotesUseCase
import {{packageName}}.core.domain.usecase.ObserveSettingsUseCase
import {{packageName}}.core.domain.usecase.TogglePinUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Zustand der Notizübersicht. */
@Immutable
data class NotesUiState(
    val query: String = "",
    val pinned: List<Note> = emptyList(),
    val others: List<Note> = emptyList(),
    val layout: NoteLayout = NoteLayout.GRID,
    val isLoading: Boolean = true
) {
    val isEmpty: Boolean get() = pinned.isEmpty() && others.isEmpty()
}

/** Aktionen der Übersicht. */
sealed interface NotesUiEvent {
    data class QueryChanged(val query: String) : NotesUiEvent
    data class PinToggled(val id: Long) : NotesUiEvent
}

/** MVI: Suche als eigener Zustand, Notizen und Darstellung aus dem Repository. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class NotesViewModel @Inject constructor(
    observeNotes: ObserveNotesUseCase,
    observeSettings: ObserveSettingsUseCase,
    private val togglePin: TogglePinUseCase
) : ViewModel() {

    private val query = MutableStateFlow("")

    val uiState: StateFlow<NotesUiState> = combine(query, observeSettings()) { text, settings -> text to settings }
        .flatMapLatest { (text, settings) ->
            observeNotes(text, settings.sortOrder).map { sections ->
                NotesUiState(text, sections.pinned, sections.others, settings.layout, isLoading = false)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), NotesUiState())

    fun onEvent(event: NotesUiEvent) {
        when (event) {
            is NotesUiEvent.QueryChanged -> query.value = event.query
            is NotesUiEvent.PinToggled -> viewModelScope.launch { togglePin(event.id) }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
