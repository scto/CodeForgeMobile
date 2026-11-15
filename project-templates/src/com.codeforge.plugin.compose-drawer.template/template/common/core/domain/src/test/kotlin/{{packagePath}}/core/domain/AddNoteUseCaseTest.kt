package {{packageName}}.core.domain

import {{packageName}}.core.domain.model.Note
import {{packageName}}.core.domain.repository.NoteRepository
import {{packageName}}.core.domain.usecase.AddNoteUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AddNoteUseCaseTest {
    private class FakeRepository : NoteRepository {
        override val notes = MutableStateFlow(emptyList<Note>())
        override suspend fun add(title: String, body: String): Note =
            Note(notes.value.size + 1L, title, body, 0L).also { note -> notes.update { listOf(note) + it } }
        override suspend fun delete(id: Long) = notes.update { list -> list.filterNot { it.id == id } }
    }

    @Test
    fun blankTitleIsRejected() = runTest {
        val repository = FakeRepository()
        assertTrue(AddNoteUseCase(repository)("  ", "Text").isFailure)
        assertTrue(repository.notes.value.isEmpty())
    }

    @Test
    fun titleAndBodyAreTrimmed() = runTest {
        val repository = FakeRepository()
        val note = AddNoteUseCase(repository)("  Einkauf ", " Milch ").getOrThrow()
        assertEquals("Einkauf", note.title)
        assertEquals("Milch", note.body)
    }
}
