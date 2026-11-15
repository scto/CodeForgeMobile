package {{packageName}}.feature.notes

import {{packageName}}.core.domain.model.Note
import {{packageName}}.core.domain.repository.NoteRepository
import {{packageName}}.core.domain.usecase.AddNoteUseCase
import {{packageName}}.core.domain.usecase.DeleteNoteUseCase
import {{packageName}}.core.domain.usecase.ObserveNotesUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
class NotesViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    private class FakeRepository : NoteRepository {
        override val notes = MutableStateFlow(listOf(Note(1, "Alt", "", 0L)))
        override suspend fun add(title: String, body: String): Note =
            Note(notes.value.size + 1L, title, body, 0L).also { note -> notes.update { listOf(note) + it } }
        override suspend fun delete(id: Long) = notes.update { list -> list.filterNot { it.id == id } }
    }

    private fun viewModel(repository: NoteRepository) =
        NotesViewModel(ObserveNotesUseCase(repository), AddNoteUseCase(repository), DeleteNoteUseCase(repository))

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun savingAddsNoteAndClosesDialog() = runTest(dispatcher) {
        val viewModel = viewModel(FakeRepository())
        val collector = launch { viewModel.uiState.collect {} }
        viewModel.onEvent(NotesUiEvent.AddClicked)
        viewModel.onEvent(NotesUiEvent.TitleChanged("Neu"))
        viewModel.onEvent(NotesUiEvent.SaveClicked)
        assertEquals(listOf("Neu", "Alt"), viewModel.uiState.value.notes.map { it.title })
        assertEquals(null, viewModel.uiState.value.draft)
        collector.cancel()
    }

    @Test
    fun blankTitleKeepsDialogWithError() = runTest(dispatcher) {
        val viewModel = viewModel(FakeRepository())
        val collector = launch { viewModel.uiState.collect {} }
        viewModel.onEvent(NotesUiEvent.AddClicked)
        viewModel.onEvent(NotesUiEvent.SaveClicked)
        assertTrue(viewModel.uiState.value.draft?.showTitleError == true)
        assertEquals(1, viewModel.uiState.value.notes.size)
        collector.cancel()
    }
}
