package com.codeforge.core.domain.model

import kotlinx.serialization.Serializable

/** Cursorposition (Zeile, Spalte). */
@Serializable
data class CfmCursorPosition(
    val line: Int,
    val column: Int
)

/** Eine im Editor geöffnete Datei mit Aktiv-Markierung und Cursorposition. */
@Serializable
data class CfmOpenFile(
    val path: String,
    val active: Boolean = false,
    val cursor: CfmCursorPosition? = null
)

/** Eintrag der Projektdateiliste: relativer Pfad, Größe und Änderungszeit. */
@Serializable
data class CfmFileInfo(
    val path: String,
    val size: Long,
    val modified: Long
)

/** Inhalt der Arbeitsbereichsdatei `.cfm/cfm.json`: Schemaversion, Projekt, Zeitstempel, Dateiliste, offene und geänderte Dateien. */
@Serializable
data class CfmWorkspace(
    val version: Int = 1,
    val project: String,
    val updatedAt: String,
    val files: List<CfmFileInfo> = emptyList(),
    val openFiles: List<CfmOpenFile> = emptyList(),
    val modifiedFiles: List<String> = emptyList()
)
