package com.codeforge.feature.projectwizard

import androidx.datastore.core.DataStore
import com.codeforge.core.datastore.SettingsRepository
import com.codeforge.core.datastore.proto.AppSettings
import com.codeforge.core.datastore.proto.WizardConfig
import com.codeforge.core.domain.model.GitCredential
import com.codeforge.core.domain.model.GitCredentialInfo
import com.codeforge.core.domain.model.GitIdentity
import com.codeforge.core.domain.model.ProjectHandle
import com.codeforge.core.domain.model.ProjectLanguage
import com.codeforge.core.domain.model.ProjectRequest
import com.codeforge.core.domain.model.ProjectTemplateDescriptor
import com.codeforge.core.domain.model.ProjectTemplateKind
import com.codeforge.core.domain.model.RecentProject
import com.codeforge.core.domain.repository.GitSettingsRepository
import com.codeforge.core.domain.repository.RecentProjectsRepository
import com.codeforge.core.domain.repository.TemplateEngineRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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
class ProjectWizardViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeTemplateEngine: FakeTemplateEngineRepository
    private lateinit var fakeRecentProjects: FakeRecentProjectsRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var fakeDataStore: FakeDataStore
    private lateinit var fakeGitSettings: FakeGitSettingsRepository
    private lateinit var viewModel: ProjectWizardViewModel

    private class FakeTemplateEngineRepository : TemplateEngineRepository {
        var generateResult: Result<ProjectHandle> = Result.success(ProjectHandle("/data/projects/TestApp", "TestApp", 1))
        var generateThrowException: Throwable? = null

        override suspend fun listTemplates(): List<ProjectTemplateDescriptor> = listOf(
            ProjectTemplateDescriptor("empty_activity", ProjectTemplateKind.EMPTY_ACTIVITY, supportsJava = true),
            ProjectTemplateDescriptor("compose_activity", ProjectTemplateKind.COMPOSE_ACTIVITY, supportsJava = false),
        )

        override fun defaultProjectsDirectory(): String = "/data/projects"

        override suspend fun generate(request: ProjectRequest): Result<ProjectHandle> {
            generateThrowException?.let { throw it }
            return generateResult
        }
    }

    private class FakeRecentProjectsRepository : RecentProjectsRepository {
        val projects = MutableStateFlow<List<RecentProject>>(emptyList())
        override val recentProjects: Flow<List<RecentProject>> = projects.asStateFlow()

        override suspend fun addOrUpdate(project: RecentProject) {
            projects.update { current -> listOf(project) + current.filterNot { it.id == project.id } }
        }

        override suspend fun remove(projectId: String) {
            projects.update { current -> current.filterNot { it.id == projectId } }
        }
    }

    private class FakeDataStore(initialSettings: AppSettings = AppSettings.getDefaultInstance()) : DataStore<AppSettings> {
        private val _flow = MutableStateFlow(initialSettings)
        override val data: Flow<AppSettings> = _flow.asStateFlow()

        override suspend fun updateData(transform: suspend (t: AppSettings) -> AppSettings): AppSettings {
            val updated = transform(_flow.value)
            _flow.value = updated
            return updated
        }
    }

    private class FakeGitSettingsRepository : GitSettingsRepository {
        val identityFlow = MutableStateFlow(GitIdentity("Test User", "test@example.com"))
        override val identity: Flow<GitIdentity> = identityFlow.asStateFlow()
        override val credentials: Flow<List<GitCredentialInfo>> = MutableStateFlow(emptyList())

        override suspend fun currentIdentity(): GitIdentity = identityFlow.value
        override suspend fun setIdentity(identity: GitIdentity) { identityFlow.value = identity }
        override suspend fun credentialFor(remoteUrl: String): GitCredential? = null
        override suspend fun saveCredential(host: String, username: String, token: String) {}
        override suspend fun removeCredential(host: String) {}
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeTemplateEngine = FakeTemplateEngineRepository()
        fakeRecentProjects = FakeRecentProjectsRepository()
        fakeDataStore = FakeDataStore(
            AppSettings.newBuilder()
                .setWizard(
                    WizardConfig.newBuilder()
                        .setLastSaveLocation("/data/projects")
                        .setLanguage("KOTLIN")
                        .setMinSdk(24)
                        .setUseGroovyDsl(false)
                        .setSkipGitInit(false)
                        .build()
                )
                .build()
        )
        settingsRepository = SettingsRepository(fakeDataStore)
        fakeGitSettings = FakeGitSettingsRepository()
        viewModel = ProjectWizardViewModel(
            templateEngine = fakeTemplateEngine,
            recentProjects = fakeRecentProjects,
            settings = settingsRepository,
            gitSettings = fakeGitSettings,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state loads templates and default settings`() = runTest(testDispatcher) {
        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertEquals(2, state.templates.size)
        assertEquals("/data/projects", state.targetDir)
        assertEquals(ProjectLanguage.KOTLIN, state.language)
        assertEquals(24, state.minSdk)
        assertTrue(state.useKotlinDsl)
        assertTrue(state.initGit)
        assertFalse(state.gitIdentityMissing)
    }

    @Test
    fun `template selection transitions to configure step and suggests package`() = runTest(testDispatcher) {
        advanceUntilIdle()
        val template = viewModel.uiState.value.templates.first()
        viewModel.onEvent(ProjectWizardUiEvent.TemplateSelected(template))

        val state = viewModel.uiState.value
        assertEquals(WizardStep.CONFIGURE, state.step)
        assertEquals(template, state.selected)
        assertEquals("com.empty", state.packageName)
    }

    @Test
    fun `project name change updates suggested package when unedited`() = runTest(testDispatcher) {
        advanceUntilIdle()
        val template = viewModel.uiState.value.templates.first()
        viewModel.onEvent(ProjectWizardUiEvent.TemplateSelected(template))
        viewModel.onEvent(ProjectWizardUiEvent.ProjectNameChanged("AwesomeApp"))

        val state = viewModel.uiState.value
        assertEquals("AwesomeApp", state.projectName)
        assertEquals("com.empty.awesomeapp", state.packageName)
    }

    @Test
    fun `package name change stops automatic suggestion updates`() = runTest(testDispatcher) {
        advanceUntilIdle()
        val template = viewModel.uiState.value.templates.first()
        viewModel.onEvent(ProjectWizardUiEvent.TemplateSelected(template))
        viewModel.onEvent(ProjectWizardUiEvent.PackageNameChanged("custom.pkg.app"))
        viewModel.onEvent(ProjectWizardUiEvent.ProjectNameChanged("NewName"))

        val state = viewModel.uiState.value
        assertEquals("custom.pkg.app", state.packageName)
    }

    @Test
    fun `compose template forces kotlin language`() = runTest(testDispatcher) {
        advanceUntilIdle()
        viewModel.onEvent(ProjectWizardUiEvent.LanguageChanged(ProjectLanguage.JAVA))
        val composeTemplate = viewModel.uiState.value.templates.first { it.id == "compose_activity" }
        viewModel.onEvent(ProjectWizardUiEvent.TemplateSelected(composeTemplate))

        val state = viewModel.uiState.value
        assertEquals(ProjectLanguage.KOTLIN, state.language)
    }

    @Test
    fun `create with validation errors does not generate and shows errors`() = runTest(testDispatcher) {
        advanceUntilIdle()
        val template = viewModel.uiState.value.templates.first()
        viewModel.onEvent(ProjectWizardUiEvent.TemplateSelected(template))
        // Empty project name -> invalid
        viewModel.onEvent(ProjectWizardUiEvent.CreateClicked)

        val state = viewModel.uiState.value
        assertTrue(state.showErrors)
        assertFalse(state.isGenerating)
    }

    @Test
    fun `successful creation saves settings, updates recents, and emits OpenProject effect`() = runTest(testDispatcher) {
        advanceUntilIdle()
        val template = viewModel.uiState.value.templates.first()
        viewModel.onEvent(ProjectWizardUiEvent.TemplateSelected(template))
        viewModel.onEvent(ProjectWizardUiEvent.ProjectNameChanged("ValidApp"))
        viewModel.onEvent(ProjectWizardUiEvent.PackageNameChanged("com.example.validapp"))

        val effects = mutableListOf<ProjectWizardUiEffect>()
        val job = launch { viewModel.effects.collect { effects.add(it) } }

        viewModel.onEvent(ProjectWizardUiEvent.CreateClicked)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isGenerating)
        assertEquals(1, fakeRecentProjects.projects.value.size)
        assertEquals("/data/projects/TestApp", fakeRecentProjects.projects.value.first().path)
        assertEquals(listOf(ProjectWizardUiEffect.OpenProject("/data/projects/TestApp")), effects)
        job.cancel()
    }

    @Test
    fun `failure in template engine updates generationError without crash`() = runTest(testDispatcher) {
        advanceUntilIdle()
        fakeTemplateEngine.generateResult = Result.failure(IllegalStateException("Directory already exists"))
        val template = viewModel.uiState.value.templates.first()
        viewModel.onEvent(ProjectWizardUiEvent.TemplateSelected(template))
        viewModel.onEvent(ProjectWizardUiEvent.ProjectNameChanged("ValidApp"))
        viewModel.onEvent(ProjectWizardUiEvent.PackageNameChanged("com.example.validapp"))

        viewModel.onEvent(ProjectWizardUiEvent.CreateClicked)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isGenerating)
        assertEquals("Directory already exists", state.generationError)
    }

    @Test
    fun `unexpected exception in template engine coroutine is caught without crashing`() = runTest(testDispatcher) {
        advanceUntilIdle()
        fakeTemplateEngine.generateThrowException = RuntimeException("Unexpected filesystem crash")
        val template = viewModel.uiState.value.templates.first()
        viewModel.onEvent(ProjectWizardUiEvent.TemplateSelected(template))
        viewModel.onEvent(ProjectWizardUiEvent.ProjectNameChanged("ValidApp"))
        viewModel.onEvent(ProjectWizardUiEvent.PackageNameChanged("com.example.validapp"))

        viewModel.onEvent(ProjectWizardUiEvent.CreateClicked)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isGenerating)
        assertEquals("Unexpected filesystem crash", state.generationError)
    }
}
