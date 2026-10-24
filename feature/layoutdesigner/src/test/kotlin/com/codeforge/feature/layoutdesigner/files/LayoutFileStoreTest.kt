package com.codeforge.feature.layoutdesigner.files

import java.io.File
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LayoutFileStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val fileStore = DefaultLayoutFileStore()

    @Test
    fun readText_existingFile_returnsContent() = runTest {
        val file = tempFolder.newFile("sample.xml")
        file.writeText("<LinearLayout/>")

        val result = fileStore.readText(file.absolutePath)
        assertEquals("<LinearLayout/>", result)
    }

    @Test(expected = IOException::class)
    fun readText_missingFile_throwsIOException() = runTest {
        val missing = File(tempFolder.root, "non_existent.xml")
        fileStore.readText(missing.absolutePath)
    }

    @Test
    fun writeAtomically_writesContentAndCleansTmp() = runTest {
        val target = File(tempFolder.root, "sub/test.xml")
        val content = "<TextView android:text=\"Hello\" />"

        fileStore.writeAtomically(target.absolutePath, content)

        assertTrue(target.exists())
        assertEquals(content, target.readText())
        val tmpFile = File(target.parentFile, "${target.name}.tmp")
        assertFalse("Temporary file must be cleaned up", tmpFile.exists())
    }

    @Test
    fun writeAtomically_overwritesExistingFile() = runTest {
        val target = tempFolder.newFile("existing.xml")
        target.writeText("initial")

        fileStore.writeAtomically(target.absolutePath, "updated")

        assertEquals("updated", target.readText())
        val tmpFile = File(target.parentFile, "${target.name}.tmp")
        assertFalse("Temporary file must be cleaned up", tmpFile.exists())
    }
}
