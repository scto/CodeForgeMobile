/**
 * Modul: :core:domain
 * @author Thomas Schmid
 */
package com.codeforge.core.domain.extension

import kotlinx.coroutines.flow.Flow

/** Regeln für `contributes.requires`: gültige Namen und die Freigabeliste für den Store. */
object RequirementPolicy {
    /** Ordner der Download-Voraussetzungen relativ zum Termux-Präfix (Platzhalter `${deps}`). */
    const val DEPS_SUBDIR = "opt/plugin-deps"

    /** Datei im Zielordner eines Downloads mit der SHA-256 der installierten Version. */
    const val SHA_MARKER = ".codeforge-sha256"

    private val NAME = Regex("[A-Za-z0-9@][A-Za-z0-9@._/+-]*")

    // Pakete, die Store-Plugins anfordern dürfen (nur Programme, die CodeForge selbst als Tool-Plugins verwendet).
    private val STORE_ALLOWED: Map<RequirementManager, Set<String>> = mapOf(
        RequirementManager.PKG to setOf(
            "clang", "cmake", "nodejs", "python", "golang", "rust", "rust-analyzer", "ruff", "shfmt", "shellcheck",
            "taplo", "gopls", "dart", "neocmakelsp", "ktlint", "git", "jq", "ripgrep", "librsvg", "openjdk-17", "openjdk-21"
        ),
        RequirementManager.NPM to setOf(
            "prettier", "typescript", "typescript-language-server", "pyright", "bash-language-server",
            "vscode-langservers-extracted", "yaml-language-server"
        ),
        RequirementManager.PIP to emptySet()
    )

    // Hosts, von denen Store-Plugins Dateien laden dürfen (immer mit SHA-256-Prüfung).
    private val STORE_DOWNLOAD_HOSTS = setOf("github.com", "download.eclipse.org", "registry.npmjs.org")

    /** Verhindert Optionen (`-g`, `--foo`) und Pfadtricks (`..`) als Paketnamen. */
    fun isValidName(name: String): Boolean = NAME.matches(name) && !name.contains("..")

    /** Ob Store-Plugins das angeforderte Paket installieren lassen dürfen (Allowlist je Manager bzw. Download-Host). */
    fun isStoreAllowed(r: RequirementContribution): Boolean =
        if (r.manager == RequirementManager.DOWNLOAD) {
            hostOf(r.url) in STORE_DOWNLOAD_HOSTS
        } else {
            r.name in STORE_ALLOWED[r.manager].orEmpty()
        }

    private fun hostOf(url: String?): String? =
        url?.removePrefix("https://")?.substringBefore('/')?.substringBefore(':')?.lowercase()
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
    /** Eine Ausgabezeile. */
    data class Line(val text: String) : ToolRunEvent
    /** Ende des Laufs mit Exit-Code; [timedOut] bei Zeitüberschreitung. */
    data class Finished(val exitCode: Int, val timedOut: Boolean = false) : ToolRunEvent
}

/** Führt Plugin-Werkzeuge aus und installiert deren Voraussetzungen (nach Bestätigung durch den Nutzer). */
interface PluginToolRunner {
    /** Noch nicht vorhandene Voraussetzungen des Plugins [pluginId] (aktivierte Plugins). */
    suspend fun missingRequirements(pluginId: String): List<RequirementContribution>

    /** Installiert [requirements] nacheinander; bricht beim ersten Fehler ab. Abbruch des Flows beendet den Prozess. */
    fun install(requirements: List<RequirementContribution>): Flow<ToolRunEvent>

    /**
     * Löscht die Downloads (`requires` mit Manager `download`) des Plugins [pluginId], die kein anderes aktives Plugin
     * braucht. Vor dem Deinstallieren aufrufen (danach sind die Contributions des Plugins nicht mehr bekannt).
     */
    suspend fun releaseDownloads(pluginId: String) {}

    /** Startet das Werkzeug im Projektverzeichnis [projectDir]; [input] ersetzt `${input}`. */
    fun run(pluginId: String, toolId: String, projectDir: String, input: String): Flow<ToolRunEvent>
}
