/**
 * Modul: :core:domain
 * @author Thomas Schmid
 *
 * Importiert ein über den SAF-Verzeichnisbaum-Picker (ActivityResultContracts.
 * OpenDocumentTree, siehe :app/ProjectImportRoute.kt) ausgewähltes Projekt in den
 * app-privaten Speicher. Der Rest der App (FileSystemRepository, Bonsai-Dateibaum, PRoot-
 * Rootfs) arbeitet ausschließlich mit absoluten java.io.File-Pfaden — ein `content://`-URI
 * ist dafür nicht direkt nutzbar (kein stabiler Dateisystempfad, keine proot-Bind-Mount-
 * Fähigkeit) — daher der einmalige Kopiervorgang statt einer dauerhaften SAF-Anbindung.
 *
 * [treeUriString] statt android.net.Uri, damit :core:domain frei von Android-Framework-
 * Typen bleibt (Clean-Architecture-Regel dieses Projekts); die Implementierung in
 * :core:data parst den String zurück zu einem Uri.
 */
package com.codeforge.core.domain.repository

import com.codeforge.core.domain.model.ImportedProject
import kotlinx.coroutines.flow.Flow

sealed interface ImportProgress {
    data class Copying(val currentPath: String, val filesCopiedSoFar: Int) : ImportProgress
    data class Done(val result: ImportedProject) : ImportProgress
    data class Failed(val message: String) : ImportProgress
}

interface ProjectImportRepository {
    fun importFromTree(treeUriString: String): Flow<ImportProgress>
}
