/**
 * Modul: :core:domain
 * @author Thomas Schmid
 */
package com.codeforge.core.domain.model

data class ImportedProject(
    val rootPath: String,
    val name: String,
    val fileCount: Int
)
