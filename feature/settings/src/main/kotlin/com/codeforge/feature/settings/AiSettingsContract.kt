/**
 * Modul: :feature:settings
 * @author Thomas Schmid
 */
package com.codeforge.feature.settings

import androidx.compose.runtime.Immutable
import com.codeforge.core.domain.ai.AiPrivacyPolicy
import com.codeforge.core.domain.ai.AiProviderType

/** Ergebnis eines Verbindungstests bzw. Modellabrufs; [Idle] = noch nichts geprüft. */
sealed interface AiCheckState {
    /** Noch nichts geprüft. */
    data object Idle : AiCheckState
    /** Die Prüfung läuft. */
    data object Running : AiCheckState
    /** Erfolg mit Detailtext. */
    data class Success(val detail: String) : AiCheckState
    /** Fehler mit Meldung. */
    data class Failure(val message: String) : AiCheckState
}

/** Bearbeitungsstand eines Anbieters; ungespeicherte Änderungen stehen in den Feldern, [saved] = zuletzt gespeicherter Stand. */
@Immutable
data class AiProviderState(
    val provider: AiProviderType,
    val apiKey: String = "",
    val model: String = provider.defaultModel,
    val baseUrl: String = provider.defaultBaseUrl,
    val keyVisible: Boolean = false,
    val dirty: Boolean = false,
    val test: AiCheckState = AiCheckState.Idle,
    val modelsFetch: AiCheckState = AiCheckState.Idle,
    /** Vorschläge (statische Liste, nach Abruf ergänzt um die Live-Liste des Anbieters). */
    val models: List<String> = provider.suggestedModels
) {
    /** Ob ein API-Schlüssel eingetragen ist. */
    val hasKey: Boolean get() = apiKey.isNotBlank()
    /** Ob die Basis-URL änderbar ist (nur OpenAI-kompatibel). */
    val baseUrlEditable: Boolean get() = provider == AiProviderType.OPENAI
}

/** Zustand der KI-Einstellungen: Anbieter, aktiver Anbieter, Datenschutz, Tageslimit und Verbrauch. */
@Immutable
data class AiSettingsUiState(
    val loading: Boolean = true,
    val providers: List<AiProviderState> = AiProviderType.entries.map { AiProviderState(it) },
    val activeProvider: AiProviderType = AiProviderType.OPENAI,
    val privacyPolicy: AiPrivacyPolicy = AiPrivacyPolicy.ONLY_SELECTED,
    val dailyLimit: Int = 50,
    val todayCount: Int = 0,
    val message: AiSettingsMessage? = null
)

/** Einmalige Hinweise der KI-Einstellungen. */
sealed interface AiSettingsMessage {
    /** Die Einstellungen eines Anbieters wurden gespeichert. */
    data class Saved(val provider: AiProviderType) : AiSettingsMessage
    /** Der aktive Anbieter wurde gewechselt. */
    data class ActiveChanged(val provider: AiProviderType) : AiSettingsMessage
}

/** Aktionen in den KI-Einstellungen. */
sealed interface AiSettingsUiEvent {
    /** Der API-Schlüssel wurde geändert. */
    data class ApiKeyChanged(val provider: AiProviderType, val value: String) : AiSettingsUiEvent
    /** Das Modell wurde geändert. */
    data class ModelChanged(val provider: AiProviderType, val value: String) : AiSettingsUiEvent
    /** Die Basis-URL wurde geändert. */
    data class BaseUrlChanged(val provider: AiProviderType, val value: String) : AiSettingsUiEvent
    /** Sichtbarkeit des Schlüssels umschalten. */
    data class ToggleKeyVisible(val provider: AiProviderType) : AiSettingsUiEvent
    /** Einstellungen des Anbieters speichern. */
    data class Save(val provider: AiProviderType) : AiSettingsUiEvent
    /** Verbindung testen. */
    data class TestConnection(val provider: AiProviderType) : AiSettingsUiEvent
    /** Modellliste vom Anbieter abrufen. */
    data class FetchModels(val provider: AiProviderType) : AiSettingsUiEvent
    /** Schlüssel löschen. */
    data class ClearKey(val provider: AiProviderType) : AiSettingsUiEvent
    /** Anbieter aktiv schalten. */
    data class SetActive(val provider: AiProviderType) : AiSettingsUiEvent
    /** Die Datenschutzrichtlinie wurde geändert. */
    data class PolicyChanged(val policy: AiPrivacyPolicy) : AiSettingsUiEvent
    /** Das Tageslimit wurde geändert. */
    data class DailyLimitChanged(val limit: Int) : AiSettingsUiEvent
    /** Der Hinweis wurde angezeigt. */
    data object MessageShown : AiSettingsUiEvent
}
