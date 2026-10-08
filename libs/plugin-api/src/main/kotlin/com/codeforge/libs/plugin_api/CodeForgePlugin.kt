/**
 * Modul: :libs:plugin-api
 * @author Thomas Schmid
 */
package com.codeforge.libs.plugin_api

import com.codeforge.core.domain.repository.LspClientRepository
import com.codeforge.libs.terminal_engine.ShellLaunchSpec

/**
 * Öffentliche SDK-Schnittstelle für Plugins. Plugin-Entwickler kompilieren gegen dieses
 * Interface (als separates SDK-Artefakt zu veröffentlichen — hier noch Teil der App).
 * Die entryPointClass im plugin.json-Manifest muss eine Klasse benennen, die dieses
 * Interface implementiert und einen No-Arg-Konstruktor besitzt.
 *
 * WICHTIG für Plugin-Autoren: Die kompilierte Klasse muss als classes.dex (via `d8`
 * aus den .class-Dateien erzeugt) im Archiv liegen, nicht als reine .jar mit
 * Java-Bytecode — DexClassLoader lädt nur Dex-Code, keine rohen .class-Dateien.
 */
interface CodeForgePlugin {
    fun onLoad(context: PluginContext)
    fun onUnload()

    /**
     * Wird aufgerufen, wenn der Nutzer das Plugin vollständig entfernt (nicht bei
     * bloßem Deaktivieren via Switch — dafür ist onUnload() zuständig). Standard-
     * implementierung ist ein No-Op, da die meisten Plugins keine externen Ressourcen
     * (z.B. einen in der Rootfs installierten Language Server) aufräumen müssen.
     * suspend, damit z.B. ein `rm -rf` innerhalb der Rootfs sauber abgewartet werden
     * kann, bevor PluginRepositoryImpl das Plugin-Verzeichnis von der Registry entfernt.
     */
    suspend fun onUninstall(context: PluginContext) {}
}

interface PluginContext {
    val pluginId: String
    val hostServices: PluginHostServices
}

/**
 * Ausgewählte Host-Funktionalität, die Plugins sicher nutzen dürfen — bewusst eine
 * kleine, explizite Facade statt vollem App-Zugriff (Sandbox-Prinzip: ein Plugin sieht
 * nur das, was hier freigegeben ist).
 *
 * TODO (v2): Für reaktive Plugins (z.B. "starte LSP-Server, sobald eine .kt-Datei aktiv
 * wird") fehlt noch ein generalisierter Aktive-Datei-Bridge-Mechanismus. Aktuell existiert
 * mit ActiveComposablePreviewBridge (:core:navigation) nur eine Compose-spezifische
 * Variante. Ein allgemeiner "ActiveFileBridge" wäre die naheliegende Erweiterung, damit
 * Plugins wie das Kotlin-LSP-Beispiel automatisch statt nur beim Laden reagieren können.
 */
interface PluginHostServices {
    val lspClientRepository: LspClientRepository
    /** Home-Verzeichnis der Termux-Umgebung (`$HOME`); sinnvoller Arbeits-/Installationsort für Plugins. */
    val homeDir: String

    /**
     * Baut ein Kommando, das [script] in der Termux-Umgebung ausführt (`bash -c`, Umgebung
     * mit PREFIX/HOME/PATH/LD_LIBRARY_PATH, siehe TermuxEnvironment in :libs:terminal-engine).
     * Das enthaltene processEnv MUSS auf `ProcessBuilder(spec.command).environment()` angewendet
     * werden, [ShellLaunchSpec.workingDirectory] als `directory(...)`.
     *
     * [extraPathEntries] werden dem PATH vorangestellt (z. B. das bin-Verzeichnis eines JDK).
     */
    fun buildShellCommand(
        script: String,
        workingDirectory: String = homeDir,
        extraPathEntries: List<String> = emptyList()
    ): ShellLaunchSpec

    /**
     * Pfad des Termux-JDK (`$PREFIX/lib/jvm/java-<version>-openjdk`) — rein deterministisch,
     * KEIN Existenz-Check. Installiert wird es über den SDK-Manager bzw. `codeforge-env`.
     */
    fun jdkInstallPath(version: String): String
}
