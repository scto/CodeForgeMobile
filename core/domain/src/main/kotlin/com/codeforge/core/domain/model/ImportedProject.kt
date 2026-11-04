/**
 * Modul: :core:domain
 * @author Thomas Schmid
 */
package com.codeforge.core.domain.model

/** Ergebnis eines Projektimports: Wurzelpfad, Name und Anzahl importierter Dateien. */
data class ImportedProject(
    val rootPath: String,
    val name: String,
    val fileCount: Int
)
