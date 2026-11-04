// Modul: :core:domain
package com.codeforge.core.domain.repository

/** Kurzinfo zu einem geöffneten Dokument. */
data class OpenDocumentInfo(
    val path: String,
    val languageId: String,
    val content: String
)

/** Liefert die aktuell im Editor geöffneten Dokumente (für Sprachserver und KI-Kontext). */
interface OpenDocumentsProvider {
    /** Liefert die geöffneten Dokumente. */
    fun getOpenDocuments(): List<OpenDocumentInfo>
}
