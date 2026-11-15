/**
 * Modul: :feature:plugins
 * @author Thomas Schmid
 */
package com.codeforge.feature.plugins

import androidx.compose.runtime.Immutable
import com.codeforge.core.domain.extension.PluginIssue
import com.codeforge.core.domain.extension.PluginLogLine
import com.codeforge.core.domain.extension.PluginSettingItem
import com.codeforge.core.domain.extension.RequirementContribution
import com.codeforge.core.domain.model.InstalledPlugin
import com.codeforge.core.ui.LspInfoModel
import com.codeforge.core.ui.LspRuntimeInfo

/** Zustand der Plugin-Verwaltung. */
@Immutable
data class PluginsUiState(
    val plugins: List<InstalledPlugin> = emptyList(),
    val loadedPluginIds: Set<String> = emptySet(),
    /** Inkompatibilitäten je Plugin-ID (zu alte App, fehlende/deaktivierte Abhängigkeit); solche Plugins werden nicht geladen. */
    val issues: Map<String, List<PluginIssue>> = emptyMap(),
    val installProgressPercent: Int? = null,
    val isLoading: Boolean = true,
    /** Noch nicht installierte Programme je aktiviertem Plugin (`contributes.requires`). */
    val missingRequirements: Map<String, List<RequirementContribution>> = emptyMap(),
    val requirementDialog: RequirementDialog? = null,
    /** Offener Info-Dialog eines Language-Server-Plugins (Zustand/Laufzeit/Allgemein). */
    val infoDialog: PluginInfoDialog? = null,
    /** Offenes Protokoll eines Plugins. */
    val logDialog: PluginLogDialog? = null,
    /** Plugins mit Einstellungsseite (`contributes.settings`). */
    val pluginsWithSettings: Set<String> = emptySet(),
    /** Offene Einstellungsseite. */
    val settingsSheet: PluginSettingsSheet? = null
)

/** Einstellungsseite eines Plugins; [values] = wirksame Werte (Nutzer oder Vorgabe). */
@Immutable
data class PluginSettingsSheet(
    val pluginId: String,
    val title: String,
    val items: List<PluginSettingItem>,
    val values: Map<String, String>
)

/** Protokoll-Dialog eines Plugins (letzte Zeilen, älteste zuerst). */
@Immutable
data class PluginLogDialog(val pluginId: String, val title: String, val lines: List<PluginLogLine> = emptyList())

/** Info-Dialog zu einem Plugin. */
@Immutable
data class PluginInfoDialog(val pluginId: String, val model: LspInfoModel, val runtime: LspRuntimeInfo? = null)

/** Dialog zum Installieren fehlender Programme: erst Bestätigung, dann Live-Ausgabe. */
sealed interface RequirementDialog {
    /** ID des Plugins. */
    val pluginId: String

    /** Voraussetzungen bestätigen. */
    data class Confirm(override val pluginId: String, val missing: List<RequirementContribution>) : RequirementDialog
    /** Voraussetzungen werden installiert. */
    data class Running(
        override val pluginId: String,
        val lines: List<String> = emptyList(),
        val done: Boolean = false,
        val exitCode: Int? = null
    ) : RequirementDialog
}

/** Aktionen der Plugin-Verwaltung. */
sealed interface PluginsUiEvent {
    /** Plugin ein-/ausschalten. */
    data class ToggleEnabled(val pluginId: String, val enabled: Boolean) : PluginsUiEvent
    /** Plugin deinstallieren. */
    data class Uninstall(val pluginId: String) : PluginsUiEvent
    /** Plugin aus Archiv installieren. */
    data class InstallFromFile(val archivePath: String) : PluginsUiEvent
    /** Voraussetzungen anzeigen. */
    data class RequirementsClicked(val pluginId: String) : PluginsUiEvent
    /** Voraussetzungen bestätigt. */
    data object RequirementsConfirmed : PluginsUiEvent
    /** Voraussetzungen abgelehnt. */
    data object RequirementsDismissed : PluginsUiEvent
    /** Info anzeigen. */
    data class InfoClicked(val pluginId: String) : PluginsUiEvent
    /** Info schließen. */
    data object InfoDismissed : PluginsUiEvent
    /** Protokoll eines Plugins öffnen. */
    data class LogClicked(val pluginId: String) : PluginsUiEvent
    /** Protokoll schließen. */
    data object LogDismissed : PluginsUiEvent
    /** Protokoll des offenen Plugins leeren. */
    data object LogCleared : PluginsUiEvent
    /** Einstellungsseite öffnen. */
    data class SettingsClicked(val pluginId: String) : PluginsUiEvent
    /** Einstellungsseite schließen. */
    data object SettingsDismissed : PluginsUiEvent
    /** Wert geändert; `null` = Vorgabe wiederherstellen. */
    data class SettingChanged(val key: String, val value: String?) : PluginsUiEvent
}

/** Einmalige Effekte. */
sealed interface PluginsUiEffect {
    /** Meldung anzeigen. */
    data class ShowSnackbar(val message: String) : PluginsUiEffect
}
