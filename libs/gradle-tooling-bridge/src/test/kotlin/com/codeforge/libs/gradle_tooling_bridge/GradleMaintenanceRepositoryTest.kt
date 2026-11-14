package com.codeforge.libs.gradle_tooling_bridge

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class GradleMaintenanceRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun repo(home: File, running: Boolean = false) = GradleMaintenanceRepositoryImpl({ home }, { running })

    @Test fun clearsOnlyBuildCache() = runBlocking {
        val home = tmp.newFolder("gradle-home")
        File(home, "caches/build-cache-1").apply { mkdirs() }.resolve("a.bin").writeBytes(ByteArray(10))
        val keep = File(home, "caches/modules-2").apply { mkdirs() }.resolve("lib.jar").apply { writeBytes(ByteArray(5)) }
        assertEquals(10L, repo(home).clearBuildCache().getOrThrow())
        assertFalse(File(home, "caches/build-cache-1").exists())
        assertTrue(keep.exists())
    }

    @Test fun projectDirNeedsGradleProjectAndDoesNotFollowLinks() = runBlocking {
        val home = tmp.newFolder("h")
        val outside = tmp.newFolder("outside").resolve("wichtig.txt").apply { writeText("bleibt") }
        val noProject = tmp.newFolder("kein-projekt").apply { File(this, ".gradle").mkdirs() }
        assertTrue(repo(home).deleteProjectGradleDir(noProject.path).isFailure)
        assertTrue(File(noProject, ".gradle").exists())

        val project = tmp.newFolder("projekt").apply { File(this, "settings.gradle.kts").writeText("") }
        val dot = File(project, ".gradle").apply { mkdirs() }
        File(dot, "8.14/x.lock").apply { parentFile.mkdirs(); writeBytes(ByteArray(7)) }
        Files.createSymbolicLink(File(dot, "link").toPath(), outside.parentFile.toPath())
        assertEquals(7L, repo(home).deleteProjectGradleDir(project.path).getOrThrow())
        assertFalse(dot.exists())
        assertTrue("Ziel des Links bleibt unberührt", outside.exists())
    }

    @Test fun refusesWhileBuildRuns() = runBlocking {
        val home = tmp.newFolder("busy").apply { File(this, "caches/x").mkdirs() }
        assertTrue(repo(home, running = true).deleteUserCaches().isFailure)
        assertTrue(File(home, "caches/x").exists())
    }
}
