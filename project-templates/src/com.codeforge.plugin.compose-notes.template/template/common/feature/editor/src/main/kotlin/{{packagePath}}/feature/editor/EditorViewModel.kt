package {{packageName}}.feature.editor

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import {{packageName}}.core.domain.model.Note
import {{packageName}}.core.domain.model.NoteColor
import {{packageName}}.core.domain.usecase.DeleteNoteUseCase
import {{packageName}}.core.domain.usecase.ObserveNoteUseCase
import {{packageName}}.core.domain.usecase.SaveNoteUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

/** Zustand des Editors. [closed] wird gesetzt, wenn der Editor sich schließen soll (nach dem Löschen). */
@Immutable
data class EditorUiState(
    val note: Note = Note(),
    val isLoaded: Boolean = false,
    val showDeleteDialog: Boolean = false,
    val closed: Boolean = false
)

/** Aktionen im Editor. */
sealed interface EditorUiEvent {
    data class TitleChanged(val value: String) : EditorUiEvent
    data class ContentChanged(val value: String) : EditorUiEvent
    data class ColorSelected(val color: NoteColor) : EditorUiEvent
    data object PinToggled : EditorUiEvent
    data object DeleteClicked : EditorUiEvent
    data object DeleteConfirmed : EditorUiEvent
    data object DeleteDismissed : EditorUiEvent
}

/** Lädt die Notiz aus der Route und speichert jede Änderung sofort (leere Notizen werden nicht angelegt). */
@HiltViewModel
class EditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val observeNote: ObserveNoteUseCase,
    private val saveNote: SaveNoteUseCase,
    private val deleteNote: DeleteNoteUseCase
) : ViewModel() {

    private val noteId: Long = savedStateHandle[EditorDestination.ARG_NOTE_ID] ?: EditorDestination.NEW_NOTE
    private val saveLock = Mutex()
    private val state = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = state.asStateFlow()

    init {
        viewModelScope.launch {
            val existing = if (noteId == EditorDestination.NEW_NOTE) null else observeNote(noteId).first()
            state.update { it.copy(note = existing ?: Note(), isLoaded = true) }
        }
    }

    fun onEvent(event: EditorUiEvent) {
        when (event) {
            is EditorUiEvent.TitleChanged -> edit { it.copy(title = event.value) }
            is EditorUiEvent.ContentChanged -> edit { it.copy(content = event.value) }
            is EditorUiEvent.ColorSelected -> edit { it.copy(color = event.color) }
            EditorUiEvent.PinToggled -> edit { it.copy(pinned = !it.pinned) }
            EditorUiEvent.DeleteClicked -> state.update { it.copy(showDeleteDialog = true) }
            EditorUiEvent.DeleteDismissed -> state.update { it.copy(showDeleteDialog = false) }
            EditorUiEvent.DeleteConfirmed -> delete()
        }
    }

    private fun edit(change: (Note) -> Note) {
        state.update { it.copy(note = change(it.note)) }
        viewModelScope.launch {
            saveLock.withLock {
                val id = saveNote(state.value.note) ?: 0L
                state.update { it.copy(note = it.note.copy(id = id)) }
            }
        }
    }

    private fun delete() {
        viewModelScope.launch {
            val id = state.value.note.id
            if (id != 0L) deleteNote(id)
            state.update { it.copy(showDeleteDialog = false, closed = true) }
        }
    }
}
