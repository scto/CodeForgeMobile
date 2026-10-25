package com.codeforge.libs.j2k

import com.codeforge.libs.j2k.scanner.ProjectLanguageScannerImpl
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ProjectLanguageScannerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val scanner = ProjectLanguageScannerImpl()

    @Test
    fun testScanProjectWithHighJavaPercentage() = runBlocking {
        val root = tempFolder.newFolder("java_project")
        val srcMain = File(root, "src/main/java/com/example").apply { mkdirs() }
        File(srcMain, "Main.java").writeText("public class Main {\n    int a;\n}\n")
        File(srcMain, "package-info.java").writeText("// info")
        File(root, "build.gradle").writeText("apply plugin: 'java'")

        val result = scanner.scanProject(root.absolutePath)
        assertEquals(1, result.javaFilesCount)
        assertEquals(0, result.kotlinFilesCount)
        assertEquals(100f, result.javaPercentage, 0.01f)
        assertTrue(result.hasGroovyGradle)
        assertFalse(result.hasKotlinDsl)
        assertTrue(result.qualifiesForAutoPrompt)

        val plan = scanner.createPlan(root.absolutePath)
        assertEquals(1, plan.javaFiles.size)
        assertEquals(1, plan.groovyFiles.size)
    }

    @Test
    fun testScanProjectExcludesBuildAndBackupDirs() = runBlocking {
        val root = tempFolder.newFolder("mixed_project")
        val buildDir = File(root, "build/generated").apply { mkdirs() }
        File(buildDir, "Generated.java").writeText("class Generated {}")
        val backupDir = File(root, "Backup").apply { mkdirs() }
        File(backupDir, "Old.java").writeText("class Old {}")

        val srcMain = File(root, "src/main/kotlin").apply { mkdirs() }
        File(srcMain, "Main.kt").writeText("fun main() {}\n")

        val result = scanner.scanProject(root.absolutePath)
        assertEquals(0, result.javaFilesCount)
        assertEquals(1, result.kotlinFilesCount)
        assertFalse(result.qualifiesForAutoPrompt)
    }
}
