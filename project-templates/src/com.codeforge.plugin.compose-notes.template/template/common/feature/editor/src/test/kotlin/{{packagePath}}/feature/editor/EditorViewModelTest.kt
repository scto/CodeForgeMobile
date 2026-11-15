package {{packageName}}.feature.editor

import androidx.lifecycle.SavedStateHandle
import {{packageName}}.core.domain.model.Note
import {{packageName}}.core.domain.model.NoteColor
import {{packageName}}.core.domain.repository.NoteRepository
import {{packageName}}.core.domain.usecase.DeleteNoteUseCase
import {{packageName}}.core.domain.usecase.ObserveNoteUseCase
import {{packageName}}.core.domain.usecase.SaveNoteUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EditorViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    private class FakeRepository(initial: List<Note>) : NoteRepository {
        override val notes = MutableStateFlow(initial)
        override fun note(id: Long): Flow<Note?> = notes.map { list -> list.firstOrNull { it.id == id } }
        override suspend fun upsert(note: Note): Long {
            val id = if (note.id == 0L) 100L else note.id
            notes.update { list -> list.filterNot { it.id == id } + note.copy(id = id) }
            return id
        }
        override suspend fun delete(id: Long) = notes.update { list -> list.filterNot { it.id == id } }
    }

    private fun viewModel(repository: NoteRepository, id: Long) = EditorViewModel(
        SavedStateHandle(mapOf(EditorDestination.ARG_NOTE_ID to id)),
        ObserveNoteUseCase(repository),
        SaveNoteUseCase(repository),
        DeleteNoteUseCase(repository)
    )

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun newNoteIsCreatedOnFirstEditAndKeepsItsId() = runTest(dispatcher) {
        val repository = FakeRepository(emptyList())
        val viewModel = viewModel(repository, EditorDestination.NEW_NOTE)
        viewModel.onEvent(EditorUiEvent.TitleChanged("Neu"))
        viewModel.onEvent(EditorUiEvent.ColorSelected(NoteColor.SKY))
        assertEquals(listOf(Note(100, "Neu", color = NoteColor.SKY)), repository.notes.value)
    }

    @Test
    fun deleteRemovesNoteAndCloses() = runTest(dispatcher) {
        val repository = FakeRepository(listOf(Note(5, "Alt")))
        val viewModel = viewModel(repository, 5)
        assertEquals("Alt", viewModel.uiState.value.note.title)
        viewModel.onEvent(EditorUiEvent.DeleteClicked)
        viewModel.onEvent(EditorUiEvent.DeleteConfirmed)
        assertTrue(repository.notes.value.isEmpty())
        assertTrue(viewModel.uiState.value.closed)
    }
}
