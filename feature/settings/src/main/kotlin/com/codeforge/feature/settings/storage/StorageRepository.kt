/**
 * Modul: :feature:settings
 * @author Thomas Schmid
 */
package com.codeforge.feature.settings.storage

import android.content.Context
import android.net.Uri
import com.codeforge.core.common.log.CfmLogger
import com.codeforge.core.domain.model.BuildStatus
import com.codeforge.core.domain.repository.GradleBuildRepository
import com.codeforge.core.domain.repository.GradleMaintenanceRepository
import com.codeforge.core.domain.repository.ProjectsDirectoryProvider
import com.codeforge.core.domain.repository.RecentProjectsRepository
import com.codeforge.core.resources.R
import com.codeforge.core.resources.Res
import com.codeforge.libs.terminal_engine.TermuxEnvironment
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/** Speicherverbrauch, Aufräumen, Projektsicherung und Absturzberichte. */
@Singleton
class StorageRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val projectsDirectory: ProjectsDirectoryProvider,
    private val maintenance: GradleMaintenanceRepository,
    private val builds: GradleBuildRepository,
    private val recentProjects: RecentProjectsRepository
) {

    private fun scanner() = StorageScanner(
        StorageLayout(
            filesDir = context.filesDir,
            cacheDir = context.cacheDir,
            projectsRoot = projectsDirectory.getProjectsRoot(),
            gradleUserHome = File(maintenance.gradleUserHome()),
            environmentDirs = listOf(File(TermuxEnvironment.prefix), File(TermuxEnvironment.home))
        )
    )

    /** Pfad des Projektordners. */
    fun projectsRootPath(): String = projectsDirectory.getProjectsRoot().absolutePath

    /** Misst den Verbrauch. */
    suspend fun scan(): StorageReport = withContext(Dispatchers.IO) { scanner().scan() }

    /** Löscht eine Kategorie; während eines Builds verweigert. */
    suspend fun clear(category: StorageCategory): Result<Long> = withContext(Dispatchers.IO) {
        when (category) {
            StorageCategory.DEPENDENCIES -> maintenance.deleteUserCaches()
            StorageCategory.BUILD_OUTPUT -> guarded { scanner().clear(category) }
            else -> runCatching { scanner().clear(category) }
        }
    }

    /** Löscht alle regenerierbaren Caches außer den Abhängigkeiten (die ein erneuter Download wären). */
    suspend fun clearCaches(): Result<Long> = withContext(Dispatchers.IO) {
        guarded {
            val s = scanner()
            listOf(StorageCategory.BUILD_OUTPUT, StorageCategory.PREVIEWS, StorageCategory.APP_CACHE).sumOf(s::clear)
        }
    }

    /** Löscht ein Projekt und entfernt es aus den zuletzt geöffneten. */
    suspend fun deleteProject(path: String): Result<Long> = withContext(Dispatchers.IO) {
        guarded { scanner().deleteProject(path) }.onSuccess {
            val canonical = runCatching { File(path).canonicalPath }.getOrDefault(path)
            recentProjects.recentProjects.first()
                .filter { runCatching { File(it.path).canonicalPath }.getOrDefault(it.path) == canonical }
                .forEach { recentProjects.remove(it.id) }
        }
    }

    /** Sichert alle Projekte als ZIP nach [target]; liefert die Zahl der Dateien. */
    suspend fun backupProjects(target: Uri): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val stream = checkNotNull(context.contentResolver.openOutputStream(target)) { "Ziel nicht beschreibbar" }
            ZipOutputStream(stream.buffered()).use { scanner().backupProjects(it) }
        }
    }

    // Absturzberichte liegen privat und im Projektordner.
    private fun crashDirs(): List<File> = listOf(
        File(context.filesDir, CRASH_DIR),
        File(projectsDirectory.getProjectsRoot(), "${CfmLogger.CFM_DIR_NAME}/$CRASH_DIR")
    )

    /** Anzahl der Absturzberichte. */
    suspend fun crashReportCount(): Int = withContext(Dispatchers.IO) {
        crashDirs().sumOf { dir -> dir.listFiles().orEmpty().count { it.isFile } }
    }

    /** Löscht alle Absturzberichte; liefert die Anzahl. */
    suspend fun clearCrashReports(): Int = withContext(Dispatchers.IO) {
        crashDirs().sumOf { dir -> dir.listFiles().orEmpty().filter { it.isFile }.count { it.delete() } }
    }

    private suspend fun <T> guarded(work: () -> T): Result<T> = runCatching {
        check(builds.currentSession.value?.status != BuildStatus.RUNNING) { Res.string(R.string.buildrun_build_running) }
        work()
    }

    private companion object {
        const val CRASH_DIR = "crash"
    }
}
