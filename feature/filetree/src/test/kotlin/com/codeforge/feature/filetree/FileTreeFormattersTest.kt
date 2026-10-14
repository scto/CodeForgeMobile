package com.codeforge.feature.filetree

import com.codeforge.core.testing.TestRes
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.Locale

class FileTreeFormattersTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Before
    fun setUp() {
        TestRes.install()
    }

    @After
    fun tearDown() {
        TestRes.uninstall()
    }

    @Test
    fun testFormatFileSize() {
        assertEquals("0 B", FileTreeFormatters.formatFileSize(-1))
        assertEquals("0 B", FileTreeFormatters.formatFileSize(0))
        assertEquals("512 B", FileTreeFormatters.formatFileSize(512))
        assertEquals("1.0 KB", FileTreeFormatters.formatFileSize(1024))
        assertEquals("1.5 KB", FileTreeFormatters.formatFileSize(1536))
        assertEquals("1.0 MB", FileTreeFormatters.formatFileSize(1024 * 1024))
        assertEquals("2.5 MB", FileTreeFormatters.formatFileSize((2.5 * 1024 * 1024).toLong()))
        assertEquals("1.0 GB", FileTreeFormatters.formatFileSize(1024L * 1024 * 1024))
    }

    @Test
    fun testFormatFileDate() {
        assertEquals("-", FileTreeFormatters.formatFileDate(0L))
        assertEquals("-", FileTreeFormatters.formatFileDate(-500L))

        // Fixed timestamp: 1700000000000L -> 14.11.2023 ...
        val formatted = FileTreeFormatters.formatFileDate(1700000000000L, Locale.GERMAN)
        assertTrue(formatted.contains("2023"))
        assertTrue(formatted.contains("."))
    }

    @Test
    fun testFormatChildCount() {
        assertTrue(FileTreeFormatters.formatChildCount(0).isNotEmpty())
        assertTrue(FileTreeFormatters.formatChildCount(1).isNotEmpty())
        assertTrue(FileTreeFormatters.formatChildCount(5).contains("5"))
    }

    @Test
    fun testFormatFilePermissions() {
        val testFile = tempFolder.newFile("sample.txt")
        val perms = FileTreeFormatters.formatFilePermissions(testFile)
        assertEquals(3, perms.length)
        assertTrue(perms.startsWith("r"))
    }

    @Test
    fun testExtractFilePropertiesForFile() {
        val testFile = tempFolder.newFile("hello.kt")
        testFile.writeText("println(\"Hello\")")

        val props = FileTreeFormatters.extractFileProperties(testFile, isDirectory = false)
        assertEquals("hello.kt", props.name)
        assertEquals(testFile.absolutePath, props.path)
        assertTrue(props.type.contains("KT"))
        assertTrue(props.sizeFormatted.contains("Bytes"))
        assertTrue(props.permissions.isNotEmpty())
        assertTrue(props.modifiedFormatted.isNotEmpty())
    }

    @Test
    fun testExtractFilePropertiesForDirectory() {
        val dir = tempFolder.newFolder("myDir")
        File(dir, "child1.txt").writeText("abc")
        File(dir, "child2.txt").writeText("def")

        val props = FileTreeFormatters.extractFileProperties(dir, isDirectory = true)
        assertEquals("myDir", props.name)
        assertEquals(dir.absolutePath, props.path)
        assertTrue(props.sizeFormatted.contains("2"))
    }

    @Test
    fun testFormatSubtitle() {
        val testFile = tempFolder.newFile("test.txt")
        testFile.writeText("content")

        val subtitle = FileTreeFormatters.formatSubtitle(testFile, isDirectory = false)
        assertTrue(subtitle.contains("B •") || subtitle.contains("KB •"))

        val dir = tempFolder.newFolder("subfolder")
        val dirSubtitle = FileTreeFormatters.formatSubtitle(dir, isDirectory = true)
        assertTrue(dirSubtitle.contains("•"))
    }
}
