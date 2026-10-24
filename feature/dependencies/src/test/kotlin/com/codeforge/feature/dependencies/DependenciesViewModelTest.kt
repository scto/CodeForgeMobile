package com.codeforge.feature.dependencies

import com.codeforge.core.domain.model.DependencyConflict
import com.codeforge.core.domain.model.DependencyGraph
import com.codeforge.core.domain.model.DependencyInsight
import com.codeforge.core.domain.model.DependencyNode
import com.codeforge.core.domain.repository.DependencyGraphRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DependenciesViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepo: FakeDependencyGraphRepository
    private lateinit var viewModel: DependenciesViewModel

    private class FakeDependencyGraphRepository : DependencyGraphRepository {
        var modulesToReturn = listOf(":app", ":core:domain")
        var configsToReturn = listOf("debugRuntimeClasspath", "debugCompileClasspath")
        var graphToReturn: DependencyGraph = DependencyGraph(
            moduleName = ":app",
            configuration = "debugRuntimeClasspath",
            rootNodes = listOf(
                DependencyNode(
                    coordinate = "org.jetbrains.kotlin:kotlin-stdlib",
                    group = "org.jetbrains.kotlin",
                    name = "kotlin-stdlib",
                    requestedVersion = "2.0.0",
                    resolvedVersion = "2.1.21"
                )
            ),
            conflicts = listOf(
                DependencyConflict(
                    coordinate = "org.jetbrains.kotlin:kotlin-stdlib",
                    group = "org.jetbrains.kotlin",
                    name = "kotlin-stdlib",
                    requestedVersion = "2.0.0",
                    resolvedVersion = "2.1.21",
                    directRequester = null
                )
            ),
            totalDependencies = 1,
            maxDepth = 1
        )
        var insightToReturn = DependencyInsight(
            coordinate = "org.jetbrains.kotlin:kotlin-stdlib",
            resolvedVersion = "2.1.21",
            selectionReasons = listOf("By conflict resolution"),
            pathsToRoot = listOf(listOf("kotlin-stdlib", "compileClasspath"))
        )

        var loadGraphCalls = 0
        var lastModuleLoaded: String? = null
        var lastConfigLoaded: String? = null

        override suspend fun getModules(projectRoot: String): List<String> = modulesToReturn
        override suspend fun getConfigurations(projectRoot: String, moduleName: String): List<String> = configsToReturn
        override suspend fun loadGraph(
            projectRoot: String,
            moduleName: String,
            configuration: String,
            forceReload: Boolean
        ): Result<DependencyGraph> {
            loadGraphCalls++
            lastModuleLoaded = moduleName
            lastConfigLoaded = configuration
            return Result.success(graphToReturn)
        }

        override suspend fun loadInsight(
            projectRoot: String,
            moduleName: String,
            configuration: String,
            coordinate: String
        ): Result<DependencyInsight> = Result.success(insightToReturn)

        override fun invalidateCache(projectRoot: String) {}
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepo = FakeDependencyGraphRepository()
        viewModel = DependenciesViewModel(fakeRepo)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun init_loadsModulesAndInitialGraph() = runTest {
        viewModel.handleIntent(DependenciesUiIntent.Init("/test/project"))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("/test/project", state.projectRoot)
        assertEquals(":app", state.selectedModule)
        assertEquals("debugRuntimeClasspath", state.selectedConfiguration)
        assertNotNull(state.graph)
        assertEquals(1, state.conflictsCount)
        assertEquals(1, fakeRepo.loadGraphCalls)
    }

    @Test
    fun selectModule_updatesModuleAndReloadsGraph() = runTest {
        viewModel.handleIntent(DependenciesUiIntent.Init("/test/project"))
        advanceUntilIdle()

        viewModel.handleIntent(DependenciesUiIntent.SelectModule(":core:domain"))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(":core:domain", state.selectedModule)
        assertEquals(2, fakeRepo.loadGraphCalls)
        assertEquals(":core:domain", fakeRepo.lastModuleLoaded)
    }

    @Test
    fun selectNode_triggersLoadInsight() = runTest {
        viewModel.handleIntent(DependenciesUiIntent.Init("/test/project"))
        advanceUntilIdle()

        val node = viewModel.uiState.value.graph!!.rootNodes.first()
        viewModel.handleIntent(DependenciesUiIntent.SelectNode(node))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(node, state.selectedNode)
        assertNotNull(state.insight)
        assertEquals("2.1.21", state.insight!!.resolvedVersion)
    }

    @Test
    fun expandAndCollapseAll_updatesExpandedKeys() = runTest {
        viewModel.handleIntent(DependenciesUiIntent.Init("/test/project"))
        advanceUntilIdle()

        viewModel.handleIntent(DependenciesUiIntent.ExpandAll)
        assertTrue(viewModel.uiState.value.expandedNodeKeys.isNotEmpty())

        viewModel.handleIntent(DependenciesUiIntent.CollapseAll)
        assertTrue(viewModel.uiState.value.expandedNodeKeys.isEmpty())
    }
}
