/**
 * Modul: :examples:lsp-plugin-common
 * @author Thomas Schmid
 */
package com.example.codeforgeplugin.lspcommon

import com.codeforge.libs.plugin_api.CodeForgePlugin
import com.codeforge.libs.plugin_api.PluginContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Download eines Servers: entweder ein `.tar.gz` (entpackt) oder eine einzelne Datei ([fileName]). */
data class ServerDownload(
    val url: String,
    /** Nur für `.tar.gz`: Anzahl führender Pfadebenen, die beim Entpacken entfallen. */
    val stripComponents: Int = 1,
    /** Gesetzt = Einzeldatei (z. B. JAR) wird unter diesem Namen in [LspServerSpec.installDir] abgelegt. */
    val fileName: String? = null
)

/**
 * Beschreibt, was ein Plugin VOR dem Start seines Servers sicherstellen muss. Der Start selbst
 * erfolgt NICHT im Plugin, sondern deklarativ über `contributes.lspServers` in `plugin.json`
 * (LspServerResolver der App) — dadurch gilt immer das aktive Projekt als Workspace.
 *
 * @property installedCheck Shell-Skript; Exit-Code 0 = Server ist einsatzbereit.
 * @property installDir Shell-Ausdruck (`$HOME` wird von der Termux-Shell expandiert); null = nichts selbst installiert.
 * @property requiredJdk JDK-Hauptversion (z. B. "17"), die für den Server vorhanden sein muss; null = keine JVM nötig.
 * @property installScript Shell-Skript, das den Server selbst baut/installiert (z. B. `cargo install`), wenn kein [download] möglich ist.
 * @property missingHint Hinweis für den Nutzer, wenn nichts heruntergeladen werden kann ([download] = null) und der Server fehlt.
 */
data class LspServerSpec(
    val tag: String,
    val displayName: String,
    val installedCheck: String,
    val installDir: String? = null,
    val download: ServerDownload? = null,
    val requiredJdk: String? = null,
    val installScript: String? = null,
    val missingHint: String? = null
)

/**
 * Gemeinsame Basis aller LSP-Plugins: JDK-Prüfung, Installation, Meldung an den Nutzer, Deinstallation.
 * Benötigt im Manifest die Berechtigung `shell` (apiVersion 2).
 */
abstract class LspServerPlugin(private val spec: LspServerSpec) : CodeForgePlugin {

    private val pluginScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onLoad(context: PluginContext) {
        pluginScope.launch { ensureReady(context) }
    }

    override fun onUnload() {
        pluginScope.cancel()
    }

    override suspend fun onUninstall(context: PluginContext) {
        val dir = spec.installDir ?: return
        when (val outcome = LspServerInstaller(context.hostServices).uninstall(dir)) {
            is InstallOutcome.Failed -> report(context, "${spec.displayName}: Entfernen fehlgeschlagen: ${outcome.output}", true)
            InstallOutcome.Success -> report(context, "${spec.displayName} entfernt.")
        }
        // Das JDK bleibt bewusst installiert: es wird vom SDK-Manager verwaltet und von anderen Plugins genutzt.
    }

    private suspend fun ensureReady(context: PluginContext) {
        val installer = LspServerInstaller(context.hostServices)

        spec.requiredJdk?.let { version ->
            val java = "${context.hostServices.jdkInstallPath(version)}/bin/java"
            if (installer.runShellScript("test -x \"$java\"").exitCode != 0) {
                report(
                    context,
                    "${spec.displayName}: JDK $version fehlt. Bitte unter Einstellungen → SDK-Manager installieren.",
                    true
                )
                return
            }
        }

        if (installer.runShellScript(spec.installedCheck).exitCode == 0) return

        val script = spec.installScript
        if (script != null) {
            report(context, "${spec.displayName} wird gebaut/installiert (kann einige Minuten dauern) …")
            val result = installer.runShellScript(script)
            val ok = result.exitCode == 0 && installer.runShellScript(spec.installedCheck).exitCode == 0
            if (ok) {
                report(context, "${spec.displayName} installiert.")
            } else {
                report(context, "${spec.displayName}: Installation fehlgeschlagen: ${result.output.takeLast(MAX_OUTPUT_TAIL)}", true)
            }
            return
        }

        val download = spec.download
        val dir = spec.installDir
        if (download == null || dir == null) {
            report(context, spec.missingHint ?: "${spec.displayName} ist nicht installiert.", true)
            return
        }

        report(context, "${spec.displayName} wird installiert …")
        val outcome = if (download.fileName != null) {
            installer.installFile(dir, download.url, download.fileName)
        } else {
            installer.install(dir, download.url, download.stripComponents)
        }
        when (outcome) {
            is InstallOutcome.Failed -> report(context, "${spec.displayName}: Installation fehlgeschlagen: ${outcome.output}", true)
            InstallOutcome.Success ->
                if (installer.runShellScript(spec.installedCheck).exitCode == 0) {
                    report(context, "${spec.displayName} installiert.")
                } else {
                    report(context, "${spec.displayName}: Archiv entpackt, aber Startdatei fehlt.", true)
                }
        }
    }

    private fun report(context: PluginContext, message: String, isError: Boolean = false) {
        if (isError) System.err.println("[${spec.tag}] $message") else println("[${spec.tag}] $message")
        runCatching { context.hostServices.showMessage(message.take(MAX_MESSAGE)) }
    }

    private companion object {
        const val MAX_MESSAGE = 500
        const val MAX_OUTPUT_TAIL = 400
    }
}
