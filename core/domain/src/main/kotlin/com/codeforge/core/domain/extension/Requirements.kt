/**
 * Modul: :core:domain
 * @author Thomas Schmid
 */
package com.codeforge.core.domain.extension

import kotlinx.coroutines.flow.Flow

/** Regeln für `contributes.requires`: gültige Namen und die Freigabeliste für den Store. */
object RequirementPolicy {
    private val NAME = Regex("[A-Za-z0-9@][A-Za-z0-9@._/+-]*")

    /** Verhindert Optionen (`-g`, `--foo`) und Pfadtricks (`..`) als Paketnamen. */
    fun isValidName(name: String): Boolean = NAME.matches(name) && !name.contains("..")

    /** Pakete, die Store-Plugins anfordern dürfen (nur Programme, die CodeForge selbst als Tool-Plugins verwendet). */
    private val STORE_ALLOWED: Map<RequirementManager, Set<String>> = mapOf(
        RequirementManager.PKG to setOf(
            "clang", "cmake", "nodejs", "python", "golang", "rust", "rust-analyzer", "ruff", "shfmt", "shellcheck",
            "taplo", "gopls", "dart", "neocmakelsp", "ktlint", "git", "jq", "ripgrep"
        ),
        RequirementManager.NPM to setOf(
            "prettier", "typescript", "typescript-language-server", "pyright", "bash-language-server",
            "vscode-langservers-extracted", "yaml-language-server"
        ),
        RequirementManager.PIP to emptySet()
    )

    fun isStoreAllowed(r: RequirementContribution): Boolean = r.name in STORE_ALLOWED[r.manager].orEmpty()
}

/** Platzhalter-Ersetzung für Werkzeug-Kommandos; jedes Listenelement bleibt ein einzelnes Argument (keine Shell). */
fun ToolContribution.expandCommand(projectDir: String, pluginDir: String, home: String, input: String): List<String> =
    command.map {
        it.replace("${'$'}{projectDir}", projectDir)
            .replace("${'$'}{pluginDir}", pluginDir)
            .replace("${'$'}{home}", home)
            .replace("${'$'}{input}", input)
    }

/** Ereignisse eines Werkzeug- bzw. Installationslaufs. */
sealed interface ToolRunEvent {
    data class Line(val text: String) : ToolRunEvent
    data class Finished(val exitCode: Int, val timedOut: Boolean = false) : ToolRunEvent
}

/** Führt Plugin-Werkzeuge aus und installiert deren Voraussetzungen (nach Bestätigung durch den Nutzer). */
interface PluginToolRunner {
    /** Noch nicht vorhandene Voraussetzungen des Plugins [pluginId] (aktivierte Plugins). */
    suspend fun missingRequirements(pluginId: String): List<RequirementContribution>

    /** Installiert [requirements] nacheinander; bricht beim ersten Fehler ab. Abbruch des Flows beendet den Prozess. */
    fun install(requirements: List<RequirementContribution>): Flow<ToolRunEvent>

    /** Startet das Werkzeug im Projektverzeichnis [projectDir]; [input] ersetzt `${input}`. */
    fun run(pluginId: String, toolId: String, projectDir: String, input: String): Flow<ToolRunEvent>
}
