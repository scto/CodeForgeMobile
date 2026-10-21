// Modul: :core:domain
package com.codeforge.core.domain.repository

data class OpenDocumentInfo(
    val path: String,
    val languageId: String,
    val content: String
)

interface OpenDocumentsProvider {
    fun getOpenDocuments(): List<OpenDocumentInfo>
}
