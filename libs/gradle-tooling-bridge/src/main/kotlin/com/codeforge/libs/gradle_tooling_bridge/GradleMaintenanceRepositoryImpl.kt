/**
 * Modul: :libs:gradle-tooling-bridge
 * @author Thomas Schmid
 */
package com.codeforge.libs.gradle_tooling_bridge

import com.codeforge.core.domain.model.BuildStatus
import com.codeforge.core.domain.repository.GradleBuildRepository
import com.codeforge.core.domain.repository.GradleMaintenanceRepository
import com.codeforge.core.resources.R
import com.codeforge.core.resources.Res
import com.codeforge.libs.terminal_engine.TermuxEnvironment
import java.io.File
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Löscht Gradle-Caches. Verweigert die Arbeit, solange ein Build läuft; folgt keinen symbolischen Links. */
@Singleton
class GradleMaintenanceRepositoryImpl internal constructor(
    private val userHomeProvider: () -> File,
    private val isBuildRunning: () -> Boolean,
) : GradleMaintenanceRepository {

    @Inject constructor(builds: GradleBuildRepository) : this(
        userHomeProvider = {
            val env = TermuxEnvironment.environment()
            File(env["GRADLE_USER_HOME"] ?: (TermuxEnvironment.home + "/.gradle"))
        },
        isBuildRunning = { builds.currentSession.value?.status == BuildStatus.RUNNING },
    )

    override fun gradleUserHome(): String = userHomeProvider().absolutePath

    override suspend fun clearBuildCache(): Result<Long> = guarded {
        val caches = File(userHomeProvider(), "caches")
        caches.listFiles { f -> f.isDirectory && f.name.startsWith("build-cache-") }.orEmpty().sumOf(::deleteTree)
    }

    override suspend fun deleteProjectGradleDir(projectRoot: String): Result<Long> = guarded {
        val root = File(projectRoot)
        // Nur echte Gradle-Projekte anfassen, damit ein falscher Pfad nichts Fremdes löscht.
        require(MARKERS.any { File(root, it).isFile }) { Res.string(R.string.buildrun_not_a_project, projectRoot) }
        deleteTree(File(root, ".gradle"))
    }

    override suspend fun deleteUserCaches(): Result<Long> = guarded { deleteTree(File(userHomeProvider(), "caches")) }

    private suspend fun guarded(work: () -> Long): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            check(!isBuildRunning()) { Res.string(R.string.buildrun_build_running) }
            work()
        }
    }

    /** Löscht [dir] samt Inhalt und liefert die freigegebenen Bytes; symbolische Links werden entfernt, nicht verfolgt. */
    private fun deleteTree(dir: File): Long {
        val start = dir.toPath()
        if (!Files.exists(start, java.nio.file.LinkOption.NOFOLLOW_LINKS)) return 0L
        var freed = 0L
        Files.walkFileTree(start, object : SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                if (attrs.isRegularFile) freed += attrs.size()
                Files.deleteIfExists(file)
                return FileVisitResult.CONTINUE
            }

            override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult = FileVisitResult.CONTINUE

            override fun postVisitDirectory(d: Path, exc: IOException?): FileVisitResult {
                runCatching { Files.deleteIfExists(d) }
                return FileVisitResult.CONTINUE
            }
        })
        return freed
    }

    private companion object {
        val MARKERS = listOf("settings.gradle.kts", "settings.gradle", "build.gradle.kts", "build.gradle", "gradlew")
    }
}
