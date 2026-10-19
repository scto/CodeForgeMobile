// Modul: :libs:lsp-client
package com.codeforge.libs.lsp_client

import com.codeforge.core.domain.model.LspFileOperation
import com.codeforge.core.domain.model.LspPosition
import com.codeforge.core.domain.model.LspRange
import com.codeforge.core.domain.model.LspTextDocumentEdit
import com.codeforge.core.domain.model.LspTextEdit
import com.codeforge.core.domain.model.LspWorkspaceEdit
import com.codeforge.core.domain.model.LspDocumentChange
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LspWorkspaceEditApplierTest {

    @Test
    fun `apply updates open file buffer and closed file disk correctly`() = runBlocking {
        val openBuffers = mutableMapOf("/project/OpenFile.kt" to "class OpenFile {\n    fun test() = 1\n}")
        val diskFiles = mutableMapOf("/project/ClosedFile.kt" to "class ClosedFile {\n    fun run() = 2\n}")

        val applier = LspWorkspaceEditApplier(
            getOpenFileContent = { openBuffers[it] },
            updateOpenFileContent = { path, content -> openBuffers[path] = content },
            readDiskFile = { diskFiles[it] ?: error("Not found") },
            writeDiskFile = { path, content -> diskFiles[path] = content },
            deleteDiskFile = { diskFiles.remove(it) != null },
            renameDiskFile = { old, new ->
                val c = diskFiles.remove(old) ?: return@LspWorkspaceEditApplier false
                diskFiles[new] = c
                true
            },
            fileExists = { openBuffers.containsKey(it) || diskFiles.containsKey(it) }
        )

        val edit = LspWorkspaceEdit(
            changes = mapOf(
                "file:///project/OpenFile.kt" to listOf(
                    LspTextEdit(LspRange(LspPosition(1, 8), LspPosition(1, 12)), "tested")
                ),
                "file:///project/ClosedFile.kt" to listOf(
                    LspTextEdit(LspRange(LspPosition(1, 8), LspPosition(1, 11)), "execute")
                )
            )
        )

        val result = applier.apply(edit).getOrThrow()
        assertEquals(2, result.changedFiles.size)
        assertEquals(2, result.totalEdits)

        assertEquals("class OpenFile {\n    fun tested() = 1\n}", openBuffers["/project/OpenFile.kt"])
        assertEquals("class ClosedFile {\n    fun execute() = 2\n}", diskFiles["/project/ClosedFile.kt"])

        // Test Undo
        result.undo()
        assertEquals("class OpenFile {\n    fun test() = 1\n}", openBuffers["/project/OpenFile.kt"])
        assertEquals("class ClosedFile {\n    fun run() = 2\n}", diskFiles["/project/ClosedFile.kt"])
    }

    @Test
    fun `atomic rollback restores all files when one file write fails`() = runBlocking {
        val openBuffers = mutableMapOf("/project/A.kt" to "original A")
        val diskFiles = mutableMapOf("/project/B.kt" to "original B")

        var diskWriteCount = 0
        val applier = LspWorkspaceEditApplier(
            getOpenFileContent = { openBuffers[it] },
            updateOpenFileContent = { path, content -> openBuffers[path] = content },
            readDiskFile = { diskFiles[it] ?: error("Not found") },
            writeDiskFile = { path, content ->
                diskWriteCount++
                if (path == "/project/B.kt") {
                    throw java.io.IOException("Disk I/O error simulated")
                }
                diskFiles[path] = content
            },
            deleteDiskFile = { diskFiles.remove(it) != null },
            renameDiskFile = { old, new ->
                val c = diskFiles.remove(old) ?: return@LspWorkspaceEditApplier false
                diskFiles[new] = c
                true
            },
            fileExists = { openBuffers.containsKey(it) || diskFiles.containsKey(it) }
        )

        val edit = LspWorkspaceEdit(
            changes = mapOf(
                "file:///project/A.kt" to listOf(
                    LspTextEdit(LspRange(LspPosition(0, 0), LspPosition(0, 8)), "modified")
                ),
                "file:///project/B.kt" to listOf(
                    LspTextEdit(LspRange(LspPosition(0, 0), LspPosition(0, 8)), "modified")
                )
            )
        )

        val result = applier.apply(edit)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is WorkspaceEditException)

        // Verifiziere atomaren Rollback: Puffer A muss wieder auf Original zurückgesetzt sein!
        assertEquals("original A", openBuffers["/project/A.kt"])
        assertEquals("original B", diskFiles["/project/B.kt"])
    }

    @Test
    fun `apply handles file operations and rollback on failure`() = runBlocking {
        val diskFiles = mutableMapOf("/project/OldName.kt" to "content")

        val applier = LspWorkspaceEditApplier(
            getOpenFileContent = { null },
            updateOpenFileContent = { _, _ -> },
            readDiskFile = { diskFiles[it] ?: error("Not found") },
            writeDiskFile = { path, content -> diskFiles[path] = content },
            deleteDiskFile = { diskFiles.remove(it) != null },
            renameDiskFile = { old, new ->
                val c = diskFiles.remove(old) ?: return@LspWorkspaceEditApplier false
                diskFiles[new] = c
                true
            },
            fileExists = { diskFiles.containsKey(it) }
        )

        val edit = LspWorkspaceEdit(
            documentChanges = listOf(
                LspDocumentChange.FileOp(LspFileOperation.CreateFile("file:///project/Created.kt")),
                LspDocumentChange.FileOp(LspFileOperation.RenameFile("file:///project/OldName.kt", "file:///project/NewName.kt"))
            )
        )

        val res = applier.apply(edit).getOrThrow()
        assertTrue(diskFiles.containsKey("/project/Created.kt"))
        assertTrue(diskFiles.containsKey("/project/NewName.kt"))
        assertFalse(diskFiles.containsKey("/project/OldName.kt"))

        // Undo
        res.undo()
        assertFalse(diskFiles.containsKey("/project/Created.kt"))
        assertFalse(diskFiles.containsKey("/project/NewName.kt"))
        assertTrue(diskFiles.containsKey("/project/OldName.kt"))
    }
}
