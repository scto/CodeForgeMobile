package {{packageName}}.feature.home

import {{packageName}}.core.domain.model.Greeting
import {{packageName}}.core.domain.repository.GreetingRepository
import {{packageName}}.core.domain.usecase.IncrementGreetingUseCase
import {{packageName}}.core.domain.usecase.ObserveGreetingUseCase
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
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    private class FakeRepository : GreetingRepository {
        override val greeting = MutableStateFlow(Greeting("Hallo", 0))
        override suspend fun increment() = greeting.update { it.copy(count = it.count + 1) }
    }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun incrementUpdatesState() = runTest(dispatcher) {
        val repo = FakeRepository()
        val viewModel = HomeViewModel(ObserveGreetingUseCase(repo), IncrementGreetingUseCase(repo))
        val collector = launch { viewModel.uiState.collect {} }
        viewModel.onEvent(HomeUiEvent.IncrementClicked)
        assertEquals(HomeUiState(greeting = "Hallo", count = 1, isLoading = false), viewModel.uiState.value)
        collector.cancel()
    }
}
