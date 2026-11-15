package {{packageName}}.core.domain

import {{packageName}}.core.domain.model.Note
import {{packageName}}.core.domain.model.SortOrder
import {{packageName}}.core.domain.repository.NoteRepository
import {{packageName}}.core.domain.usecase.ObserveNotesUseCase
import {{packageName}}.core.domain.usecase.SaveNoteUseCase
import {{packageName}}.core.domain.usecase.TogglePinUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteUseCasesTest {
    private class FakeRepository(initial: List<Note> = emptyList()) : NoteRepository {
        override val notes = MutableStateFlow(initial)

        override fun note(id: Long): Flow<Note?> = notes.map { list -> list.firstOrNull { it.id == id } }

        override suspend fun upsert(note: Note): Long {
            val id = if (note.id == 0L) notes.value.size + 1L else note.id
            notes.update { list -> list.filterNot { it.id == id } + note.copy(id = id) }
            return id
        }

        override suspend fun delete(id: Long) = notes.update { list -> list.filterNot { it.id == id } }
    }

    @Test
    fun pinnedNotesAreSeparatedAndSorted() = runTest {
        val repository = FakeRepository(
            listOf(
                Note(1, "Banane", updatedAt = 1),
                Note(2, "Apfel", updatedAt = 3, pinned = true),
                Note(3, "Citrus", updatedAt = 2)
            )
        )
        val sections = ObserveNotesUseCase(repository)("", SortOrder.UPDATED).first()
        assertEquals(listOf("Apfel"), sections.pinned.map { it.title })
        assertEquals(listOf("Citrus", "Banane"), sections.others.map { it.title })
        val byTitle = ObserveNotesUseCase(repository)("", SortOrder.TITLE).first()
        assertEquals(listOf("Banane", "Citrus"), byTitle.others.map { it.title })
    }

    @Test
    fun searchMatchesTitleAndContent() = runTest {
        val repository = FakeRepository(listOf(Note(1, "Einkauf", "Milch"), Note(2, "Ideen", "Podcast")))
        val sections = ObserveNotesUseCase(repository)("milch", SortOrder.TITLE).first()
        assertEquals(listOf("Einkauf"), sections.others.map { it.title })
    }

    @Test
    fun blankNoteIsNotSavedAndExistingOneIsDeleted() = runTest {
        val repository = FakeRepository(listOf(Note(1, "Alt")))
        assertNull(SaveNoteUseCase(repository)(Note()))
        assertNull(SaveNoteUseCase(repository)(Note(id = 1)))
        assertTrue(repository.notes.value.isEmpty())
    }

    @Test
    fun togglePinFlipsFlag() = runTest {
        val repository = FakeRepository(listOf(Note(1, "A")))
        TogglePinUseCase(repository)(1)
        assertTrue(repository.notes.value.single().pinned)
    }
}
