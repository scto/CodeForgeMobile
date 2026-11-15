package {{packageName}}.feature.home

import {{packageName}}.core.domain.model.Note
import {{packageName}}.core.domain.repository.NoteRepository
import {{packageName}}.core.domain.usecase.ObserveNotesUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
class HomeViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    private class FakeRepository(notes: List<Note>) : NoteRepository {
        override val notes = MutableStateFlow(notes)
        override suspend fun add(title: String, body: String): Note = error("nicht gebraucht")
        override suspend fun delete(id: Long) = Unit
    }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun showsCountAndLatestThreeTitles() = runTest(dispatcher) {
        val notes = (1L..5L).map { Note(it, "Notiz $it", "", 0L) }
        val viewModel = HomeViewModel(ObserveNotesUseCase(FakeRepository(notes)))
        val collector = launch { viewModel.uiState.collect {} }
        val state = viewModel.uiState.value
        assertEquals(5, state.noteCount)
        assertEquals(listOf("Notiz 1", "Notiz 2", "Notiz 3"), state.latestTitles)
        assertTrue(!state.isLoading)
        collector.cancel()
    }
}
