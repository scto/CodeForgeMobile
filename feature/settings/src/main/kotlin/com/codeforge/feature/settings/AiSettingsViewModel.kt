/**
 * Modul: :feature:settings
 * @author Thomas Schmid
 */
package com.codeforge.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codeforge.core.domain.ai.AiChatOptions
import com.codeforge.core.domain.ai.AiProvider
import com.codeforge.core.domain.ai.AiProviderType
import com.codeforge.core.domain.repository.AiRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class AiSettingsViewModel @Inject constructor(
    private val repository: AiRepository,
    private val aiProvider: AiProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow(AiSettingsUiState())
    val uiState: StateFlow<AiSettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val providers = AiProviderType.entries.map { type ->
                val o = repository.getProviderOptions(type)
                AiProviderState(
                    provider = type,
                    apiKey = o.apiKey,
                    model = o.model,
                    baseUrl = o.baseUrl ?: type.defaultBaseUrl,
                    // Gespeichertes, nicht vorgeschlagenes Modell bleibt auswählbar.
                    models = (listOf(o.model) + type.suggestedModels).distinct()
                )
            }
            _uiState.update {
                it.copy(
                    loading = false,
                    providers = providers,
                    activeProvider = repository.getAiOptions().first().provider,
                    privacyPolicy = repository.getPrivacyPolicy().first(),
                    dailyLimit = repository.getDailyLimit().first(),
                    todayCount = repository.getTodayRequestCount().first()
                )
            }
        }
    }

    fun onEvent(event: AiSettingsUiEvent) {
        when (event) {
            is AiSettingsUiEvent.ApiKeyChanged -> edit(event.provider) {
                it.copy(apiKey = event.value.trim(), test = AiCheckState.Idle)
            }
            is AiSettingsUiEvent.ModelChanged -> edit(event.provider) { it.copy(model = event.value, test = AiCheckState.Idle) }
            is AiSettingsUiEvent.BaseUrlChanged -> edit(event.provider) { it.copy(baseUrl = event.value, test = AiCheckState.Idle) }
            is AiSettingsUiEvent.ToggleKeyVisible -> update(event.provider) { it.copy(keyVisible = !it.keyVisible) }
            is AiSettingsUiEvent.ClearKey -> viewModelScope.launch {
                edit(event.provider) { it.copy(apiKey = "", test = AiCheckState.Idle) }
                save(event.provider, silent = true)
            }
            is AiSettingsUiEvent.Save -> viewModelScope.launch { save(event.provider, silent = false) }
            is AiSettingsUiEvent.TestConnection -> testConnection(event.provider)
            is AiSettingsUiEvent.FetchModels -> fetchModels(event.provider)
            is AiSettingsUiEvent.SetActive -> viewModelScope.launch {
                save(event.provider, silent = true)
                repository.saveAiOptions(options(event.provider))
                _uiState.update { it.copy(activeProvider = event.provider, message = AiSettingsMessage.ActiveChanged(event.provider)) }
            }
            is AiSettingsUiEvent.PolicyChanged -> viewModelScope.launch {
                repository.savePrivacyPolicy(event.policy)
                _uiState.update { it.copy(privacyPolicy = event.policy) }
            }
            is AiSettingsUiEvent.DailyLimitChanged -> viewModelScope.launch {
                val limit = event.limit.coerceIn(0, MAX_DAILY_LIMIT)
                repository.saveDailyLimit(limit)
                _uiState.update { it.copy(dailyLimit = limit) }
            }
            AiSettingsUiEvent.MessageShown -> _uiState.update { it.copy(message = null) }
        }
    }

    private fun current(provider: AiProviderType): AiProviderState =
        _uiState.value.providers.first { it.provider == provider }

    private fun options(provider: AiProviderType): AiChatOptions = current(provider).let {
        AiChatOptions(
            provider = provider,
            model = it.model.trim().ifBlank { provider.defaultModel },
            baseUrl = it.baseUrl.trim().ifBlank { provider.defaultBaseUrl },
            apiKey = it.apiKey.trim()
        )
    }

    private fun update(provider: AiProviderType, transform: (AiProviderState) -> AiProviderState) {
        _uiState.update { state ->
            state.copy(providers = state.providers.map { if (it.provider == provider) transform(it) else it })
        }
    }

    private fun edit(provider: AiProviderType, transform: (AiProviderState) -> AiProviderState) =
        update(provider) { transform(it).copy(dirty = true) }

    private suspend fun save(provider: AiProviderType, silent: Boolean) {
        repository.saveProviderOptions(options(provider))
        update(provider) { it.copy(dirty = false) }
        if (!silent) _uiState.update { it.copy(message = AiSettingsMessage.Saved(provider)) }
    }

    private fun testConnection(provider: AiProviderType) {
        update(provider) { it.copy(test = AiCheckState.Running) }
        viewModelScope.launch {
            val result = runCatching { aiProvider.testConnection(options(provider)).getOrThrow() }
            update(provider) {
                it.copy(
                    test = result.fold(
                        onSuccess = { detail -> AiCheckState.Success(detail) },
                        onFailure = { e -> AiCheckState.Failure(e.message ?: e.javaClass.simpleName) }
                    )
                )
            }
        }
    }

    private fun fetchModels(provider: AiProviderType) {
        update(provider) { it.copy(modelsFetch = AiCheckState.Running) }
        viewModelScope.launch {
            val result = runCatching { aiProvider.fetchAvailableModels(options(provider)).getOrThrow() }
            update(provider) { st ->
                result.fold(
                    onSuccess = { live ->
                        // Live-Modelle zuerst, bekannte Vorschläge dahinter (z. B. noch nicht gelistete Vorabmodelle).
                        val merged = (live + st.models + provider.suggestedModels).distinct()
                        st.copy(models = merged, modelsFetch = AiCheckState.Success(live.size.toString()))
                    },
                    onFailure = { e -> st.copy(modelsFetch = AiCheckState.Failure(e.message ?: e.javaClass.simpleName)) }
                )
            }
        }
    }

    private companion object {
        const val MAX_DAILY_LIMIT = 100_000
    }
}
