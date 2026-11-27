/**
 * Modul: :libs:plugin-api
 *
 * Plugin-API 5: voller Zugriff auf CodeForge Mobile über [PluginContext.ide] – Befehle, Ereignisbus, App-Einstellungen,
 * Editor, Projektdateien, Git, Navigation und Statusleiste. Jeder Bereich hat eine Berechtigung (siehe
 * [IdePermissions]); ohne sie wirft der Aufruf [PluginPermissionException]. Alles, was ein Plugin anmeldet (Befehle,
 * Abos, Statuseinträge), wird beim Entladen automatisch entfernt. Eigene Oberflächen (Compose) bietet
 * `:libs:plugin-ui-api`.
 */
package com.codeforge.libs.plugin_api

import com.codeforge.core.domain.extension.ide.IdeCommandHandler
import com.codeforge.core.domain.extension.ide.IdeCommandInfo
import com.codeforge.core.domain.extension.ide.IdeEditorSnapshot
import com.codeforge.core.domain.extension.ide.IdeEvent
import com.codeforge.core.domain.extension.ide.IdeNavTarget
import com.codeforge.core.domain.extension.ide.IdeSettingInfo
import com.codeforge.core.domain.extension.ide.IdeTextEdit
import com.codeforge.core.domain.extension.PluginPermissions
import com.codeforge.core.domain.extension.registry.Disposable

/** Berechtigungen der API 5 (in `plugin.json` → `permissions`); Werte wie in `PluginPermissions`. */
object IdePermissions {
    const val COMMANDS_EXECUTE = PluginPermissions.COMMANDS_EXECUTE
    const val EVENTS = PluginPermissions.EVENTS
    const val SETTINGS_READ = PluginPermissions.SETTINGS_READ
    const val SETTINGS_WRITE = PluginPermissions.SETTINGS_WRITE
    const val FILES_READ = PluginPermissions.FILES_READ
    const val FILES_WRITE = PluginPermissions.FILES_WRITE
    const val GIT_READ = PluginPermissions.GIT_READ
    const val GIT_WRITE = PluginPermissions.GIT_WRITE
}

/** Einstieg in die API 5 (siehe [PluginContext.ide]). */
interface IdeApi {
    /** Befehle (`commands` zum Anmelden, `commands.execute` zum Ausführen). */
    val commands: IdeCommandsApi
    /** Ereignisbus (`events`). */
    val events: IdeEventsApi
    /** App-Einstellungen (`settings.read` / `settings.write`). */
    val settings: IdeSettingsApi
    /** Editor (`editor`). */
    val editor: IdeEditorApi
    /** Dateien des geöffneten Projekts (`files.read` / `files.write`). */
    val files: IdeFilesApi
    /** Git des geöffneten Projekts (`git.read` / `git.write`). */
    val git: IdeGitApi
    /** Bildschirme der App öffnen (`ui`). */
    val navigation: IdeNavigationApi
    /** Statusleiste unter dem Editor (`ui`). */
    val statusBar: IdeStatusBarApi
}

/** Befehle. IDs von CodeForge Mobile beginnen mit `cfm.`, die eines Plugins mit `<pluginId>.`. */
interface IdeCommandsApi {
    /** Alle Befehle (IDE und Plugins). */
    fun list(): List<IdeCommandInfo>

    /** Führt [id] aus und liefert das Ergebnis als Text; wirft bei Fehlern. */
    suspend fun execute(id: String, args: Map<String, String> = emptyMap()): String?

    /**
     * Meldet einen Befehl an; seine ID wird `<pluginId>.<id>`. Er erscheint in der Befehlspalette und lässt sich von
     * anderen Plugins und Menüs ausführen.
     */
    fun register(id: String, title: String, handler: IdeCommandHandler, category: String = "Plugin"): Disposable
}

/**
 * Ereignisbus. Themen der IDE siehe `IdeTopics` (`file.saved`, `git.committed`, `build.finished`,
 * `settings.changed` …); eigene Ereignisse erscheinen unter `plugin.<pluginId>.<name>`.
 */
interface IdeEventsApi {
    /** Ruft [listener] für Themen mit dem Präfix [topicPrefix] auf (leer = alle). */
    fun subscribe(topicPrefix: String, listener: (IdeEvent) -> Unit): Disposable

    /** Veröffentlicht `plugin.<pluginId>.<name>` mit [payload]. */
    fun publish(name: String, payload: Map<String, String> = emptyMap())
}

/** Ausgewählte App-Einstellungen in Punktschreibweise (`editor.fontSize`, `theme.mode` …), Werte als Text. */
interface IdeSettingsApi {
    /** Freigegebene Schlüssel mit Typ und Beschreibung. */
    fun keys(): List<IdeSettingInfo>

