package com.codeforge.feature.themebuilder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codeforge.core.datastore.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ThemeBuilderViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ThemeBuilderUiState())
    val uiState: StateFlow<ThemeBuilderUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() = viewModelScope.launch {
        _uiState.update { it.copy(isLoading = true) }
        val settings = settingsRepository.appSettings.first()
        val theme = settings.theme
        _uiState.update {
            it.copy(
                isLoading = false,
                mode = theme.mode,
                useDynamicColor = theme.useDynamicColor,
                useAmoled = theme.useAmoled
            )
        }
    }

    fun onEvent(event: ThemeBuilderUiEvent) {
        when (event) {
            is ThemeBuilderUiEvent.ModeSelected -> {
                _uiState.update { it.copy(mode = event.mode) }
                viewModelScope.launch {
                    settingsRepository.updateTheme { current ->
                        current.toBuilder().setMode(event.mode).build()
                    }
                }
            }
            is ThemeBuilderUiEvent.DynamicColorToggled -> {
                val newDynamic = !_uiState.value.useDynamicColor
                _uiState.update { it.copy(useDynamicColor = newDynamic) }
                viewModelScope.launch {
                    settingsRepository.updateTheme { current ->
                        current.toBuilder().setUseDynamicColor(newDynamic).build()
                    }
                }
            }
            is ThemeBuilderUiEvent.AmoledToggled -> {
                val newAmoled = !_uiState.value.useAmoled
                _uiState.update { it.copy(useAmoled = newAmoled) }
                viewModelScope.launch {
                    settingsRepository.updateTheme { current ->
                        current.toBuilder().setUseAmoled(newAmoled).build()
                    }
                }
            }
        }
    }
}
