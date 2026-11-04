// Modul: :core:domain
package com.codeforge.core.domain.model

/** Eine gelesene Datei: Pfad und Textinhalt. */
data class FileHandle(
    val path: String,
    val content: String
)

/** Ein Verzeichniseintrag: Name, Pfad, Verzeichnis-Flag und Größe in Bytes. */
data class FileEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val sizeBytes: Long = 0L
)
