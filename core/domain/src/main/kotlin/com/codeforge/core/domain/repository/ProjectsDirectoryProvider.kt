package com.codeforge.core.domain.repository

import java.io.File

/**
 * Zentraler Anbieter für das primäre Projekt-Stammverzeichnis (Task 55).
 *
 * Verwaltet den einheitlichen Pfad im externen Speicher ("CodeForgeMobileProjects"),
 * Berechtigungsprüfung, Fallback bei fehlenden Rechten und sichere Pfadbereinigung
 * gegen Verzeichnis-Traversierung.
 */
interface ProjectsDirectoryProvider {

    companion object {
        /**
         * Offizieller Standardname des Projektordners im externen Speicher.
         * Historisch in Commit 4bdb999 als Standard festgelegt.
         */
        const val PROJECTS_DIR_NAME = "CodeForgeMobileProjects"
    }

    /**
     * Liefert das Projekt-Stammverzeichnis.
     * Wenn der externe Speicher beschreibbar ist: <externer Speicher>/CodeForgeMobileProjects/
     * Fallback bei fehlenden Rechten: context.getExternalFilesDir("projects") bzw. app-internes Verzeichnis.
     */
    fun getProjectsRoot(): File

    /**
     * Gibt an, ob der primäre externe Speicher nutzbar und beschreibbar ist.
     */
    fun isExternalStorageUsable(): Boolean

    /**
     * Bereinigt einen vom Nutzer oder aus Repositories stammenden Projektnamen,
     * so dass er nur sichere Zeichen enthält [^A-Za-z0-9._-].
     */
    fun sanitizeProjectName(name: String): String

    /**
     * Löst einen sicheren Zielordner für das Projekt unterhalb des Stammverzeichnisses auf.
     * Verhindert ".." und Directory-Traversal-Ausbrüche.
     */
    fun resolveProjectPath(projectName: String): File

    /**
     * Prüft, ob die gegebene Datei oder das Verzeichnis innerhalb des Stammverzeichnisses liegt.
     */
    fun isSubpathOfProjectsRoot(file: File): Boolean

    /**
     * Absoluter Pfad des Stammverzeichnisses.
     */
    val projectsRootPath: String
        get() = getProjectsRoot().absolutePath
}
