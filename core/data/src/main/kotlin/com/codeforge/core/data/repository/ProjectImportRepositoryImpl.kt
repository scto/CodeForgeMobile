/**
 * Modul: :core:data
 * @author Thomas Schmid
 */
package com.codeforge.core.data.repository

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.codeforge.core.domain.model.ImportedProject
import com.codeforge.core.domain.repository.ImportProgress
import com.codeforge.core.domain.repository.ProjectImportRepository
import com.codeforge.core.resources.R
import com.codeforge.core.resources.Res
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import javax.inject.Inject

class ProjectImportRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : ProjectImportRepository {

    override fun importFromTree(treeUriString: String): Flow<ImportProgress> = flow {
        val treeUri: Uri = Uri.parse(treeUriString)
        val sourceRoot = DocumentFile.fromTreeUri(context, treeUri)

        if (sourceRoot == null || !sourceRoot.isDirectory) {
            emit(ImportProgress.Failed(Res.string(R.string.data_ausgewaehltes_verzeichnis_konnte_nicht)))
            return@flow
        }

        val projectName = sourceRoot.name?.takeIf { it.isNotBlank() } ?: Res.string(R.string.data_importiertes_projekt)
        val targetRoot = uniqueTargetDirectory(projectName)
        if (!targetRoot.mkdirs() && !targetRoot.isDirectory) {
            emit(ImportProgress.Failed(Res.string(R.string.data_zielverzeichnis_konnte_nicht_angelegt, targetRoot.absolutePath)))
            return@flow
        }

        var copiedCount = 0
        runCatching {
            copiedCount = copyRecursively(sourceRoot, targetRoot) { path ->
                emit(ImportProgress.Copying(currentPath = path, filesCopiedSoFar = copiedCount))
            }
        }.onFailure { throwable ->
            emit(ImportProgress.Failed(throwable.message ?: Res.string(R.string.data_import_fehlgeschlagen)))
            return@flow
        }

        emit(
            ImportProgress.Done(
                ImportedProject(
                    rootPath = targetRoot.absolutePath,
                    name = projectName,
                    fileCount = copiedCount
                )
            )
        )
    }.flowOn(Dispatchers.IO)

    /**
     * Kopiert [source] (SAF-DocumentFile-Baum) rekursiv nach [targetDir] (lokales
     * java.io.File-Verzeichnis). [onFileCopied] wird nach jeder kopierten Datei mit deren
     * Pfad aufgerufen — die Zählung selbst erfolgt im Aufrufer (`copiedCount`-Closure), da
     * `emit` innerhalb des `flow{}`-Builders nicht aus einer normalen rekursiven Funktion
     * heraus aufgerufen werden darf (kein `FlowCollector`-Empfänger hier).
     */
    private suspend fun copyRecursively(
        source: DocumentFile,
        targetDir: File,
        onFileCopied: suspend (path: String) -> Unit
    ): Int {
        var count = 0
        source.listFiles().forEach { child ->
            val childName = child.name ?: return@forEach
            if (child.isDirectory) {
                val childDir = File(targetDir, childName).apply { mkdirs() }
                count += copyRecursively(child, childDir, onFileCopied)
            } else {
                val targetFile = File(targetDir, childName)
                context.contentResolver.openInputStream(child.uri)?.use { input ->
                    targetFile.outputStream().use { output -> input.copyTo(output) }
                }
                count++
                onFileCopied(targetFile.absolutePath)
            }
        }
        return count
    }

    private fun uniqueTargetDirectory(projectName: String): File {
        val projectsRoot = File(context.filesDir, "projects").apply { mkdirs() }
        val sanitized = projectName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        var candidate = File(projectsRoot, sanitized)
        var suffix = 1
        while (candidate.exists()) {
            candidate = File(projectsRoot, "${sanitized}_$suffix")
            suffix++
        }
        return candidate
    }
}
