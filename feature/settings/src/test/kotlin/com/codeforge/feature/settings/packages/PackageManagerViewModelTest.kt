package com.codeforge.feature.settings.packages

import com.codeforge.core.domain.model.AptPackage
import com.codeforge.core.domain.model.PackageEvent
import com.codeforge.core.domain.repository.PackageRepository
import com.codeforge.core.testing.TestRes
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PackageManagerViewModelTest {

    private fun pkg(
        name: String,
        installed: String? = null,
        update: Boolean = false,
        large: Boolean = false,
        protectedPkg: Boolean = false,
        desc: String = "",
    ) = AptPackage(name, installed, "2.0", 2048, large, update, protectedPkg, desc)

    private class FakeRepo(var list: List<AptPackage>) : PackageRepository {
        val calls = mutableListOf<String>()
        override suspend fun listPackages() = Result.success(list)
        override fun install(name: String): Flow<PackageEvent> {
            calls += "install:$name"
            list = list.map { if (it.name == name) it.copy(installedVersion = it.availableVersion, hasUpdate = false) else it }
            return flowOf(PackageEvent.Progress(50, "x"), PackageEvent.Success(name))
        }
        override fun remove(name: String): Flow<PackageEvent> {
            calls += "remove:$name"
            list = list.map { if (it.name == name) it.copy(installedVersion = null) else it }
            return flowOf(PackageEvent.Success(name))
        }
        override fun refreshIndex(): Flow<PackageEvent> { calls += "index"; return flowOf(PackageEvent.Success("")) }
        override fun upgradeAll(): Flow<PackageEvent> { calls += "upgrade"; return flowOf(PackageEvent.Success("")) }
    }

    @Before fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        TestRes.install()
    }

    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun filterSortsUpdatesFirstAndSearchesDescription() {
        val all = listOf(
            pkg("zsh", "1"), pkg("git", "1", update = true), pkg("openjdk-21", large = true),
            pkg("jq", desc = "JSON processor"),
        )
        assertEquals(listOf("git", "jq", "openjdk-21", "zsh"), filterPackages(all, "", PackageFilter.ALL).map { it.name })
        assertEquals(listOf("git", "zsh"), filterPackages(all, "", PackageFilter.INSTALLED).map { it.name })
        assertEquals(listOf("git"), filterPackages(all, "", PackageFilter.UPDATES).map { it.name })
        assertEquals(listOf("openjdk-21"), filterPackages(all, "", PackageFilter.LARGE).map { it.name })
        assertEquals(listOf("jq"), filterPackages(all, " JSON ", PackageFilter.ALL).map { it.name })
    }

    @Test fun formatsSizes() {
        assertEquals("", formatSizeKib(0))
        assertEquals("812 KiB", formatSizeKib(812))
        assertEquals("100.0 MiB", formatSizeKib(102400, Locale.US))
        assertEquals("1.5 GiB", formatSizeKib(1536 * 1024L, Locale.US))
    }

    @Test fun loadsAndInstalls() = runTest {
        val repo = FakeRepo(listOf(pkg("jq")))
        val vm = PackageManagerViewModel(repo)
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.packages.size)
        vm.onEvent(PackageManagerEvent.Install("jq"))
        advanceUntilIdle()
        assertEquals(listOf("install:jq"), repo.calls)
        assertTrue(vm.uiState.value.running.isEmpty())
        assertEquals("2.0", vm.uiState.value.packages.single().installedVersion)
    }

    @Test fun removeNeedsConfirmation() = runTest {
        val repo = FakeRepo(listOf(pkg("git", "1")))
        val vm = PackageManagerViewModel(repo)
        advanceUntilIdle()
        vm.onEvent(PackageManagerEvent.RemoveRequested("git"))
        assertEquals("git", vm.uiState.value.pendingRemove)
        assertTrue(repo.calls.isEmpty())
        vm.onEvent(PackageManagerEvent.RemoveDismissed)
        assertNull(vm.uiState.value.pendingRemove)
        vm.onEvent(PackageManagerEvent.RemoveRequested("git"))
        vm.onEvent(PackageManagerEvent.RemoveConfirmed)
        advanceUntilIdle()
        assertEquals(listOf("remove:git"), repo.calls)
        assertNull(vm.uiState.value.packages.single().installedVersion)
    }
}
