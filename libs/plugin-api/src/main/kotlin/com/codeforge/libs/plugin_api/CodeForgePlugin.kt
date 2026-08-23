/**
 * Modul: :libs:plugin-api
 * @author Thomas Schmid
 */
package com.codeforge.libs.plugin_api

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
}

interface PluginContext {
    val pluginId: String
}
