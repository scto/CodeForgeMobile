/**
 * Modul: :feature:agent
 * @author Thomas Schmid
 */
package com.codeforge.feature.agent

import com.codeforge.core.navigation.FileSyncBridge
import com.codeforge.libs.agent.AgentWorkspace
import java.io.File

/**
 * Stimmt den Agenten mit dem Editor ab: ungespeicherte Puffer werden vor dem Lesen gespeichert, Änderungen des
 * Agenten (Schreiben, Löschen) werden dem Editor gemeldet, damit offene Tabs neu laden.
 */
class SyncingAgentWorkspace(
    private val delegate: AgentWorkspace,
    private val root: File,
    private val sync: FileSyncBridge,
) : AgentWorkspace by delegate {

    private fun abs(path: String) = File(root, path).path

    override suspend fun readFile(path: String): String? {
        sync.requestFlush(setOf(abs(path)))
        return delegate.readFile(path)
    }

    override suspend fun writeFile(path: String, content: String) {
        sync.requestFlush(setOf(abs(path)))
        delegate.writeFile(path, content)
        sync.notifyExternalChange(listOf(abs(path)))
    }

    override suspend fun deletePath(path: String): Boolean {
        val target = File(root, path)
        val wasDirectory = target.isDirectory
        if (wasDirectory) sync.requestFlushDirectory(target.path) else sync.requestFlush(setOf(target.path))
        val deleted = delegate.deletePath(path)
        if (deleted) {
            if (wasDirectory) sync.notifyDirectoryChange(target.path) else sync.notifyExternalChange(listOf(target.path))
        }
        return deleted
    }
}
