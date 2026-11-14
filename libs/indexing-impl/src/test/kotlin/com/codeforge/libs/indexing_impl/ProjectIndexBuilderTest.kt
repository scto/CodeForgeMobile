package com.codeforge.libs.indexing_impl

import com.codeforge.libs.indexing_api.IndexedFileKind
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectIndexBuilderTest {

    private fun write(root: File, rel: String, text: String = "") {
        File(root, rel).apply { parentFile?.mkdirs(); writeText(text) }
    }

    @Test
    fun buildsIndexForMultiModuleProject() {
        val root = kotlin.io.path.createTempDirectory("idx").toFile()
        try {
            write(root, "settings.gradle.kts", "rootProject.name = \"Demo\"\ninclude(\":app\", \":core:domain\")\ndependencyResolutionManagement { repositories { google() } }")
            write(root, "build.gradle.kts")
            write(root, "app/build.gradle.kts")
            write(root, "core/domain/build.gradle")
            write(root, "gradle/libs.versions.toml", "[versions]\n")
            write(root, "app/src/main/res/values/colors.xml")
            write(root, "app/src/main/kotlin/A.kt")
            write(root, "app/build/generated/Ignored.kt")
            write(root, ".git/config")

            val index = ProjectIndexBuilder.build(root.path)
            assertEquals("Demo", index.rootProjectName)
            assertEquals(listOf(":", ":app", ":core:domain"), index.modules.map { it.gradlePath })
            assertEquals(3, index.buildFiles.size)
            assertTrue(index.hasVersionCatalog)
            assertEquals(1, index.repositories.size)
            assertTrue(index.files.none { it.relativePath.startsWith("app/build/") || it.relativePath.startsWith(".git/") })
            assertEquals(
                IndexedFileKind.ANDROID_RESOURCE_XML,
                index.files.first { it.name == "colors.xml" }.kind
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun projectWithoutSettingsHasOnlyRootModule() {
        val root = kotlin.io.path.createTempDirectory("idx").toFile()
        try {
            write(root, "build.gradle", "")
            val index = ProjectIndexBuilder.build(root.path)
            assertEquals(1, index.modules.size)
            assertFalse(index.hasVersionCatalog)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun classify() {
        assertEquals(IndexedFileKind.GRADLE_SCRIPT, ProjectScanner.classify("gradle/deps.gradle.kts"))
        assertEquals(IndexedFileKind.KOTLIN_SOURCE, ProjectScanner.classify("a/B.kt"))
        assertEquals(IndexedFileKind.OTHER, ProjectScanner.classify("pom.xml"))
    }

    @Test
    fun scanReportsProgressUpToHundredPercent() {
        val root = java.nio.file.Files.createTempDirectory("scan").toFile()
        try {
            java.io.File(root, "app/src").mkdirs()
            repeat(30) { java.io.File(root, "app/src/F$it.kt").writeText("") }
            java.io.File(root, "settings.gradle.kts").writeText("")
            java.io.File(root, "build").apply { mkdirs() }.resolve("ignored.bin").writeText("")
            val seen = ArrayList<Pair<String, Int>>()
            val files = ProjectScanner.scan(root) { file, percent -> seen += file to percent }
            org.junit.Assert.assertEquals(31, files.size)
            org.junit.Assert.assertEquals(100, seen.last().second)
            org.junit.Assert.assertTrue(seen.map { it.second }.zipWithNext().all { (a, b) -> a <= b })
            org.junit.Assert.assertTrue(seen.any { it.first.endsWith(".kt") })
            org.junit.Assert.assertTrue(seen.none { it.first.contains("ignored") })
        } finally {
            root.deleteRecursively()
        }
    }
}
