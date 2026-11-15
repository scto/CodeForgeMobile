package {{packageName}}.feature.settings

import {{packageName}}.core.domain.model.AppSettings
import {{packageName}}.core.domain.model.ThemeMode
import {{packageName}}.core.domain.repository.SettingsRepository
import {{packageName}}.core.domain.usecase.ObserveSettingsUseCase
import {{packageName}}.core.domain.usecase.UpdateSettingsUseCase
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
class SettingsViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    private class FakeRepository : SettingsRepository {
        override val settings = MutableStateFlow(AppSettings())
        override suspend fun setThemeMode(mode: ThemeMode) = settings.update { it.copy(themeMode = mode) }
        override suspend fun setDynamicColor(enabled: Boolean) = settings.update { it.copy(dynamicColor = enabled) }
    }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun changesAreReflectedInState() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = SettingsViewModel(ObserveSettingsUseCase(repository), UpdateSettingsUseCase(repository))
        val collector = launch { viewModel.uiState.collect {} }
        viewModel.onEvent(SettingsUiEvent.ThemeModeSelected(ThemeMode.DARK))
        viewModel.onEvent(SettingsUiEvent.DynamicColorChanged(false))
        assertEquals(AppSettings(ThemeMode.DARK, dynamicColor = false), viewModel.uiState.value.settings)
        assertTrue(repository.settings.value.themeMode == ThemeMode.DARK)
        collector.cancel()
    }
}
