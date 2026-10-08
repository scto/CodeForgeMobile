/**
 * Modul: :examples:kotlin-lsp-plugin
 * @author Thomas Schmid
 *
 * Referenz-Plugin für die CodeForge-Plugin-API (:libs:plugin-api). Installiert bei
 * Bedarf selbstständig den Kotlin Language Server (kotlin-language-server,
 * https://github.com/fwcd/kotlin-language-server) innerhalb der per
 * :libs:terminal-engine Termux-Umgebung, startet ihn und bindet ihn an
 * LspClientRepository an. Wird das Plugin vollständig deinstalliert (nicht nur
 * deaktiviert), wird der Server auch aus Termux entfernt.
 *
 * TODO: DOWNLOAD_URL zeigt auf eine konkrete Release-Version — wie bei DistroCatalog
 * (:libs:terminal-engine) regelmäßig gegen die tatsächlich aktuellen Releases prüfen.
 *
 * TODO (siehe PluginHostServices-KDoc zu ActiveFileBridge): distro/workspaceRoot sind
 * hier hart codiert. Sobald Plugins eigene Konfiguration persistieren können und ein
 * allgemeiner ActiveFileBridge-Mechanismus existiert, sollte dieses Plugin (a) den
 * Workspace aus dem gerade aktiven Projekt lesen statt einen festen Pfad anzunehmen,
 * und (b) den Server erst starten, wenn tatsächlich eine .kt-Datei geöffnet wird.
 *
 * JDK-ABHÄNGIGKEIT (jetzt GESCHLOSSEN statt nur dokumentiert): kotlin-language-server
 * ist selbst ein JVM-Programm. Dieses Plugin installiert das JDK nicht selbst, prüft
 * aber vor dem Start explizit, ob REQUIRED_JDK_VERSION bereits über :feature:sdkmanager
 * (TermuxScriptSdkRepository, :libs:terminal-engine) installiert wurde, und
 * setzt dessen bin-Verzeichnis explizit in PATH (via buildShellCommand's
 * extraPathEntries) statt sich auf ein zufällig vorhandenes "java" zu verlassen. Fehlt
 * das JDK, bricht der Start mit einer klaren, handlungsanleitenden Fehlermeldung ab
 * statt stillschweigend zu scheitern.
 */
package com.example.codeforgeplugin.kotlinlsp

import com.codeforge.libs.plugin_api.CodeForgePlugin
import com.codeforge.libs.plugin_api.PluginContext
import com.example.codeforgeplugin.lspcommon.InstallOutcome
import com.example.codeforgeplugin.lspcommon.LspServerInstaller
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class KotlinLspPlugin : CodeForgePlugin {

    private val pluginScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var activeContext: PluginContext? = null

    override fun onLoad(context: PluginContext) {
        activeContext = context
        pluginScope.launch { installIfNeededAndStart(context) }
    }

    private suspend fun installIfNeededAndStart(context: PluginContext) {
        val installer = LspServerInstaller(context.hostServices)

        val javaBinary = "${context.hostServices.jdkInstallPath(REQUIRED_JDK_VERSION)}/bin/java"
        val javaCheck = installer.runShellScript("test -x \"$javaBinary\"")
        if (javaCheck.exitCode != 0) {
            log(
                "JDK $REQUIRED_JDK_VERSION nicht gefunden ($javaBinary fehlt). " +
                    "Bitte zuerst über Einstellungen -> SDK Manager -> JDK $REQUIRED_JDK_VERSION installieren.",
                isError = true
            )
            return
        }

        if (!installer.isInstalled(INSTALL_DIR, "bin/kotlin-language-server")) {
            log("Kotlin Language Server nicht gefunden, installiere von $DOWNLOAD_URL ...")
            when (val outcome = installer.install(INSTALL_DIR, DOWNLOAD_URL)) {
                is InstallOutcome.Failed -> {
                    log("Installation fehlgeschlagen: ${outcome.output}", isError = true)
                    return
                }
                InstallOutcome.Success -> log("Installation abgeschlossen.")
            }
        }

        startLanguageServer(context)
    }

    private suspend fun startLanguageServer(context: PluginContext) {
        val jdkBinDir = "${context.hostServices.jdkInstallPath(REQUIRED_JDK_VERSION)}/bin"

        val workspaceRoot = "${context.hostServices.homeDir}/project"
        val launchSpec = context.hostServices.buildShellCommand(
            script = "mkdir -p $DEFAULT_WORKSPACE_ROOT && exec \"$INSTALL_DIR/bin/kotlin-language-server\"",
            extraPathEntries = listOf(jdkBinDir)
        )

        context.hostServices.lspClientRepository
            .start(
                serverCommand = launchSpec.command,
                workspaceRootPath = workspaceRoot,
                extraProcessEnv = launchSpec.processEnv
            )
            .onFailure { throwable -> log("Start fehlgeschlagen: ${throwable.message}", isError = true) }
    }

    override fun onUnload() {
        val context = activeContext
        pluginScope.launch {
            context?.hostServices?.lspClientRepository?.stop()
        }
        pluginScope.cancel()
        activeContext = null
    }

    override suspend fun onUninstall(context: PluginContext) {
        val installer = LspServerInstaller(context.hostServices)
        when (val outcome = installer.uninstall(INSTALL_DIR)) {
            is InstallOutcome.Failed -> log("Deinstallation fehlgeschlagen: ${outcome.output}", isError = true)
            InstallOutcome.Success -> log("Kotlin Language Server aus Termux entfernt.")
        }
        // Das JDK wird bewusst NICHT mitdeinstalliert — es ist eine eigenständig über
        // den SDK Manager verwaltete Ressource, die auch von anderen Plugins/Werkzeugen
        // (z.B. dem Java-LSP-Plugin) genutzt werden kann.
    }

    private fun log(message: String, isError: Boolean = false) {
        if (isError) android.util.Log.e(TAG, message) else android.util.Log.i(TAG, message)
    }

    private companion object {
        const val TAG = "KotlinLspPlugin"
        /** Shell-Ausdruck ($HOME wird von der Termux-Shell expandiert). */
        const val DEFAULT_WORKSPACE_ROOT = "\$HOME/project"
        const val INSTALL_DIR = "\$HOME/.local/share/kotlin-language-server"
        const val DOWNLOAD_URL = "https://github.com/fwcd/kotlin-language-server/releases/download/1.3.9/server.tar.gz"
        const val REQUIRED_JDK_VERSION = "17"
    }
}
