/**
 * Modul: :libs:plugin-ui-api
 *
 * Seit API 5: Werkzeugfenster, Bildschirme und Einstellungsseiten von Plugins (Compose, Material 3, App-Theme).
 * Angemeldet über `context.register(PluginUiPoints.…, …)` mit der Berechtigung `ui`. Wie bei [PluginPanel] gilt:
 * Ausnahmen in `Content` beenden die App – Arbeit gehört in Coroutinen (`LaunchedEffect`).
 */
package com.codeforge.libs.plugin_ui_api

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.codeforge.core.domain.extension.PluginPermissions
import com.codeforge.core.domain.extension.registry.ExtensionPoint

/** Was eine Plugin-Oberfläche der API 5 vom Host bekommt. Für alles Weitere: `context.ide()` aus `onLoad`. */
interface PluginUiScope {
    /** Plugin-ID des Besitzers. */
    val pluginId: String

    /** Schließt das Fenster bzw. verlässt den Bildschirm. */
    fun close()
}

/**
 * Werkzeugfenster im Projekt: erscheint unter „Werkzeugfenster“ im Menü des Arbeitsbereichs und öffnet sich als
 * Blatt von unten (öffnen auch per `ide.navigation.navigate(IdeNavTarget.PluginToolWindow(...))`).
 */
interface PluginToolWindow {
    /** Eindeutig je Plugin. */
    val id: String
    val title: String

    /** Inhalt; [scope] schließt das Fenster bzw. verlässt den Bildschirm. */
    @Composable
    fun Content(scope: PluginUiScope, modifier: Modifier)
}

/** Vollbild eines Plugins mit Kopfzeile und Zurück (öffnen per `IdeNavTarget.PluginScreen`). */
interface PluginScreen {
    /** Eindeutig je Plugin. */
    val id: String
    val title: String

    /** Inhalt; [scope] schließt das Fenster bzw. verlässt den Bildschirm. */
    @Composable
    fun Content(scope: PluginUiScope, modifier: Modifier)
}

/**
 * Einstellungsseite eines Plugins unter Einstellungen → Plugin-Einstellungen. Eine je Plugin; ersetzt dort die aus
 * `contributes.settings` erzeugte Seite.
 */
interface PluginSettingsPage {
    val title: String

    /** Kurzbeschreibung unter dem Titel in der Übersicht. */
    val summary: String? get() = null

    /** Inhalt; [scope] schließt das Fenster bzw. verlässt den Bildschirm. */
    @Composable
    fun Content(scope: PluginUiScope, modifier: Modifier)
}

/** Was eine Seitenleisten-Ansicht zusätzlich bekommt. [close] schließt die Seitenleiste. */
interface PluginSidebarScope : PluginUiScope {
    /** Wurzel des geöffneten Projekts. */
    val projectRoot: String

    /** Schließt die Seitenleiste und öffnet den Bildschirm [screenId] dieses Plugins ([PluginUiPoints.SCREEN]). */
    fun openScreen(screenId: String)
}

/**
 * Seit App 3.6.1: Ansicht in der Seitenleiste des Projekts. Das [icon] erscheint in der Symbolleiste oben neben
 * Dateien, Git, Layouts, Themes …; Antippen zeigt [Content] darunter (wie die Layout-Liste). Für größere Arbeit
 * öffnet die Ansicht einen eigenen Bildschirm ([PluginSidebarScope.openScreen]).
 */
interface PluginSidebarView {
    /** Eindeutig je Plugin. */
    val id: String

    /** Beschriftung unter dem Symbol (kurz halten, ca. 10 Zeichen). */
    val title: String

    /** Symbol in der Leiste, z. B. `Icons.Filled.Translate` (nur gefüllte Material-Symbole sind im Release sicher). */
    val icon: ImageVector

    /** Inhalt der Seitenleiste unter der Symbolleiste. */
    @Composable
    fun Content(scope: PluginSidebarScope, modifier: Modifier)
}

/** Andockstellen der API 5 für Oberflächen (alle mit Berechtigung `ui`). */
object PluginUiPoints {
    val TOOL_WINDOW: ExtensionPoint<PluginToolWindow> = ExtensionPoint("ui.toolWindow", PluginPermissions.UI)
    val SCREEN: ExtensionPoint<PluginScreen> = ExtensionPoint("ui.screen", PluginPermissions.UI)
    val SETTINGS_PAGE: ExtensionPoint<PluginSettingsPage> = ExtensionPoint("ui.settingsPage", PluginPermissions.UI)

    /** Seit App 3.6.1: Symbol und Ansicht in der Seitenleiste des Projekts. */
    val SIDEBAR: ExtensionPoint<PluginSidebarView> = ExtensionPoint("ui.sidebar", PluginPermissions.UI)
}
