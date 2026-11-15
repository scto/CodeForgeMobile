package {{packageName}}.feature.notes

import {{packageName}}.core.domain.model.Accent
import {{packageName}}.core.domain.model.AppSettings
import {{packageName}}.core.domain.model.Note
import {{packageName}}.core.domain.model.NoteLayout
import {{packageName}}.core.domain.model.SortOrder
import {{packageName}}.core.domain.model.ThemeMode
import {{packageName}}.core.domain.repository.NoteRepository
import {{packageName}}.core.domain.repository.SettingsRepository
import {{packageName}}.core.domain.usecase.ObserveNotesUseCase
import {{packageName}}.core.domain.usecase.ObserveSettingsUseCase
import {{packageName}}.core.domain.usecase.TogglePinUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NotesViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    private class FakeNotes : NoteRepository {
        override val notes = MutableStateFlow(listOf(Note(1, "Einkauf", "Milch"), Note(2, "Idee", "Podcast")))
        override fun note(id: Long): Flow<Note?> = notes.map { list -> list.firstOrNull { it.id == id } }
        override suspend fun upsert(note: Note): Long {
            notes.update { list -> list.map { if (it.id == note.id) note else it } }
            return note.id
        }
        override suspend fun delete(id: Long) = Unit
    }

    private class FakeSettings : SettingsRepository {
        override val settings = MutableStateFlow(AppSettings(layout = NoteLayout.LIST, sortOrder = SortOrder.TITLE))
        override suspend fun setThemeMode(mode: ThemeMode) = Unit
        override suspend fun setAccent(accent: Accent) = Unit
        override suspend fun setLayout(layout: NoteLayout) = Unit
        override suspend fun setSortOrder(order: SortOrder) = Unit
    }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun searchFiltersAndPinMovesNote() = runTest(dispatcher) {
        val notes = FakeNotes()
        val viewModel = NotesViewModel(ObserveNotesUseCase(notes), ObserveSettingsUseCase(FakeSettings()), TogglePinUseCase(notes))
        val collector = launch { viewModel.uiState.collect {} }
        assertEquals(NoteLayout.LIST, viewModel.uiState.value.layout)
        assertEquals(listOf("Einkauf", "Idee"), viewModel.uiState.value.others.map { it.title })

        viewModel.onEvent(NotesUiEvent.QueryChanged("pod"))
        assertEquals(listOf("Idee"), viewModel.uiState.value.others.map { it.title })

        viewModel.onEvent(NotesUiEvent.QueryChanged(""))
        viewModel.onEvent(NotesUiEvent.PinToggled(2))
        assertEquals(listOf("Idee"), viewModel.uiState.value.pinned.map { it.title })
        collector.cancel()
    }
}
