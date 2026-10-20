// Modul: :core:domain
package com.codeforge.core.domain.repository

import com.codeforge.core.domain.model.Snippet
import kotlinx.coroutines.flow.Flow

/**
 * Repository-Schnittstelle für die Verwaltung von Code-Snippets und Live-Templates.
 */
interface SnippetRepository {

    /**
     * Beobachtet alle registrierten Snippets (mitgelieferte und benutzerdefinierte).
     */
    fun observeSnippets(): Flow<List<Snippet>>

    /**
     * Liefert alle aktiven (isEnabled == true) Snippets, die für die angegebene [languageId]
     * gültig sind (oder global ohne Scope).
     */
    suspend fun getSnippetsForLanguage(languageId: String): List<Snippet>

    /**
     * Liefert eine Momentaufnahme aller Snippets.
     */
    suspend fun getAllSnippets(): List<Snippet>

    /**
     * Speichert oder aktualisiert ein Snippet.
     */
    suspend fun saveSnippet(snippet: Snippet): Result<Unit>

    /**
     * Löscht ein benutzerdefiniertes Snippet anhand der [id].
     * Eingebaute Snippets können nicht gelöscht, sondern nur deaktiviert werden.
     */
    suspend fun deleteSnippet(id: String): Result<Unit>

    /**
     * Aktiviert oder deaktiviert ein Snippet.
     */
    suspend fun setSnippetEnabled(id: String, enabled: Boolean): Result<Unit>

    /**
     * Setzt alle Snippets auf die mitgelieferten Standardwerte zurück.
     */
    suspend fun resetToDefaults(): Result<Unit>

    /**
     * Importiert Snippets aus einem VS-Code-Snippet-JSON-String.
     * Existierende Snippets mit gleichem Namen werden überschrieben oder ergänzt.
     */
    suspend fun importSnippets(jsonContent: String): Result<List<Snippet>>

    /**
     * Exportiert die angegebenen [snippets] (oder alle, falls null) im VS-Code-Snippet-JSON-Format.
     */
    suspend fun exportSnippets(snippets: List<Snippet>? = null): Result<String>
}

/**
 * No-Op Implementierung für Tests und Vorschau.
 */
object NoOpSnippetRepository : SnippetRepository {
    override fun observeSnippets(): Flow<List<Snippet>> = kotlinx.coroutines.flow.flowOf(emptyList())
    override suspend fun getSnippetsForLanguage(languageId: String): List<Snippet> = emptyList()
    override suspend fun getAllSnippets(): List<Snippet> = emptyList()
    override suspend fun saveSnippet(snippet: Snippet): Result<Unit> = Result.success(Unit)
    override suspend fun deleteSnippet(id: String): Result<Unit> = Result.success(Unit)
    override suspend fun setSnippetEnabled(id: String, enabled: Boolean): Result<Unit> = Result.success(Unit)
    override suspend fun resetToDefaults(): Result<Unit> = Result.success(Unit)
    override suspend fun importSnippets(jsonContent: String): Result<List<Snippet>> = Result.success(emptyList())
    override suspend fun exportSnippets(snippets: List<Snippet>?): Result<String> = Result.success("{}")
}
