// Modul: :feature:onboarding
package com.codeforge.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

import com.codeforge.core.datastore.SettingsRepository
import com.codeforge.core.domain.repository.BootstrapProgress
import com.codeforge.core.domain.repository.DistroBootstrapRepository

import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val distroBootstrapRepository: DistroBootstrapRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    private val _effect = MutableSharedFlow<OnboardingUiEffect>()
    val effect: SharedFlow<OnboardingUiEffect> = _effect.asSharedFlow()

    init {
        viewModelScope.launch {
            settingsRepository.appSettings.collect { settings ->
                val distro = settings.terminal.defaultDistro.ifBlank { "ubuntu" }
                _uiState.update { state ->
                    if (state.selectedDistro == "ubuntu" && distro.isNotBlank()) {
                        state.copy(selectedDistro = distro)
                    } else state
                }
            }
        }
    }

    fun onEvent(event: OnboardingUiEvent) {
        when (event) {
            is OnboardingUiEvent.IntroPageChanged ->
                _uiState.update { it.copy(currentIntroPage = event.page) }

            OnboardingUiEvent.IntroFinished -> {
                _uiState.update { it.copy(step = OnboardingStep.PERMISSIONS) }
                emit(OnboardingUiEffect.RequestStoragePermission)
            }

            is OnboardingUiEvent.StoragePermissionResult ->
                _uiState.update { it.copy(storagePermissionGranted = event.granted) }

            is OnboardingUiEvent.NotificationPermissionResult ->
                _uiState.update { it.copy(notificationPermissionGranted = event.granted) }

            is OnboardingUiEvent.BatteryOptimizationResult ->
                _uiState.update { it.copy(batteryOptimizationGranted = event.granted) }

            is OnboardingUiEvent.WriteSecureSettingsResult ->
                _uiState.update { it.copy(writeSecureSettingsGranted = event.granted) }

            is OnboardingUiEvent.InstallPackagesResult ->
                _uiState.update { it.copy(installPackagesGranted = event.granted) }

            OnboardingUiEvent.PermissionsContinueClicked -> {
                completeOnboarding()
            }

            is OnboardingUiEvent.DistroSelected ->
                _uiState.update { it.copy(selectedDistro = "ubuntu") }

            OnboardingUiEvent.StartSetupClicked -> completeOnboarding()
            OnboardingUiEvent.RetrySetupClicked -> completeOnboarding()
        }
    }

    private fun completeOnboarding() = viewModelScope.launch {
        settingsRepository.updateTerminal { 
            it.toBuilder()
                .setDefaultDistro("ubuntu")
                .build() 
        }
        _uiState.update { it.copy(setupPhase = SetupPhase.DONE) }
        settingsRepository.setOnboardingCompleted(true)
        _effect.emit(OnboardingUiEffect.NavigateToWelcome)
    }

    private fun emit(effect: OnboardingUiEffect) = viewModelScope.launch { _effect.emit(effect) }
}
