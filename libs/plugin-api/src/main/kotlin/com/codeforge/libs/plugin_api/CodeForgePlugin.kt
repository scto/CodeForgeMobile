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
    /** Benötigt die Berechtigung `lsp` (bei `apiVersion` 1 ohne Deklaration erlaubt). */
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

    // --- Seit API v2 (je nach Berechtigung; ohne sie: PluginPermissionException) ---

    /** Registriert einen Befehl (Berechtigung `commands`). [id] ist pro Plugin eindeutig; erneutes Registrieren ersetzt ihn. */
    fun registerCommand(id: String, title: String, handler: PluginCommandHandler)

    /** Zeigt einen Befehl in einem App-Menü an (Berechtigung `menus`); [commandId] muss ein registrierter Befehl sein. */
    fun registerMenuItem(id: String, title: String, target: PluginMenuTarget, commandId: String)

    /** Hört auf Datei-Ereignisse (Berechtigung `file-events`) bzw. Git-Ereignisse (`git-events`); je Ereignisart wird die Berechtigung geprüft. */
    fun addEventListener(listener: PluginEventListener)

    /** Zeigt eine kurze Nachricht (max. 500 Zeichen) in der App an. Keine Berechtigung nötig. */
    fun showMessage(text: String)
}

/** Menüs, in denen Plugin-Befehle erscheinen können. */
enum class PluginMenuTarget { EDITOR_MENU, FILE_CONTEXT_MENU }

/** Kontext eines Befehlsaufrufs: aktive/ausgewählte Datei oder null. */
data class CommandArgs(val activeFilePath: String?)

fun interface PluginCommandHandler {
    suspend fun run(args: CommandArgs)
}

sealed interface PluginEvent {
    data class FileOpened(val path: String) : PluginEvent
    data class FileSaved(val path: String) : PluginEvent
    data class FileClosed(val path: String) : PluginEvent
    data class GitCommitted(val repoPath: String, val message: String) : PluginEvent
    data class GitBranchChanged(val repoPath: String, val branch: String) : PluginEvent
}

fun interface PluginEventListener {
    fun onEvent(event: PluginEvent)
}

/** Ein Plugin hat eine Funktion genutzt, für die es im Manifest keine Berechtigung angefordert hat. */
class PluginPermissionException(val pluginId: String, val permission: String) :
    SecurityException("Plugin $pluginId benötigt die Berechtigung '$permission' (plugin.json → permissions)")
