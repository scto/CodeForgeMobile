/**
 * Modul: :templates:code-plugin
 * @author Thomas Schmid
 *
 * Vorlage für ein Code-Plugin (API v2): ein Befehl, ein Dateibaum-Menüeintrag und ein Ereignis-Listener.
 * Benötigte Berechtigungen stehen in plugin.json (`commands`, `menus`, `file-events`).
 */
package com.example.codeforgeplugin.template

import com.codeforge.libs.plugin_api.CodeForgePlugin
import com.codeforge.libs.plugin_api.PluginContext
import com.codeforge.libs.plugin_api.PluginEvent
import com.codeforge.libs.plugin_api.PluginMenuTarget
import java.io.File

class TemplatePlugin : CodeForgePlugin {

    override fun onLoad(context: PluginContext) {
        val host = context.hostServices

        host.registerCommand("count-lines", "Zeilen zählen") { args ->
            val path = args.activeFilePath
            if (path == null) {
                host.showMessage("Keine Datei ausgewählt")
            } else {
                val lines = File(path).useLines { it.count() }
                host.showMessage("${File(path).name}: $lines Zeilen")
            }
        }
        host.registerMenuItem("count-lines-menu", "Zeilen zählen", PluginMenuTarget.FILE_CONTEXT_MENU, "count-lines")

        host.addEventListener { event ->
            if (event is PluginEvent.FileSaved) host.showMessage("Gespeichert: ${File(event.path).name}")
        }
    }

    override fun onUnload() = Unit
}
