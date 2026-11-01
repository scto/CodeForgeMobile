// Modul: :core:domain
package com.codeforge.core.domain.model

/**
 * Laufzeitdaten eines Language Servers, zusammengefasst über alle seine Sitzungen (ein Prozess je Workspace).
 * `null`-Felder bedeuten „nicht ermittelbar“ (z. B. kein Zugriff auf /proc).
 */
data class LspRuntimeStats(
    val serverId: String,
    val sessionCount: Int,
    val state: LspServerState,
    /** Resident Set Size aller Prozesse des Servers inkl. Kindprozessen (z. B. JVM hinter einem Startskript). */
    val memoryBytes: Long?,
    /** Start der ältesten laufenden Sitzung (Epoch-Millisekunden). */
    val startedAtMillis: Long?,
    /** Letzte Anfrage/Nutzung über alle Sitzungen (Epoch-Millisekunden). */
    val lastUsedMillis: Long?,
    val openDocuments: Int,
    val processIds: List<Long>
)

/** Liefert Laufzeitdaten laufender Language Server (Implementierung in :libs:lsp-client). */
interface LspRuntimeStatsProvider {
    /** Daten der Sitzungen, deren Server-ID in [serverIds] liegt; `null`, wenn keiner davon läuft. */
    suspend fun statsFor(serverIds: Set<String>): LspRuntimeStats?
}
