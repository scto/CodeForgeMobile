package {{packageName}}.core.domain.usecase

import {{packageName}}.core.domain.model.Note
import {{packageName}}.core.domain.model.NoteSections
import {{packageName}}.core.domain.model.SortOrder
import {{packageName}}.core.domain.repository.NoteRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** Notizen passend zu [query], sortiert, angeheftete getrennt. */
class ObserveNotesUseCase @Inject constructor(private val repository: NoteRepository) {
    operator fun invoke(query: String, order: SortOrder): Flow<NoteSections> = repository.notes.map { notes ->
        val needle = query.trim()
        val matching = notes.filter { needle.isEmpty() || it.title.contains(needle, true) || it.content.contains(needle, true) }
        val sorted = when (order) {
            SortOrder.UPDATED -> matching.sortedByDescending { it.updatedAt }
            SortOrder.TITLE -> matching.sortedBy { it.title.lowercase() }
        }
        NoteSections(pinned = sorted.filter { it.pinned }, others = sorted.filterNot { it.pinned })
    }
}

/** Eine einzelne Notiz. */
class ObserveNoteUseCase @Inject constructor(private val repository: NoteRepository) {
    operator fun invoke(id: Long): Flow<Note?> = repository.note(id)
}

/**
 * Speichert eine Notiz. Eine leere Notiz wird nicht angelegt bzw. – falls schon gespeichert – gelöscht.
 * Liefert die ID oder `null`, wenn nichts (mehr) gespeichert ist.
 */
class SaveNoteUseCase @Inject constructor(private val repository: NoteRepository) {
    suspend operator fun invoke(note: Note): Long? {
        if (note.isBlank) {
            if (note.id != 0L) repository.delete(note.id)
            return null
        }
        return repository.upsert(note.copy(title = note.title.trimEnd()))
    }
}

/** Löscht eine Notiz. */
class DeleteNoteUseCase @Inject constructor(private val repository: NoteRepository) {
    suspend operator fun invoke(id: Long) = repository.delete(id)
}

/** Heftet eine Notiz an oder löst sie. */
class TogglePinUseCase @Inject constructor(private val repository: NoteRepository) {
    suspend operator fun invoke(id: Long) {
        val note = repository.note(id).first() ?: return
        repository.upsert(note.copy(pinned = !note.pinned))
    }
}