    /** Wert oder `null` bei unbekanntem Schlüssel. */
    suspend fun get(key: String): String?

    /** Setzt [key] (`settings.write`); `false` bei unbekanntem Schlüssel oder ungültigem Wert. */
    suspend fun set(key: String, value: String): Boolean

    /** Meldet jede Änderung (auch durch den Nutzer) mit dem Schlüssel. */
    fun onChange(listener: (key: String) -> Unit): Disposable
}

/** Editor: Zustand lesen, Dateien öffnen, Text ändern, speichern. Aufträge liefern `false`, wenn kein Editor offen ist. */
interface IdeEditorApi {
    /** Aktive Datei, Text, Cursor, Auswahl, offene Dateien. */
    fun snapshot(): IdeEditorSnapshot

    /** Öffnet [path] und setzt den Cursor auf [line]/[column] (0-basiert). */
    suspend fun open(path: String, line: Int = 0, column: Int = 0): Boolean

    /** Ändert die geöffnete Datei [path] (`null` = aktive Datei); Änderungen lassen sich rückgängig machen. */
    suspend fun applyEdits(path: String?, edits: List<IdeTextEdit>): Boolean

    /** Wählt [start] bis [end] in der aktiven Datei aus. */
    suspend fun select(start: Int, end: Int = start): Boolean

    /** Speichert [path] (`null` = alle ungespeicherten Dateien). */
    suspend fun save(path: String? = null): Boolean

    /** Schließt [path]. */
    suspend fun close(path: String): Boolean
}

/** Eintrag eines Verzeichnisses. */
data class IdeFileEntry(val path: String, val name: String, val isDirectory: Boolean, val size: Long, val modified: Long)

/**
 * Dateien des geöffneten Projekts. Pfade sind absolut oder relativ zum Projekt; Zugriffe außerhalb des Projekts
 * werden abgelehnt. Änderungen erscheinen sofort im Dateibaum und in offenen Editoren.
 */
interface IdeFilesApi {
    /** Wurzel des geöffneten Projekts oder `null`. */
    fun projectRoot(): String?
    /** Einträge von [dir] (leer = Projektwurzel), Ordner zuerst. */
    fun list(dir: String = ""): List<IdeFileEntry>
    /** Ob [path] existiert. */
    fun exists(path: String): Boolean
    /** Inhalt von [path] als Text (höchstens 8 MB). */
    fun read(path: String): String
    /** Schreibt [text] nach [path] (legt Ordner an). */
    suspend fun write(path: String, text: String)
    /** Legt den Ordner [path] an. */
    suspend fun mkdirs(path: String)
    /** Benennt [from] in [to] um (sichert offene Puffer vorher). */
    suspend fun rename(from: String, to: String)
    /** Löscht [path] (Ordner samt Inhalt). */
    suspend fun delete(path: String)
}

/** Git-Status des Projekts. */
data class IdeGitStatus(
    val branch: String?,
    val ahead: Int,
    val behind: Int,
    /** Geänderte Dateien: Pfad → Status (`MODIFIED`, `ADDED`, `DELETED`, `UNTRACKED`, `CONFLICTING` …). */
    val changes: Map<String, String>,
    val staged: List<String>
)

/** Git des geöffneten Projekts. */
interface IdeGitApi {
    /** Branch, ahead/behind, geänderte und vorgemerkte Dateien. */
    suspend fun status(): IdeGitStatus
    /** Unified Diff von [path] (`null` = alle), vorgemerkt oder Arbeitskopie. */
    suspend fun diff(path: String? = null, staged: Boolean = false): String
    /** Lokale Branches. */
    suspend fun branches(): List<String>
    /** Merkt [paths] zum Commit vor. */
    suspend fun stage(paths: List<String>)
    /** Committet die vorgemerkten Änderungen und liefert den Hash. */
    suspend fun commit(message: String): String
    /** Wechselt auf [branch]. */
    suspend fun checkout(branch: String)
}

/** Bildschirme öffnen (Einstellungen, Terminal mit Befehl, Git, Build-Panel, eigene Plugin-Bildschirme). */
interface IdeNavigationApi {
    /** Öffnet [target]. */
    fun navigate(target: IdeNavTarget)
}

/** Statusleiste: höchstens 5 Einträge je Plugin; Tippen führt [commandId] aus. */
interface IdeStatusBarApi {
    /** Setzt oder ersetzt den Eintrag [id] (höhere [priority] = weiter links). */
    fun set(id: String, text: String, tooltip: String? = null, commandId: String? = null, priority: Int = 0)
    /** Entfernt den Eintrag [id]. */
    fun remove(id: String)
}
