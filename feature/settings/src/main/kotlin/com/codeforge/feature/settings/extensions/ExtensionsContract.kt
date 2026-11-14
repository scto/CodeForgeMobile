// Modul: :feature:settings
package com.codeforge.feature.settings.extensions

import androidx.compose.runtime.Immutable
import com.codeforge.core.domain.model.JvmOptions
import com.codeforge.core.domain.model.LspFeature
import com.codeforge.core.domain.model.LspServerSettings
import com.codeforge.core.resources.UiText
import com.codeforge.core.ui.LspInfoModel
import com.codeforge.core.ui.LspRuntimeInfo

/**
 * Ein Language Server aus einem Plugin. Server mit gleichem Einstellungs-Schlüssel ([key], z. B. HTML/CSS/JSON)
 * erscheinen als eine Zeile.
 */
@Immutable
data class LanguageServerItem(
    val key: String,
    val label: String,
    val languages: List<String>,
    val serverIds: Set<String>,
    val pluginId: String,
    val pluginName: String,
    val pluginVersion: String,
    /** Ob das Plugin JVM-Optionen vorsieht (Platzhalter `${'$'}{jvmArg}`/`${'$'}{jvmArgs}`). */
    val acceptsJvmOptions: Boolean,
    /** Noch nicht installierte Voraussetzungen des Plugins (Namen). */
    val missing: List<String> = emptyList()
)

/** Laufende Installation der Voraussetzungen: letzte Ausgabezeile und Fortschritt (falls bekannt). */
@Immutable
data class InstallProgressUi(val lastLine: String, val percent: Int?)

/** Zustand der Sprachserver-Verwaltung. */
@Immutable
data class ExtensionsUiState(
    val servers: List<LanguageServerItem> = emptyList(),
    val snackbarMessage: UiText? = null,
    /** Schlüssel des Servers, dessen Info-Dialog offen ist, und sein Modell. */
    val infoKey: String? = null,
    val infoModel: LspInfoModel? = null,
    /** Live-Laufzeitdaten des Servers im Info-Dialog; `null` = läuft nicht. */
    val infoRuntime: LspRuntimeInfo? = null,
    /** Einstellungen je Server (an/aus, Fähigkeiten, JVM-Optionen). */
    val serverSettings: Map<String, LspServerSettings> = emptyMap(),
    /** Laufende Installationen von Voraussetzungen je Plugin-ID. */
    val installing: Map<String, InstallProgressUi> = emptyMap(),
    /** Größe einer alten Installation aus dem früheren Erweiterungs-Manager in Bytes; 0 = keine. */
    val legacyBytes: Long = 0L
)

/** Einstellungen eines Servers (Standard, wenn nichts gespeichert ist). */
fun ExtensionsUiState.settingsOf(key: String): LspServerSettings = serverSettings[key] ?: LspServerSettings()

/** Aktionen in der Sprachserver-Verwaltung. */
sealed interface ExtensionsUiEvent {
    /** Server ein- oder ausschalten. */
    data class SetServerEnabled(val key: String, val enabled: Boolean) : ExtensionsUiEvent
    /** Einzelne Fähigkeit eines Servers schalten. */
    data class SetFeature(val key: String, val feature: LspFeature, val enabled: Boolean) : ExtensionsUiEvent
    /** JVM-Optionen speichern; `null` setzt auf die Vorgabe zurück. */
    data class SaveJvm(val key: String, val options: JvmOptions?) : ExtensionsUiEvent
    /** Fehlende Voraussetzungen des Plugins installieren. */
    data class InstallRequirements(val pluginId: String) : ExtensionsUiEvent
    /** Info-Dialog öffnen. */
    data class OpenInfo(val key: String) : ExtensionsUiEvent
    /** Info-Dialog schließen. */
    data object CloseInfo : ExtensionsUiEvent
    /** Alte Installation des früheren Erweiterungs-Managers löschen. */
    data object DeleteLegacy : ExtensionsUiEvent
    /** Meldung ausblenden. */
    data object DismissSnackbar : ExtensionsUiEvent
}
