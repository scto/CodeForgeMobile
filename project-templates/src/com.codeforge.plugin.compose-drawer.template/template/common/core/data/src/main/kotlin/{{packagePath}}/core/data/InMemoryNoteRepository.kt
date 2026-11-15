package {{packageName}}.core.data

import {{packageName}}.core.domain.model.Note
import {{packageName}}.core.domain.repository.NoteRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/** Hält die Notizen im Speicher; durch Room ersetzbar, ohne Domain und Oberfläche zu ändern. */
@Singleton
class InMemoryNoteRepository @Inject constructor() : NoteRepository {
    private val nextId = AtomicLong(SAMPLES.size.toLong())
    private val state = MutableStateFlow(SAMPLES)
    override val notes: StateFlow<List<Note>> = state.asStateFlow()

    override suspend fun add(title: String, body: String): Note {
        val note = Note(nextId.incrementAndGet(), title, body, System.currentTimeMillis())
        state.update { listOf(note) + it }
        return note
    }

    override suspend fun delete(id: Long) = state.update { notes -> notes.filterNot { it.id == id } }

    private companion object {
        val SAMPLES = listOf(
            Note(3, "Willkommen", "Wische das Menü von links auf oder tippe oben links darauf.", 0L),
            Note(2, "Einkaufen", "Milch, Brot, Kaffee", 0L),
            Note(1, "Idee", "Notizen später in Room speichern.", 0L)
        )
    }
}
