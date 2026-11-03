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
    data object Idle : AiCheckState
    data object Running : AiCheckState
    data class Success(val detail: String) : AiCheckState
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
    val hasKey: Boolean get() = apiKey.isNotBlank()
    val baseUrlEditable: Boolean get() = provider == AiProviderType.OPENAI
}

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

sealed interface AiSettingsMessage {
    data class Saved(val provider: AiProviderType) : AiSettingsMessage
    data class ActiveChanged(val provider: AiProviderType) : AiSettingsMessage
}

sealed interface AiSettingsUiEvent {
    data class ApiKeyChanged(val provider: AiProviderType, val value: String) : AiSettingsUiEvent
    data class ModelChanged(val provider: AiProviderType, val value: String) : AiSettingsUiEvent
    data class BaseUrlChanged(val provider: AiProviderType, val value: String) : AiSettingsUiEvent
    data class ToggleKeyVisible(val provider: AiProviderType) : AiSettingsUiEvent
    data class Save(val provider: AiProviderType) : AiSettingsUiEvent
    data class TestConnection(val provider: AiProviderType) : AiSettingsUiEvent
    data class FetchModels(val provider: AiProviderType) : AiSettingsUiEvent
    data class ClearKey(val provider: AiProviderType) : AiSettingsUiEvent
    data class SetActive(val provider: AiProviderType) : AiSettingsUiEvent
    data class PolicyChanged(val policy: AiPrivacyPolicy) : AiSettingsUiEvent
    data class DailyLimitChanged(val limit: Int) : AiSettingsUiEvent
    data object MessageShown : AiSettingsUiEvent
}
