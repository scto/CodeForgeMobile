// Modul: :core:domain
package com.codeforge.core.domain.repository

import com.codeforge.core.domain.model.FileEntry
import com.codeforge.core.domain.model.FileHandle

/**
 * VFS-Abstraktion. Implementiert in :core:data, konsumiert vom :libs:template-engine
 * bereitgestellten Filesystem sowie SAF-Zugriffen aus :feature:filetree.
 */
interface FileSystemRepository {
    /**
     * Liest eine Datei.
     *
     * @return Pfad und Inhalt oder Fehler.
     */
    suspend fun readFile(path: String): Result<FileHandle>
    /** Schreibt [content] in die Datei [path] (legt sie bei Bedarf an). */
    suspend fun writeFile(path: String, content: String): Result<Unit>
    /** Listet den Inhalt eines Verzeichnisses. */
    suspend fun listDirectory(path: String): Result<List<FileEntry>>
    /** Legt eine leere Datei an. */
    suspend fun createFile(path: String): Result<Unit>
    /** Legt ein Verzeichnis (samt Eltern) an. */
    suspend fun createDirectory(path: String): Result<Unit>
    /** Löscht eine Datei oder ein Verzeichnis (rekursiv). */
    suspend fun delete(path: String): Result<Unit>
    /**
     * Benennt eine Datei oder ein Verzeichnis um.
     *
     * @param newName neuer Name (ohne Pfad).
     */
    suspend fun rename(path: String, newName: String): Result<Unit>
}


