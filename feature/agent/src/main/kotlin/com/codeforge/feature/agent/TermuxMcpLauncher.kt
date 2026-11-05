/**
 * Modul: :feature:agent
 * @author Thomas Schmid
 */
package com.codeforge.feature.agent

import com.codeforge.libs.mcp.McpProcessLauncher
import com.codeforge.libs.terminal_engine.TermuxEnvironment
import java.io.File
import java.io.IOException

/** Startet stdio-MCP-Server in der Termux-Umgebung, damit `npx`, `uvx` und `python` über deren PATH gefunden werden. */
object TermuxMcpLauncher : McpProcessLauncher {
    override fun launch(command: String, args: List<String>, env: Map<String, String>): Process {
        if (!TermuxEnvironment.isBootstrapInstalled()) {
            throw IOException("Die Termux-Umgebung ist noch nicht eingerichtet – stdio-Server brauchen sie.")
        }
        val script = "exec " + (listOf(command) + args).joinToString(" ") { shellQuote(it) }
        return ProcessBuilder(TermuxEnvironment.bashPath, "-lc", script).apply {
            environment().putAll(TermuxEnvironment.environment())
            environment().putAll(env)
            directory(File(TermuxEnvironment.home))
        }.start()
    }

    /** Einfache Anführungszeichen-Quotierung für die Shell. */
    internal fun shellQuote(s: String): String = "'" + s.replace("'", "'\\''") + "'"
}
