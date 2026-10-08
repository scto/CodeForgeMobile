package com.codeforge.libs.indexing_impl

import com.codeforge.libs.indexing_api.IndexedFileKind
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectIndexBuilderTest {

    private fun write(root: File, rel: String, text: String = "") {
        File(root, rel).apply { parentFile.mkdirs(); writeText(text) }
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
}
