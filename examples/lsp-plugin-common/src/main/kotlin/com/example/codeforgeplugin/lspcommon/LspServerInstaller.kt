/**
 * Modul: :examples:lsp-plugin-common
 * @author Thomas Schmid
 *
 * Wird per `implementation` (nicht compileOnly) in jedes Plugin-Dex mitgebündelt, da
 * dieses Modul — anders als :libs:plugin-api/:core:domain — nicht im Host-App-Prozess
 * vorhanden ist und daher nicht über Classloader-Parent-Delegation aufgelöst werden kann.
 */
package com.example.codeforgeplugin.lspcommon

import com.codeforge.libs.plugin_api.PluginHostServices
import com.codeforge.libs.terminal_engine.ShellLaunchSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ShellResult(val exitCode: Int, val output: String)

sealed interface InstallOutcome {
    data object Success : InstallOutcome
    data class Failed(val output: String) : InstallOutcome
}

/**
 * Installiert/deinstalliert einen Language Server in der Termux-Umgebung via
 * einfachem Shell-Skript (wget + tar). Nutzt java.lang.ProcessBuilder direkt — das ist
 * Standard-JDK-API, für die ein Plugin keine gesonderte Host-Freigabe braucht; die
 * Sandbox-Grenze liegt beim WELCHEN Kommando (nur via
 * PluginHostServices.buildShellCommand()), nicht beim
 * Prozessstart selbst.
 *
 * Setzt voraus, dass in Termux `wget` und `tar` verfügbar sind (`pkg install wget tar`;
 * `tar` ist im Bootstrap enthalten). Läuft im Termux-Prefix — kein PRoot, keine Distro-Auswahl.
 */
class LspServerInstaller(
    private val hostServices: PluginHostServices
) {
    suspend fun isInstalled(installDir: String, binaryRelativePath: String): Boolean =
        withContext(Dispatchers.IO) {
            val spec = hostServices.buildShellCommand(
                script = "test -x \"$installDir/$binaryRelativePath\""
            )
            runShellCommand(spec).exitCode == 0
        }

    /**
     * [downloadUrl] muss auf ein .tar.gz zeigen. [stripComponents] steuert, wie viele
     * führende Pfadebenen beim Entpacken entfernt werden — bei Archiven mit einem
     * wrappenden Versions-Verzeichnis (üblich bei GitHub-Releases) meist 1, bei
     * Archiven ohne Wrapper (z.B. Eclipse-JDT-LS-Snapshots) 0.
     */
    suspend fun install(installDir: String, downloadUrl: String, stripComponents: Int = 1): InstallOutcome =
        withContext(Dispatchers.IO) {
            val script = buildString {
                append("set -e; ")
                append("mkdir -p \"$installDir\"; ")
                append("cd \"$installDir\"; ")
                append("wget -qO- \"$downloadUrl\" | tar xz --strip-components=$stripComponents; ")
            }
            val spec = hostServices.buildShellCommand(
                script = script
            )
            val result = runShellCommand(spec)
            if (result.exitCode == 0) InstallOutcome.Success else InstallOutcome.Failed(result.output)
        }

    /**
     * Führt ein beliebiges Shell-Skript in Termux aus und liefert das
     * vollständige Ergebnis zurück — für Prüfungen, die nicht in das einfache
     * "test -x <fester Pfad>"-Muster von [isInstalled] passen (z.B. Versions-Glob wie
     * bei Eclipse JDT LS' launcher-jar-Dateinamen).
     */
    suspend fun runShellScript(script: String): ShellResult = withContext(Dispatchers.IO) {
        val spec = hostServices.buildShellCommand(
            script = script
        )
        runShellCommand(spec)
    }

    suspend fun uninstall(installDir: String): InstallOutcome =
        withContext(Dispatchers.IO) {
            val spec = hostServices.buildShellCommand(
                script = "rm -rf \"$installDir\""
            )
            val result = runShellCommand(spec)
            if (result.exitCode == 0) InstallOutcome.Success else InstallOutcome.Failed(result.output)
        }

    private fun runShellCommand(spec: ShellLaunchSpec): ShellResult = try {
        val processBuilder = ProcessBuilder(spec.command).directory(java.io.File(spec.workingDirectory)).redirectErrorStream(true)
        if (spec.processEnv.isNotEmpty()) {
            processBuilder.environment().putAll(spec.processEnv)
        }
        val process = processBuilder.start()
        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()
        ShellResult(exitCode, output)
    } catch (e: Exception) {
        ShellResult(-1, e.message ?: "Unbekannter Fehler beim Ausführen des Shell-Kommandos.")
    }
}
