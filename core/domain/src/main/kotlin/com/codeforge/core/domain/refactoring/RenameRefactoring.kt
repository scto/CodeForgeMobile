// Modul: :core:domain
package com.codeforge.core.domain.refactoring

import com.codeforge.core.domain.model.LspFileRename

/** Neuer Inhalt einer Datei als Folge eines Umbenennens (Pfad = Stand *vor* dem Umbenennen). */
data class RenameFollowUp(val path: String, val newContent: String, val editCount: Int)

/** Woher die Folgeänderungen stammen. */
enum class RenameRefactorSource {
    /** `workspace/willRenameFiles` des Sprachservers. */
    LANGUAGE_SERVER,

    /** Eigene Ersetzung von `package`/`import` (Server unterstützt es nicht oder liefert nichts). */
    PACKAGE_FALLBACK
}

/** Geplante Folgeänderungen zu [rename]; anzuwenden vor dem Umbenennen. */
data class RenameRefactorPlan(
    val rename: LspFileRename,
    val source: RenameRefactorSource,
    val changes: List<RenameFollowUp>,
    /** Bei Ordnern in einem Quellverzeichnis: altes und neues Package. */
    val oldPackage: String? = null,
    val newPackage: String? = null
) {
    /** Anzahl betroffener Dateien. */
    val fileCount: Int get() = changes.size

    /** Anzahl Einzeländerungen. */
    val editCount: Int get() = changes.sumOf { it.editCount }
}

/** Ermittelt Folgeänderungen beim Umbenennen/Verschieben von Dateien und Ordnern (Sprachserver, sonst eigener Plan). */
interface RenameRefactoringService {
    /** Plan für [rename] im Projekt [projectRoot]; `null`, wenn nichts anzupassen ist. */
    suspend fun plan(rename: LspFileRename, projectRoot: String): RenameRefactorPlan?

    /** Meldet das erfolgte Umbenennen an die Sprachserver (`workspace/didRenameFiles`). */
    suspend fun completed(rename: LspFileRename)
}
