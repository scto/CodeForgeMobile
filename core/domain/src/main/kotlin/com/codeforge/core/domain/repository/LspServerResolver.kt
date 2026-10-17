// Modul: :core:domain
package com.codeforge.core.domain.repository

import com.codeforge.core.domain.model.LspServerLaunch

/**
 * Löst für eine Sprach-ID (z. B. "kotlin", "java", "python", "html", "css", "json", "c", "cpp", "rust")
 * das zugehörige Startkommando und die Prozessumgebung auf.
 *
 * Implementiert in :feature:settings (greift auf ExtensionsManager und TermuxEnvironment zu),
 * konsumiert von :feature:editor (LspSessionController).
 */
interface LspServerResolver {
    suspend fun resolve(languageId: String): LspServerLaunch?
}
