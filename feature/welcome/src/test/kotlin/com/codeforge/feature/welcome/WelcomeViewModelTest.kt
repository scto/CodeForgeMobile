package com.codeforge.feature.welcome

import com.codeforge.core.domain.model.RecentProject
import com.codeforge.core.domain.repository.RecentProjectsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WelcomeViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepository: FakeRecentProjectsRepository
    private lateinit var viewModel: WelcomeViewModel

    private class FakeRecentProjectsRepository : RecentProjectsRepository {
        val projects = MutableStateFlow<List<RecentProject>>(emptyList())
        override val recentProjects: Flow<List<RecentProject>> = projects.asStateFlow()

        override suspend fun addOrUpdate(project: RecentProject) {
            projects.update { current ->
                listOf(project) + current.filterNot { it.id == project.id }
            }
        }

        override suspend fun remove(projectId: String) {
            projects.update { current -> current.filterNot { it.id == projectId } }
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = FakeRecentProjectsRepository()
        viewModel = WelcomeViewModel(fakeRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state loads recent projects from repository`() = runTest(testDispatcher) {
        val sampleProject = RecentProject("1", "App", "/path/to/app", 1000L, 3)
        fakeRepository.addOrUpdate(sampleProject)
        advanceUntilIdle()

        val state = viewModel.uiState.first { !it.isLoading }
        assertEquals(1, state.recentProjects.size)
        assertEquals("App", state.recentProjects.first().name)
    }

    @Test
    fun `action events trigger corresponding effects`() = runTest(testDispatcher) {
        val effects = mutableListOf<WelcomeUiEffect>()
        val job = launch { viewModel.effect.collect { effects.add(it) } }

        viewModel.onEvent(WelcomeUiEvent.CreateProjectClicked)
        viewModel.onEvent(WelcomeUiEvent.ImportProjectClicked)
        viewModel.onEvent(WelcomeUiEvent.CloneProjectClicked)
        viewModel.onEvent(WelcomeUiEvent.TerminalClicked)
        viewModel.onEvent(WelcomeUiEvent.SettingsClicked)
        advanceUntilIdle()

        assertEquals(
            listOf(
                WelcomeUiEffect.NavigateToProjectWizard,
                WelcomeUiEffect.NavigateToImportPicker,
                WelcomeUiEffect.NavigateToCloneDialog,
                WelcomeUiEffect.NavigateToTerminal,
                WelcomeUiEffect.NavigateToSettings
            ),
            effects
        )
        job.cancel()
    }

    @Test
    fun `recent project actions open editor or settings or remove project`() = runTest(testDispatcher) {
        val project = RecentProject("proj-1", "MyAwesomeProject", "/data/projects/awesome", 1000L, 2)
        fakeRepository.addOrUpdate(project)
        advanceUntilIdle()

        val effects = mutableListOf<WelcomeUiEffect>()
        val job = launch { viewModel.effect.collect { effects.add(it) } }

        // Open project
        viewModel.onEvent(WelcomeUiEvent.RecentProjectOpened("proj-1"))
        advanceUntilIdle()
        assertEquals(WelcomeUiEffect.NavigateToEditor("/data/projects/awesome"), effects.last())

        // Open project settings
        viewModel.onEvent(WelcomeUiEvent.RecentProjectSettingsClicked("proj-1"))
        advanceUntilIdle()
        assertEquals(WelcomeUiEffect.NavigateToProjectSettings("/data/projects/awesome"), effects.last())

        // Remove project
        viewModel.onEvent(WelcomeUiEvent.RecentProjectRemoved("proj-1"))
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.recentProjects.isEmpty())

        job.cancel()
    }

    @Test
    fun `formatRelativeTime and getProjectInitials work correctly`() {
        assertEquals("CF", getProjectInitials("Code-Forge"))
        assertEquals("MA", getProjectInitials("My_Awesome_App"))
        assertEquals("SI", getProjectInitials("Single"))
        assertEquals("?", getProjectInitials("   "))

        val now = System.currentTimeMillis()
        assertTrue(formatRelativeTime(now - 10000).isNotBlank()) // Just now
        assertTrue(formatRelativeTime(now - 600000).isNotBlank()) // 10 min ago
        assertTrue(formatRelativeTime(now - 7200000).isNotBlank()) // 2 hr ago
        assertTrue(formatRelativeTime(now - 172800000).isNotBlank()) // 2 days ago
        assertEquals("", formatRelativeTime(0L))
    }
}
