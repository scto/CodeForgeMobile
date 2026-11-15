package {{packageName}}.core.domain.usecase

import {{packageName}}.core.domain.model.Note
import {{packageName}}.core.domain.repository.NoteRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/** Liefert alle Notizen, neueste zuerst. */
class ObserveNotesUseCase @Inject constructor(private val repository: NoteRepository) {
    operator fun invoke(): Flow<List<Note>> = repository.notes
}

/** Legt eine Notiz an; ein leerer Titel wird abgelehnt. */
class AddNoteUseCase @Inject constructor(private val repository: NoteRepository) {
    suspend operator fun invoke(title: String, body: String): Result<Note> {
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty()) return Result.failure(IllegalArgumentException("Titel fehlt"))
        return Result.success(repository.add(cleanTitle, body.trim()))
    }
}

/** Löscht eine Notiz. */
class DeleteNoteUseCase @Inject constructor(private val repository: NoteRepository) {
    suspend operator fun invoke(id: Long) = repository.delete(id)
}
