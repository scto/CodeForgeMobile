// Modul: :libs:lsp-client
package com.codeforge.libs.lsp_client

import com.codeforge.core.domain.model.LspFileRename
import com.codeforge.core.domain.model.LspWorkspaceEdit
import com.codeforge.core.domain.refactoring.PackageRenamePlanner
import com.codeforge.core.domain.refactoring.RenameFollowUp
import com.codeforge.core.domain.refactoring.RenameRefactorPlan
import com.codeforge.core.domain.refactoring.RenameRefactorSource
import com.codeforge.core.domain.refactoring.RenameRefactoringService
import com.codeforge.core.domain.repository.LspClientRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Folgeänderungen beim Umbenennen: zuerst `workspace/willRenameFiles` der laufenden Sprachserver; liefern sie nichts,
 * bei Ordnern in Quellverzeichnissen der eigene `package`/`import`-Plan ([PackageRenamePlanner]).
 * Dateien lesen über [readFile] (Tests), Projekt durchlaufen über [listFiles].
 */
@Singleton
class RenameRefactoringServiceImpl internal constructor(
    private val lspClient: LspClientRepository,
    private val readFile: (String) -> String?,
    private val listFiles: (String) -> Sequence<String>
) : RenameRefactoringService {

    @Inject
    constructor(lspClient: LspClientRepository) : this(lspClient, ::readOrNull, ::walkSources)

    override suspend fun plan(rename: LspFileRename, projectRoot: String): RenameRefactorPlan? = withContext(Dispatchers.IO) {
        serverPlan(rename) ?: fallbackPlan(rename, projectRoot)
    }

    override suspend fun completed(rename: LspFileRename) {
        runCatching { lspClient.didRenameFiles(listOf(rename)) }
    }

    private suspend fun serverPlan(rename: LspFileRename): RenameRefactorPlan? {
        if (!lspClient.supportsWillRenameFiles()) return null
        val edit = lspClient.willRenameFiles(listOf(rename)).getOrNull() ?: return null
        val changes = toFollowUps(edit)
        if (changes.isEmpty()) return null
        val pkg = PackageRenamePlanner.packageChange(rename.oldPath, rename.newPath)
        return RenameRefactorPlan(rename, RenameRefactorSource.LANGUAGE_SERVER, changes, pkg?.first, pkg?.second)
    }

    // Text-Edits des Servers auf den Dateiinhalt anwenden; Datei-Operationen im Edit werden ignoriert.
    private fun toFollowUps(edit: LspWorkspaceEdit): List<RenameFollowUp> =
        edit.allTextEdits().mapNotNull { (uri, edits) ->
            val path = uri.removePrefix("file://")
            val original = readFile(path) ?: return@mapNotNull null
            val updated = runCatching { applyTextEditsToString(original, edits) }.getOrNull() ?: return@mapNotNull null
            if (updated == original) null else RenameFollowUp(path, updated, edits.size)
        }

    private fun fallbackPlan(rename: LspFileRename, projectRoot: String): RenameRefactorPlan? {
        val (oldPkg, newPkg) = PackageRenamePlanner.packageChange(rename.oldPath, rename.newPath) ?: return null
        val oldDir = rename.oldPath.trimEnd('/') + "/"
        val changes = listFiles(projectRoot)
            .filter(PackageRenamePlanner::isCandidate)
            .mapNotNull { path ->
                val content = readFile(path) ?: return@mapNotNull null
                PackageRenamePlanner.rewrite(path, content, oldPkg, newPkg, insideRenamedDir = path.startsWith(oldDir))
            }
            .toList()
        return if (changes.isEmpty()) null else RenameRefactorPlan(rename, RenameRefactorSource.PACKAGE_FALLBACK, changes, oldPkg, newPkg)
    }

    private companion object {
        private val SKIPPED_DIRS = setOf("build", ".git", ".gradle", ".idea", ".cxx", "node_modules", ".kotlin")

        fun readOrNull(path: String): String? = runCatching { File(path).readText(Charsets.UTF_8) }.getOrNull()

        fun walkSources(root: String): Sequence<String> =
            File(root).walkTopDown()
                .onEnter { it.name !in SKIPPED_DIRS }
                .filter { it.isFile }
                .map { it.absolutePath }
    }
}
