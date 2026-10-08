package com.codeforge.libs.dependency_updater_impl

import com.codeforge.libs.dependency_updater_api.CheckStatus
import com.codeforge.libs.dependency_updater_api.LibraryCoordinate
import com.codeforge.libs.indexing_api.MavenRepository
import com.codeforge.libs.indexing_impl.ProjectIndexerImpl
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RepositoryFlowTest {

    private class FakeClient(val data: Map<String, List<String>>, val fail: Boolean = false) : MavenMetadataClient {
        var calls = 0
        override suspend fun versions(repository: MavenRepository, coordinate: LibraryCoordinate, forceRefresh: Boolean): Result<List<String>?> {
            calls++
            if (fail) return Result.failure(java.io.IOException("offline"))
            return Result.success(data[coordinate.key])
        }
    }

    private class MemoryStore : DismissalStore {
        val map = HashMap<String, MutableSet<String>>()
        override fun dismissed(rootPath: String): Set<String> = map[rootPath].orEmpty()
        override fun add(rootPath: String, key: String) { map.getOrPut(rootPath) { HashSet() } += key }
    }

    private fun project(): File {
        val root = kotlin.io.path.createTempDirectory("dep").toFile()
        File(root, "settings.gradle.kts").writeText("rootProject.name = \"P\"\ninclude(\":app\")\n")
        File(root, "gradle").mkdirs()
        File(root, "gradle/libs.versions.toml").writeText(
            "[versions]\ncompose = \"1.7.0\"\n[libraries]\nui = { module = \"androidx.compose.ui:ui\", version.ref = \"compose\" }\nokhttp = \"com.squareup.okhttp3:okhttp:4.11.0\"\n"
        )
        return root
    }

    private fun repo(client: MavenMetadataClient, store: DismissalStore = MemoryStore()) =
        DependencyUpdateRepositoryImpl(ProjectIndexerImpl(), client, store, kotlinx.coroutines.CoroutineScope(Dispatchers.Unconfined), Dispatchers.Unconfined)

    private val server = mapOf(
        "androidx.compose.ui:ui" to listOf("1.7.0", "1.7.3"),
        "com.squareup.okhttp3:okhttp" to listOf("4.11.0", "4.12.0")
    )

    @Test fun checkDismissSnoozeApply() = runBlocking {
        val root = project()
        try {
            val store = MemoryStore()
            val r = repo(FakeClient(server), store)
            val updates = r.check(root.path).getOrThrow()
            assertEquals(2, updates.size)

            val ui = updates.first { it.currentVersion == "1.7.0" }
            val ok = updates.first { it.currentVersion == "4.11.0" }
            r.snooze(root.path, ui)
            r.dismiss(root.path, ok)
            val st = r.observeOnce(root.path)
            assertEquals(2, st.updates.size)
            assertEquals(0, st.promptable.size)
            assertEquals(1, st.pending.size) // ui snoozed, ok dismissed
            assertTrue(store.map.values.single().contains(ok.key))

            r.onProjectOpened(root.path) // Unconfined: läuft synchron durch
            val reopened = r.observeOnce(root.path)
            assertEquals(1, reopened.promptable.size) // ui wieder fragbar, ok bleibt dismissed

            val result = r.apply(root.path, listOf(ui)).getOrThrow()
            assertEquals(1, result.applied.size)
            assertTrue(File(root, "gradle/libs.versions.toml").readText().contains("compose = \"1.7.3\""))
            assertEquals(CheckStatus.IDLE, r.observeOnce(root.path).status)
            assertTrue(r.observeOnce(root.path).updates.none { it.currentVersion == "1.7.0" })
        } finally { root.deleteRecursively() }
    }

    @Test fun offlineReportsFailure() = runBlocking {
        val root = project()
        try {
            val r = repo(FakeClient(emptyMap(), fail = true))
            assertTrue(r.check(root.path).isFailure)
            assertEquals(CheckStatus.FAILED, r.observeOnce(root.path).status)
        } finally { root.deleteRecursively() }
    }

    @Test fun applyFailsGracefullyWhenFileChanged() = runBlocking {
        val root = project()
        try {
            val r = repo(FakeClient(server))
            val ui = r.check(root.path).getOrThrow().first { it.currentVersion == "1.7.0" }
            File(root, "gradle/libs.versions.toml").writeText("[versions]\ncompose = \"1.7.1\"\n[libraries]\nui = { module = \"androidx.compose.ui:ui\", version.ref = \"compose\" }\n")
            val res = r.apply(root.path, listOf(ui)).getOrThrow()
            assertEquals(1, res.failed.size)
            assertEquals(0, res.applied.size)
        } finally { root.deleteRecursively() }
    }

    private suspend fun DependencyUpdateRepositoryImpl.observeOnce(root: String) =
        observe(root).first()
}
