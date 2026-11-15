package {{packageName}}.feature.tasks

import {{packageName}}.core.domain.model.Task
import {{packageName}}.core.domain.repository.TaskRepository
import {{packageName}}.core.domain.usecase.AddTaskUseCase
import {{packageName}}.core.domain.usecase.DeleteTaskUseCase
import {{packageName}}.core.domain.usecase.ObserveTasksUseCase
import {{packageName}}.core.domain.usecase.SetTaskDoneUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class TasksViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    private class FakeRepository : TaskRepository {
        override val tasks = MutableStateFlow(listOf(Task(1, "A", false), Task(2, "B", true)))
        override suspend fun add(title: String): Task =
            Task(tasks.value.size + 1L, title, false).also { task -> tasks.update { it + task } }
        override suspend fun setDone(id: Long, done: Boolean) =
            tasks.update { list -> list.map { if (it.id == id) it.copy(done = done) else it } }
        override suspend fun delete(id: Long) = tasks.update { list -> list.filterNot { it.id == id } }
    }

    private fun viewModel(repository: TaskRepository) = TasksViewModel(
        ObserveTasksUseCase(repository),
        AddTaskUseCase(repository),
        SetTaskDoneUseCase(repository),
        DeleteTaskUseCase(repository)
    )

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun filterShowsOnlyOpenTasks() = runTest(dispatcher) {
        val viewModel = viewModel(FakeRepository())
        val collector = launch { viewModel.uiState.collect {} }
        viewModel.onEvent(TasksUiEvent.FilterSelected(TaskFilter.OPEN))
        assertEquals(listOf("A"), viewModel.uiState.value.tasks.map { it.title })
        viewModel.onEvent(TasksUiEvent.DoneChanged(1, true))
        assertTrue(viewModel.uiState.value.tasks.isEmpty())
        collector.cancel()
    }

    @Test
    fun addingTaskClosesDialog() = runTest(dispatcher) {
        val viewModel = viewModel(FakeRepository())
        val collector = launch { viewModel.uiState.collect {} }
        viewModel.onEvent(TasksUiEvent.AddClicked)
        viewModel.onEvent(TasksUiEvent.TitleChanged("Neu"))
        viewModel.onEvent(TasksUiEvent.SaveClicked)
        assertEquals(null, viewModel.uiState.value.newTitle)
        assertEquals("Neu", viewModel.uiState.value.tasks.last().title)
        collector.cancel()
    }
}
