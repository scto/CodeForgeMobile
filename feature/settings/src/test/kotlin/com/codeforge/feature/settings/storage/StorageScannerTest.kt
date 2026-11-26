package com.codeforge.feature.settings.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class StorageScannerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun write(file: File, bytes: Int) {
        file.parentFile.mkdirs()
        file.writeBytes(ByteArray(bytes))
    }

    private fun layout(): StorageLayout {
        val files = tmp.newFolder("files")
        val cache = tmp.newFolder("cache")
        val projects = tmp.newFolder("projects")
        return StorageLayout(files, cache, projects, File(files, "home/.gradle"), listOf(File(files, "usr"), File(files, "home")))
    }

    @Test
    fun scanSplitsCategories() {
        val l = layout()
        write(File(l.projectsRoot, "app1/src/Main.kt"), 100)
        write(File(l.projectsRoot, "app1/app/build/out.dex"), 1000)
        write(File(l.projectsRoot, "app1/.gradle/x"), 50)
        write(File(l.projectsRoot, ".cfm/crash/c.txt"), 7)
        write(File(l.gradleUserHome, "caches/modules-2/a.jar"), 400)
        write(File(l.filesDir, "usr/bin/bash"), 300)
        write(File(l.filesDir, "keystores/k.jks"), 20)
        write(File(l.cacheDir, "compose_preview_cache/p"), 30)
        write(File(l.cacheDir, "store/d.zip"), 40)

        val report = StorageScanner(l).scan()
        assertEquals(400L, report.sizes[StorageCategory.DEPENDENCIES])
        assertEquals(1050L, report.sizes[StorageCategory.BUILD_OUTPUT])
        assertEquals(30L, report.sizes[StorageCategory.PREVIEWS])
        assertEquals(40L, report.sizes[StorageCategory.APP_CACHE])
        assertEquals(107L, report.sizes[StorageCategory.PROJECTS])
        assertEquals(300L, report.sizes[StorageCategory.ENVIRONMENT])
        assertEquals(20L, report.sizes[StorageCategory.OTHER])
        assertEquals(listOf("app1"), report.projects.map { it.name })
    }

    @Test
    fun clearAndDeleteDoNotFollowLinks() {
        val l = layout()
        val outside = tmp.newFolder("outside")
        write(File(outside, "keep.txt"), 10)
        write(File(l.projectsRoot, "app1/build/o"), 10)
        Files.createSymbolicLink(File(l.projectsRoot, "app1/build/link").toPath(), outside.toPath())
        val scanner = StorageScanner(l)
        assertEquals(10L, scanner.clear(StorageCategory.BUILD_OUTPUT))
        assertFalse(File(l.projectsRoot, "app1/build").exists())
        assertTrue(File(outside, "keep.txt").exists())

        assertEquals(0L, scanner.deleteProject(File(l.projectsRoot, "app1").path))
        assertFalse(File(l.projectsRoot, "app1").exists())
    }

    @Test(expected = IllegalArgumentException::class)
    fun deleteOutsideRootFails() {
        val l = layout()
        StorageScanner(l).deleteProject(l.filesDir.path)
    }

    @Test
    fun backupSkipsBuildOutput() {
        val l = layout()
        write(File(l.projectsRoot, "app1/src/Main.kt"), 5)
        write(File(l.projectsRoot, "app1/build/x"), 5)
        val bytes = ByteArrayOutputStream()
        val count = ZipOutputStream(bytes).use { StorageScanner(l).backupProjects(it) }
        assertEquals(1, count)
        val names = ZipInputStream(bytes.toByteArray().inputStream()).use { zin ->
            generateSequence { zin.nextEntry }.map { it.name }.toList()
        }
        assertEquals(listOf("app1/src/Main.kt"), names)
    }
}
