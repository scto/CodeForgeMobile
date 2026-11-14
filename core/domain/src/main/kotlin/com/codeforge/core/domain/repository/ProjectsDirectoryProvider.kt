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

    /** Vom Nutzer gesetzter Projektstamm (absoluter Pfad) oder `null`, wenn der Standard gilt. */
    fun getCustomRoot(): String? = null

    /**
     * Setzt einen eigenen Projektstamm oder (bei `null`/leer) zurück auf den Standard.
     * Schlägt fehl, wenn der Pfad nicht absolut ist oder nicht angelegt/beschrieben werden kann.
     * Liefert bei Erfolg das neue Stammverzeichnis.
     */
    fun setCustomRoot(path: String?): Result<File> =
        Result.failure(UnsupportedOperationException("Eigener Projektstamm wird nicht unterstützt"))

    /** Standard-Projektstamm ohne Nutzer-Override (extern, sonst App-Speicher). */
    fun defaultRoot(): File = getProjectsRoot()

    /** Pfad `<externer Speicher>/CodeForgeMobileProjects`, unabhängig von der Berechtigung. */
    fun externalDefaultRoot(): File = File("/storage/emulated/0/${PROJECTS_DIR_NAME}")

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
