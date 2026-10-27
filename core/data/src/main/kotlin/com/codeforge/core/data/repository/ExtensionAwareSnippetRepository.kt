// Modul: :core:data
package com.codeforge.core.data.repository

import com.codeforge.core.domain.extension.ContributionRepository
import com.codeforge.core.domain.extension.ExtensionSnippets
import com.codeforge.core.domain.model.Snippet
import com.codeforge.core.domain.repository.SnippetRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ergänzt die eigenen Snippets um die von aktivierten Plugins beigesteuerten (`contributes.snippets`).
 * Plugin-Snippets sind schreibgeschützt: Löschen/Deaktivieren/Überschreiben betrifft nur eigene Snippets.
 */
@Singleton
class ExtensionAwareSnippetRepository @Inject constructor(
    private val delegate: SnippetRepositoryImpl,
    private val contributionRepository: ContributionRepository
) : SnippetRepository {

    private suspend fun extensionSnippets(): List<Snippet> = withContext(Dispatchers.IO) {
        ExtensionSnippets.load(contributionRepository.contributions.value)
    }

    override fun observeSnippets(): Flow<List<Snippet>> {
        val ext = contributionRepository.contributions.map { c -> withContext(Dispatchers.IO) { ExtensionSnippets.load(c) } }
        return combine(delegate.observeSnippets(), ext) { own, fromPlugins -> own + fromPlugins }
    }

    override suspend fun getSnippetsForLanguage(languageId: String): List<Snippet> {
        val lang = languageId.lowercase()
        return delegate.getSnippetsForLanguage(languageId) + extensionSnippets().filter { lang in it.scope }
    }

    override suspend fun getAllSnippets(): List<Snippet> = delegate.getAllSnippets() + extensionSnippets()

    override suspend fun saveSnippet(snippet: Snippet): Result<Unit> =
        if (ExtensionSnippets.isExtensionSnippet(snippet.id)) readOnly() else delegate.saveSnippet(snippet)

    override suspend fun deleteSnippet(id: String): Result<Unit> =
        if (ExtensionSnippets.isExtensionSnippet(id)) readOnly() else delegate.deleteSnippet(id)

    override suspend fun setSnippetEnabled(id: String, enabled: Boolean): Result<Unit> =
        if (ExtensionSnippets.isExtensionSnippet(id)) readOnly() else delegate.setSnippetEnabled(id, enabled)

    override suspend fun resetToDefaults(): Result<Unit> = delegate.resetToDefaults()

    override suspend fun importSnippets(jsonContent: String): Result<List<Snippet>> = delegate.importSnippets(jsonContent)

    /** Export enthält nur eigene Snippets; Plugin-Snippets gehören ihrem Plugin. */
    override suspend fun exportSnippets(snippets: List<Snippet>?): Result<String> =
        delegate.exportSnippets(snippets?.filterNot { ExtensionSnippets.isExtensionSnippet(it.id) })

    private fun <T> readOnly(): Result<T> =
        Result.failure(IllegalStateException("Snippet stammt aus einem Plugin und ist schreibgeschützt"))
}
