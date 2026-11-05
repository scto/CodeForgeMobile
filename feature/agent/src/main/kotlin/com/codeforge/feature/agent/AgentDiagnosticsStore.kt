/**
 * Modul: :feature:agent
 * @author Thomas Schmid
 */
package com.codeforge.feature.agent

import com.codeforge.core.domain.model.DiagnosticSeverity as LspSeverity
import com.codeforge.core.domain.model.LspDiagnostic
import com.codeforge.libs.agent.AgentDiagnostic
import com.codeforge.libs.agent.DiagnosticSeverity
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Hält die zuletzt gemeldeten LSP-Diagnosen je Datei und liefert sie als [AgentDiagnostic] mit Pfaden relativ zum
 * Projektstamm. Hinweise (HINT) werden ausgelassen – sie kosten Kontext und helfen dem Agenten selten.
 */
class AgentDiagnosticsStore {
    private val byPath = ConcurrentHashMap<String, List<LspDiagnostic>>()

    /** Übernimmt die Diagnosen von [absolutePath] (leere Liste = Datei ist sauber). */
    fun update(absolutePath: String, diagnostics: List<LspDiagnostic>) {
        if (diagnostics.isEmpty()) byPath.remove(absolutePath) else byPath[absolutePath] = diagnostics
    }

    /** Diagnosen für [relativePath] oder, bei `null`, aller Dateien unter [root]; höchstens [limit] Einträge. */
    fun query(root: File, relativePath: String?, limit: Int = 200): List<AgentDiagnostic> {
        val rootPath = root.path.trimEnd('/', File.separatorChar)
        val wanted = relativePath?.let { File(root, it).path }
        return byPath.entries
            .filter { (path, _) ->
                path.startsWith(rootPath + File.separator) &&
                    (wanted == null || path == wanted || path.startsWith(wanted + File.separator))
            }
            .sortedBy { it.key }
            .flatMap { (path, list) ->
                val rel = path.removePrefix(rootPath + File.separator).replace(File.separatorChar, '/')
                list.filter { it.severity != LspSeverity.HINT }.map {
                    AgentDiagnostic(rel, it.range.start.line + 1, it.severity.toAgent(), it.message)
                }
            }
            .take(limit)
    }

    private fun LspSeverity.toAgent() = when (this) {
        LspSeverity.ERROR -> DiagnosticSeverity.ERROR
        LspSeverity.WARNING -> DiagnosticSeverity.WARNING
        else -> DiagnosticSeverity.INFO
    }
}
