/**
 * Modul: :feature:editor
 * @author Thomas Schmid
 */
package com.codeforge.feature.editor.plugin

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.codeforge.libs.plugin_ui_api.PanelHost
import com.codeforge.libs.plugin_ui_api.PluginPanel
import com.codeforge.libs.plugin_ui_api.PluginPreview
import com.codeforge.libs.plugin_ui_api.PreviewFile

/**
 * Zeigt eine [PluginPreview] im selben Blatt wie Plugin-Bereiche. [text] liefert den aktuellen Editor-Inhalt der
 * Datei (`null` = nicht im Editor geöffnet, z. B. Binärdateien).
 */
internal class PreviewAsPanel(
    private val preview: PluginPreview,
    private val path: String,
    private val text: () -> String?
) : PluginPanel {
    override val id: String get() = "preview:${preview.id}"
    override val title: String get() = preview.title

    @Composable
    override fun Content(host: PanelHost, modifier: Modifier) {
        preview.Content(PreviewFile(path, text()), host, modifier)
    }
}
