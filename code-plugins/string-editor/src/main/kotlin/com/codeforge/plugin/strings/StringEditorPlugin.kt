/**
 * Plugin: String-Editor (API 5, App-Prozess)
 *
 * Visueller Editor für `strings.xml`, portiert aus StringEditor (raredeveloperofc/msmt2018, GPL-3.0) nach
 * Kotlin/Jetpack Compose. Erscheint als Symbol in der Seitenleiste des Projekts (neben Layouts und Themes), dort die
 * Liste aller String-Dateien; Antippen öffnet den Editor als eigenen Bildschirm.
 */
package com.codeforge.plugin.strings

import com.codeforge.core.domain.extension.ide.IdeCommandHandler
import com.codeforge.core.domain.extension.ide.IdeNavTarget
import com.codeforge.libs.plugin_api.CodeForgePlugin
import com.codeforge.libs.plugin_api.PluginContext
import com.codeforge.libs.plugin_ui_api.PluginUiPoints
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class StringEditorPlugin : CodeForgePlugin {
    private var scope: CoroutineScope? = null

    override fun onLoad(context: PluginContext) {
        val s = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).also { scope = it }
        val ide = context.ide()
        val state = StringEditorState(context, ide, s)

        context.register(PluginUiPoints.SIDEBAR, StringsSidebar(state))
        context.register(PluginUiPoints.SCREEN, StringEditorScreen(state))

        // Dateibaum, Editor-Menü und Palette (Bedingung in plugin.json): Datei im String-Editor öffnen.
        ide.commands.register("open", "Im String-Editor öffnen", IdeCommandHandler { args ->
            val path = args["path"] ?: ide.editor.snapshot().path ?: return@IdeCommandHandler "Keine Datei"
            state.open(path)
            ide.navigation.navigate(IdeNavTarget.PluginScreen(context.pluginId, StringEditorScreen.ID))
            null
        }, "Strings")
        // Liste neu einlesen, wenn ein anderes Projekt geöffnet wird.
        ide.events.subscribe("project.opened") { state.scan() }
    }

    override fun onUnload() {
        scope?.cancel()
        scope = null
    }
}
