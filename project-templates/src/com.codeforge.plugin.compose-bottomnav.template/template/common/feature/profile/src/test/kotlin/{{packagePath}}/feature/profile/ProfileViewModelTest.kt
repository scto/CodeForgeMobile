package {{packageName}}.feature.profile

import {{packageName}}.core.domain.model.Profile
import {{packageName}}.core.domain.model.Task
import {{packageName}}.core.domain.repository.ProfileRepository
import {{packageName}}.core.domain.repository.TaskRepository
import {{packageName}}.core.domain.usecase.ObserveProfileUseCase
import {{packageName}}.core.domain.usecase.ObserveTaskStatsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import {{packageName}}.core.domain.usecase.UpdateProfileUseCase
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
class ProfileViewModelTest {
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
    fun editsAreSavedAndGoalIsLimited() = runTest(dispatcher) {
        val profile = FakeProfile(Profile())
        val viewModel = ProfileViewModel(
            ObserveProfileUseCase(profile),
            ObserveTaskStatsUseCase(FakeTasks(listOf(Task(1, "A", true)))),
            UpdateProfileUseCase(profile)
        )
        val collector = launch { viewModel.uiState.collect {} }
        viewModel.onEvent(ProfileUiEvent.NameChanged("Alex"))
        viewModel.onEvent(ProfileUiEvent.GoalChanged(99))
        assertEquals(Profile("Alex", dailyGoal = 20), profile.profile.value)
        assertEquals(1, viewModel.uiState.value.stats.done)
        assertTrue(viewModel.uiState.value.profile.name == "Alex")
        collector.cancel()
    }
}
