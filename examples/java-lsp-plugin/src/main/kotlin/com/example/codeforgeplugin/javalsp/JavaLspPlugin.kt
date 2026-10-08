/**
 * Modul: :examples:java-lsp-plugin
 * @author Thomas Schmid
 *
 * Referenz-Plugin für die CodeForge-Plugin-API (:libs:plugin-api). Installiert bei
 * Bedarf selbstständig den Eclipse JDT Language Server (eclipse.jdt.ls,
 * https://github.com/eclipse-jdtls/eclipse.jdt.ls) innerhalb der per
 * :libs:terminal-engine Termux-Umgebung, startet ihn und bindet ihn an
 * LspClientRepository an. Wird das Plugin vollständig deinstalliert, wird der Server
 * auch aus Termux entfernt.
 *
 * BESONDERHEIT gegenüber dem Kotlin-LSP-Plugin: JDT LS liefert kein einfaches
 * bin/<name>-Startskript, sondern muss über einen versionsabhängig benannten
 * Launcher-JAR gestartet werden (org.eclipse.equinox.launcher_<version>.jar). Die
 * Installationsprüfung und der Startbefehl nutzen daher eine Shell-Glob-Auflösung
 * statt eines festen Pfads.
 *
 * TODO: DOWNLOAD_URL zeigt auf den "latest snapshot"-Endpunkt, den das JDT-LS-Projekt
 * offiziell bereitstellt — im Gegensatz zu versionsgepinnten Release-URLs ändert sich
 * der Inhalt hinter dieser URL laufend. Für reproduzierbare Installationen wäre eine
 * versionsgepinnte URL vorzuziehen; regelmäßig prüfen (analog zu DistroCatalog in
 * :libs:terminal-engine).
 *
 * JDK-ABHÄNGIGKEIT (jetzt GESCHLOSSEN statt nur dokumentiert): wie kotlin-language-server
 * ist auch JDT LS ein JVM-Programm. Dieses Plugin prüft vor dem Start explizit, ob
 * REQUIRED_JDK_VERSION bereits über :feature:sdkmanager installiert wurde, und setzt
 * dessen bin-Verzeichnis explizit in PATH statt sich auf ein zufällig vorhandenes
 * "java" zu verlassen (siehe KotlinLspPlugin-KDoc für den identischen Mechanismus).
 */
package com.example.codeforgeplugin.javalsp

import com.codeforge.libs.plugin_api.CodeForgePlugin
import com.codeforge.libs.plugin_api.PluginContext
import com.example.codeforgeplugin.lspcommon.InstallOutcome
import com.example.codeforgeplugin.lspcommon.LspServerInstaller
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class JavaLspPlugin : CodeForgePlugin {

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

        if (!isLauncherJarPresent(installer)) {
            log("Eclipse JDT Language Server nicht gefunden, installiere von $DOWNLOAD_URL ...")
            when (val outcome = installer.install(INSTALL_DIR, DOWNLOAD_URL, stripComponents = 0)) {
                is InstallOutcome.Failed -> {
                    log("Installation fehlgeschlagen: ${outcome.output}", isError = true)
                    return
                }
                InstallOutcome.Success -> log("Installation abgeschlossen.")
            }
        }

        startLanguageServer(context, installer)
    }

    private suspend fun isLauncherJarPresent(installer: LspServerInstaller): Boolean {
        val result = installer.runShellScript(
            "ls $INSTALL_DIR/plugins/org.eclipse.equinox.launcher_*.jar >/dev/null 2>&1"
        )
        return result.exitCode == 0
    }

    private suspend fun startLanguageServer(context: PluginContext, installer: LspServerInstaller) {
        val javaBinary = "${context.hostServices.jdkInstallPath(REQUIRED_JDK_VERSION)}/bin/java"

        val startScript = "\"$javaBinary\" -jar \"\$(ls $INSTALL_DIR/plugins/org.eclipse.equinox.launcher_*.jar | head -n1)\" " +
            "-configuration $INSTALL_DIR/config_linux " +
            "-data $DEFAULT_WORKSPACE_ROOT/.jdt-workspace"

        val workspaceRoot = "${context.hostServices.homeDir}/project"
        val launchSpec = context.hostServices.buildShellCommand(
            script = "mkdir -p $DEFAULT_WORKSPACE_ROOT && exec $startScript"
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
            InstallOutcome.Success -> log("Eclipse JDT Language Server aus Termux entfernt.")
        }
        // Das JDK wird bewusst NICHT mitdeinstalliert (siehe KotlinLspPlugin-KDoc).
    }

    private fun log(message: String, isError: Boolean = false) {
        if (isError) android.util.Log.e(TAG, message) else android.util.Log.i(TAG, message)
    }

    private companion object {
        const val TAG = "JavaLspPlugin"
        /** Shell-Ausdruck ($HOME wird von der Termux-Shell expandiert). */
        const val DEFAULT_WORKSPACE_ROOT = "\$HOME/project"
        const val INSTALL_DIR = "\$HOME/.local/share/jdt-language-server"
        const val DOWNLOAD_URL = "https://download.eclipse.org/jdtls/snapshots/jdt-language-server-latest.tar.gz"
        const val REQUIRED_JDK_VERSION = "17"
    }
}
