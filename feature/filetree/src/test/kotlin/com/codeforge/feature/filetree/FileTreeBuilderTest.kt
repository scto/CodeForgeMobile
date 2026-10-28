/**
 * Modul: :feature:filetree
 * @author Thomas Schmid
 */
package com.codeforge.feature.filetree

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FileTreeBuilderTest {

    @get:Rule val tmp = TemporaryFolder()

    private fun file(root: File, rel: String, text: String = "x"): File =
        File(root, rel).also { it.parentFile.mkdirs(); it.writeText(text) }

    private fun names(entries: List<FileTreeEntry>) = entries.map { it.label }

    @Test
    fun `grouped sort puts hidden folders, folders, hidden files, files in that order`() {
        val root = tmp.newFolder("p")
        File(root, "zeta").mkdir(); File(root, "Alpha").mkdir(); File(root, ".git").mkdir(); File(root, ".idea").mkdir()
        file(root, "b.txt"); file(root, "A.txt"); file(root, ".gitignore"); file(root, ".env")
        val result = FileTreeBuilder.build(root, FileTreeOptions(), emptySet())
        assertEquals(listOf(".git", ".idea", "Alpha", "zeta", ".env", ".gitignore", "A.txt", "b.txt"), names(result))
    }

    @Test
    fun `mixed sort is one case-insensitive list`() {
        val root = tmp.newFolder("p")
        File(root, "b").mkdir(); file(root, "a.txt"); file(root, "C.txt")
        val result = FileTreeBuilder.build(root, FileTreeOptions(sortMode = FileTreeSortMode.MIXED), emptySet())
        assertEquals(listOf("a.txt", "b", "C.txt"), names(result))
    }

    @Test
    fun `natural order and descending`() {
        val root = tmp.newFolder("p")
        file(root, "f10.txt"); file(root, "f2.txt"); file(root, "f1.txt")
        assertEquals(listOf("f1.txt", "f2.txt", "f10.txt"), names(FileTreeBuilder.build(root, FileTreeOptions(), emptySet())))
        assertEquals(listOf("f10.txt", "f2.txt", "f1.txt"), names(FileTreeBuilder.build(root, FileTreeOptions(descending = true), emptySet())))
    }

    @Test
    fun `sort by size and type`() {
        val root = tmp.newFolder("p")
        file(root, "a.kt", "1"); file(root, "b.java", "123"); file(root, "c.kt", "12")
        val bySize = FileTreeBuilder.build(root, FileTreeOptions(sortBy = FileTreeSortBy.SIZE), emptySet())
        assertEquals(listOf("a.kt", "c.kt", "b.java"), names(bySize))
        val byType = FileTreeBuilder.build(root, FileTreeOptions(sortBy = FileTreeSortBy.TYPE), emptySet())
        assertEquals(listOf("b.java", "a.kt", "c.kt"), names(byType))
    }

    @Test
    fun `hidden entries and cfm folder are filtered`() {
        val root = tmp.newFolder("p")
        File(root, ".cfm").mkdir(); File(root, ".git").mkdir(); file(root, ".env"); file(root, "a.txt")
        assertEquals(listOf(".git", ".env", "a.txt"), names(FileTreeBuilder.build(root, FileTreeOptions(), emptySet())))
        assertEquals(listOf("a.txt"), names(FileTreeBuilder.build(root, FileTreeOptions(showHidden = false), emptySet())))
    }

    @Test
    fun `children appear only for expanded directories`() {
        val root = tmp.newFolder("p")
        val sub = File(root, "src").also { it.mkdir() }
        file(sub, "Main.kt")
        val closed = FileTreeBuilder.build(root, FileTreeOptions(), emptySet())
        assertEquals(1, closed.size)
        assertFalse(closed[0].expanded)
        val open = FileTreeBuilder.build(root, FileTreeOptions(), setOf(sub.absolutePath))
        assertEquals(listOf("src", "Main.kt"), names(open))
        assertEquals(1, open[1].depth)
    }

    @Test
    fun `project view hides build dirs and marks modules`() {
        val root = tmp.newFolder("p")
        file(root, "settings.gradle.kts", """include(":app")""")
        File(root, "app").mkdir(); File(root, "build").mkdir(); File(root, ".gradle").mkdir(); File(root, "docs").mkdir()
        val result = FileTreeBuilder.build(root, FileTreeOptions(view = FileTreeView.PROJECT), emptySet())
        assertEquals(listOf("app", "docs", "settings.gradle.kts"), names(result))
        assertEquals(FileTreeEntryKind.MODULE, result.first { it.label == "app" }.kind)
        assertEquals(FileTreeEntryKind.DIRECTORY, result.first { it.label == "docs" }.kind)
    }

    @Test
    fun `module view groups manifests, sources, res and gradle scripts`() {
        val root = tmp.newFolder("p")
        file(root, "settings.gradle.kts", """include(":app", ":feature:ui")""")
        file(root, "build.gradle.kts"); file(root, "README.md"); file(root, "gradle/libs.versions.toml")
        file(root, "app/build.gradle.kts")
        file(root, "app/src/main/AndroidManifest.xml")
        file(root, "app/src/main/kotlin/a/Main.kt")
        file(root, "app/src/main/res/values/strings.xml")
        file(root, "app/src/test/kotlin/a/MainTest.kt")
        file(root, "feature/ui/build.gradle.kts")
        file(root, "feature/ui/src/main/kotlin/Ui.kt")

        val top = FileTreeBuilder.build(root, FileTreeOptions(view = FileTreeView.MODULES), emptySet())
        assertEquals(listOf("app", "feature.ui"), top.filter { it.kind == FileTreeEntryKind.MODULE }.map { it.label })
        assertTrue(top.any { it.group == FileTreeGroup.GRADLE_SCRIPTS })
        assertTrue(top.any { it.group == FileTreeGroup.PROJECT_FILES })

        val appKey = "module:${File(root, "app").absolutePath}"
        val mod = FileTreeBuilder.build(root, FileTreeOptions(view = FileTreeView.MODULES), setOf(appKey))
        val groups = mod.filter { it.kind == FileTreeEntryKind.GROUP && it.depth == 1 }.mapNotNull { it.group }
        assertEquals(listOf(FileTreeGroup.MANIFESTS, FileTreeGroup.SOURCES, FileTreeGroup.RESOURCES), groups)

        val srcKey = "group:${File(root, "app").absolutePath}:SOURCES"
        val withSources = FileTreeBuilder.build(root, FileTreeOptions(view = FileTreeView.MODULES), setOf(appKey, srcKey))
        val sourceDirs = withSources.filter { it.depth == 2 }.map { it.label to it.detail }
        assertEquals(listOf("kotlin" to null, "kotlin" to "test"), sourceDirs)

        val keys = withSources.map { it.key }
        assertEquals("keys must be unique", keys.size, keys.toSet().size)
    }

    @Test
    fun `gradle settings parsing`() {
        val text = """
            // include(":ignored")
            rootProject.name = "x"
            pluginManagement { includeBuild("build-logic") }
            include(":app")
            include(
                ":core:domain",
                ":core:data",
            )
            include ':legacy', ':other'
            /* include(":block") */
        """.trimIndent()
        assertEquals(listOf(":app", ":core:domain", ":core:data", ":legacy", ":other"), GradleModules.parse(text))
        assertEquals("core.domain", GradleModules.displayName(":core:domain"))
    }
}
