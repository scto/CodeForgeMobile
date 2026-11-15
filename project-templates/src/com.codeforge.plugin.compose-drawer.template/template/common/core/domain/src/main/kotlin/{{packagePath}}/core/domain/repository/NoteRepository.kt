package {{packageName}}.core.domain.repository

import {{packageName}}.core.domain.model.Note
import kotlinx.coroutines.flow.Flow

/** Quelle der Notizen; neueste zuerst. */
interface NoteRepository {
    val notes: Flow<List<Note>>

    suspend fun add(title: String, body: String): Note

    suspend fun delete(id: Long)
}
