package com.codeforge.feature.git

import com.codeforge.core.domain.repository.GitRepository
import com.codeforge.core.domain.repository.ProjectsDirectoryProvider
import com.codeforge.core.domain.repository.RecentProjectsRepository
import com.codeforge.core.testing.TestRes
import java.io.File
import java.lang.reflect.Proxy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GitCloneViewModelTest {

    private var root = File("/data/app/projects")

    private val provider = proxy<ProjectsDirectoryProvider> { name ->
        if (name == "getProjectsRoot") root else error("unexpected $name")
    }
    private val git = proxy<GitRepository> { name -> if (name == "clone") emptyFlow<Any>() else error("unexpected $name") }
    private val recents = proxy<RecentProjectsRepository> { error("unexpected $it") }

    @Suppress("UNCHECKED_CAST")
    private inline fun <reified T : Any> proxy(crossinline handler: (String) -> Any?): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, m, _ -> handler(m.name) } as T

    @Before fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        TestRes.install()
    }

    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun defaultTargetFollowsPermissionAfterResume() {
        val vm = GitCloneViewModel(git, recents, provider)
        assertEquals("/data/app/projects", vm.uiState.value.targetDirectory)
        root = File("/storage/emulated/0/CodeForgeMobileProjects") // Berechtigung inzwischen erteilt
        vm.onEvent(GitCloneUiEvent.ScreenResumed)
        assertEquals("/storage/emulated/0/CodeForgeMobileProjects", vm.uiState.value.targetDirectory)
    }

    @Test fun userEditedTargetIsNeverReplaced() {
        val vm = GitCloneViewModel(git, recents, provider)
        vm.onEvent(GitCloneUiEvent.TargetDirChanged("/sdcard/Eigen"))
        root = File("/storage/emulated/0/CodeForgeMobileProjects")
        vm.onEvent(GitCloneUiEvent.ScreenResumed)
        assertEquals("/sdcard/Eigen", vm.uiState.value.targetDirectory)
    }
}
