package {{packageName}}.feature.today

import {{packageName}}.core.domain.model.Profile
import {{packageName}}.core.domain.model.Task
import {{packageName}}.core.domain.repository.ProfileRepository
import {{packageName}}.core.domain.repository.TaskRepository
import {{packageName}}.core.domain.usecase.ObserveProfileUseCase
import {{packageName}}.core.domain.usecase.ObserveTaskStatsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import {{packageName}}.core.domain.usecase.ObserveTasksUseCase
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
class TodayViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    private class FakeTasks(initial: List<Task>) : TaskRepository {
        override val tasks = MutableStateFlow(initial)
        override suspend fun add(title: String): Task = error("nicht gebraucht")
        override suspend fun setDone(id: Long, done: Boolean) = Unit
        override suspend fun delete(id: Long) = Unit
    }

    private class FakeProfile(initial: Profile) : ProfileRepository {
        override val profile = MutableStateFlow(initial)
        override suspend fun update(profile: Profile) {
            this.profile.value = profile
        }
    }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun combinesProfileStatsAndOpenTasks() = runTest(dispatcher) {
        val tasks = FakeTasks(listOf(Task(1, "A", true), Task(2, "B", false), Task(3, "C", false)))
        val viewModel = TodayViewModel(
            ObserveProfileUseCase(FakeProfile(Profile("Alex", dailyGoal = 1))),
            ObserveTaskStatsUseCase(tasks),
            ObserveTasksUseCase(tasks)
        )
        val collector = launch { viewModel.uiState.collect {} }
        val state = viewModel.uiState.value
        assertEquals("Alex", state.name)
        assertEquals(listOf("B", "C"), state.nextTasks)
        assertTrue(state.goalReached)
        collector.cancel()
    }
}
