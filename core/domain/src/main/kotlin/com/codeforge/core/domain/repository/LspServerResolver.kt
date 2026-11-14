// Modul: :core:domain
package com.codeforge.core.domain.repository

import com.codeforge.core.domain.model.LspServerLaunch

/**
 * Löst für eine Sprach-ID (z. B. "kotlin", "java", "python", "html", "css", "json", "c", "cpp", "rust")
 * das zugehörige Startkommando und die Prozessumgebung auf.
 *
 * Implementiert in :feature:settings (greift auf die Plugin-Contributions und TermuxEnvironment zu),
 * konsumiert von :feature:editor (LspSessionController).
 */
interface LspServerResolver {
    /**
     * Ermittelt, wie der Server für eine Sprache gestartet wird.
     *
     * @param languageId Sprachkennung, z. B. `kotlin`.
     * @return Startkommando und Umgebung oder `null`, wenn kein Server verfügbar ist.
     */
    suspend fun resolve(languageId: String): LspServerLaunch?
}
