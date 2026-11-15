package {{packageName}}.feature.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import {{packageName}}.core.domain.usecase.AddNoteUseCase
import {{packageName}}.core.domain.usecase.DeleteNoteUseCase
import {{packageName}}.core.domain.usecase.ObserveNotesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** MVI: Liste aus dem Repository, Dialog-Eingabe als eigener Zustand. */
@HiltViewModel
class NotesViewModel @Inject constructor(
    observeNotes: ObserveNotesUseCase,
    private val addNote: AddNoteUseCase,
    private val deleteNote: DeleteNoteUseCase
) : ViewModel() {

    private val draft = MutableStateFlow<NoteDraft?>(null)

    val uiState: StateFlow<NotesUiState> = combine(observeNotes(), draft) { notes, currentDraft ->
        NotesUiState(notes = notes, draft = currentDraft)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), NotesUiState())

    fun onEvent(event: NotesUiEvent) {
        when (event) {
            NotesUiEvent.AddClicked -> draft.value = NoteDraft()
            is NotesUiEvent.TitleChanged -> draft.update { it?.copy(title = event.value, showTitleError = false) }
            is NotesUiEvent.BodyChanged -> draft.update { it?.copy(body = event.value) }
            NotesUiEvent.DialogDismissed -> draft.value = null
            NotesUiEvent.SaveClicked -> save()
            is NotesUiEvent.DeleteClicked -> viewModelScope.launch { deleteNote(event.id) }
        }
    }

    private fun save() {
        val current = draft.value ?: return
        viewModelScope.launch {
            addNote(current.title, current.body)
                .onSuccess { draft.value = null }
                .onFailure { draft.update { it?.copy(showTitleError = true) } }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
