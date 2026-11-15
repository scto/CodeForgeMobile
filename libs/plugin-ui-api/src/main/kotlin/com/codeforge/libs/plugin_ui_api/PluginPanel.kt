/**
 * Modul: :libs:plugin-ui-api
 * @author Thomas Schmid
 */
package com.codeforge.libs.plugin_ui_api

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.codeforge.core.domain.extension.PluginPermissions
import com.codeforge.core.domain.extension.registry.ExtensionPoint

/**
 * Seit API 3: eigene Oberfläche eines Plugins, angezeigt als Bereich im Editor (Befehlspalette → Titel). Der Inhalt ist
 * normales Compose mit Material 3; Farben und Typografie kommen aus dem App-Theme.
 *
 * Achtung: Compose kennt keine Fehlergrenzen – eine Ausnahme in [Content] beendet die App. Arbeit gehört in
 * Coroutinen (`LaunchedEffect`), nicht in die Komposition.
 */
interface PluginPanel {
    /** Eindeutig je Plugin. */
    val id: String

    /** Titel (Palette, Kopf des Bereichs). */
    val title: String

    /** Inhalt des Bereichs; [host] verbindet mit dem Editor. */
    @Composable
    fun Content(host: PanelHost, modifier: Modifier)
}

/** Seit API 3: Was ein [PluginPanel] vom Editor sieht und auslösen kann. */
interface PanelHost {
    /** Absoluter Pfad der aktiven Datei oder `null`. */
    val activeFilePath: String?

    /** Projektordner oder `null`. */
    val projectRoot: String?

    /** Öffnet eine Datei im Editor. */
    fun openFile(path: String)

    /** Kurze Meldung anzeigen. */
    fun showMessage(text: String)

    /** Schließt den Bereich. */
    fun close()
}

/** Seit API 3: Andockstellen für Plugin-Oberflächen. */
object PluginUiExtensionPoints {
    /** Bereiche im Editor. Berechtigung `ui`. */
    val PANEL: ExtensionPoint<PluginPanel> = ExtensionPoint("ui.panel", PluginPermissions.UI)
}
