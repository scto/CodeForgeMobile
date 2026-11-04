package com.codeforge.core.domain.repository

import com.codeforge.core.domain.model.CfmFileInfo
import com.codeforge.core.domain.model.CfmOpenFile
import com.codeforge.core.domain.model.CfmWorkspace
import java.io.File

/** Persistenz des Projekt-Arbeitsbereichs in `<Projekt>/.cfm/cfm.json` (offene Dateien, geänderte Dateien, Dateiliste). */
interface WorkspaceRepository {
    companion object {
        /** Name des IDE-Verzeichnisses im Projekt. */
        const val CFM_DIR_NAME = ".cfm"
        /** Name der Arbeitsbereichsdatei. */
        const val WORKSPACE_FILE_NAME = "cfm.json"
        /** Aktuelle Schemaversion der Arbeitsbereichsdatei. */
        const val SCHEMA_VERSION = 1

        /** Verzeichnisse, die beim Scannen ausgelassen werden (z. B. `build`, `.git`). */
        val DEFAULT_EXCLUDED_DIRS = setOf(
            ".cfm",
            ".git",
            "build",
            ".gradle",
            ".idea",
            "node_modules",
            "bin",
            "out",
            ".cxx",
            ".externalNativeBuild"
        )
    }

    /**
     * Lädt den Arbeitsbereich.
     *
     * @return der Arbeitsbereich oder `null`, wenn keiner gespeichert ist.
     */
    suspend fun loadWorkspace(projectDir: File): CfmWorkspace?

    /** Speichert den Arbeitsbereich. */
    suspend fun saveWorkspace(projectDir: File, workspace: CfmWorkspace): Result<Unit>

    /** Scannt die Projektdateien (begrenzt auf [maxFiles]). */
    suspend fun scanProjectFiles(projectDir: File, maxFiles: Int = 10000): List<CfmFileInfo>

    /**
     * Aktualisiert die Liste geänderter Dateien.
     *
     * @param modifiedRelativePaths Pfade relativ zum Projekt.
     */
    suspend fun updateModifiedFiles(projectDir: File, modifiedRelativePaths: List<String>): Result<Unit>

    /** Aktualisiert die Liste offener Dateien. */
    suspend fun updateOpenFiles(projectDir: File, openFiles: List<CfmOpenFile>): Result<Unit>

    /** Trägt `.cfm/` in `.git/info/exclude` ein. */
    suspend fun ensureGitExclude(projectDir: File): Result<Unit>
}
