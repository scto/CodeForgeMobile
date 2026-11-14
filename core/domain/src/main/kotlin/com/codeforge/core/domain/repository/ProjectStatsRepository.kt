package com.codeforge.core.domain.repository

import com.codeforge.core.domain.model.ProjectStats

/** Wertet einen Projektordner aus (Sprachen, Zeilen, Build-System, Git …). */
interface ProjectStatsRepository {
    /**
     * Liest das Projekt ein.
     *
     * @param rootPath Projektwurzel.
     * @param onProgress Anzahl bisher gelesener Dateien (für die Anzeige).
     */
    suspend fun analyze(rootPath: String, onProgress: (filesRead: Int) -> Unit = {}): Result<ProjectStats>
}
