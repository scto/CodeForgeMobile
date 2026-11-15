package {{packageName}}.feature.notes

import androidx.compose.runtime.Immutable
import {{packageName}}.core.domain.model.Note

/** Eingabe im Dialog „Neue Notiz“. */
@Immutable
data class NoteDraft(val title: String = "", val body: String = "", val showTitleError: Boolean = false)

/** Zustand der Notizliste; [draft] ist `null`, solange der Dialog zu ist. */
@Immutable
data class NotesUiState(
    val notes: List<Note> = emptyList(),
    val draft: NoteDraft? = null
)

/** Aktionen des Nutzers. */
sealed interface NotesUiEvent {
    data object AddClicked : NotesUiEvent
    data class TitleChanged(val value: String) : NotesUiEvent
    data class BodyChanged(val value: String) : NotesUiEvent
    data object SaveClicked : NotesUiEvent
    data object DialogDismissed : NotesUiEvent
    data class DeleteClicked(val id: Long) : NotesUiEvent
}
