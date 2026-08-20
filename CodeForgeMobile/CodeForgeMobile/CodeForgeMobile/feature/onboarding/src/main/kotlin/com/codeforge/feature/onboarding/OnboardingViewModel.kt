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

            OnboardingUiEvent.PermissionsContinueClicked ->
                _uiState.update { it.copy(step = OnboardingStep.SETUP) }

            is OnboardingUiEvent.DistroSelected ->
                _uiState.update { it.copy(selectedDistro = event.distro) }

            OnboardingUiEvent.StartSetupClicked -> runSetup()
            OnboardingUiEvent.RetrySetupClicked -> runSetup()
        }
    }

    private fun runSetup() {
        val distro = _uiState.value.selectedDistro
        _uiState.update { it.copy(setupPhase = SetupPhase.DOWNLOADING, setupErrorMessage = null) }

        distroBootstrapRepository.bootstrap(distro)
            .onEach { progress -> reduceBootstrapProgress(progress) }
            .catch { throwable ->
                _uiState.update {
                    it.copy(setupPhase = SetupPhase.FAILED, setupErrorMessage = throwable.message)
                }
            }
            .launchIn(viewModelScope)
    }

    private fun reduceBootstrapProgress(progress: BootstrapProgress) {
        when (progress) {
            is BootstrapProgress.Downloading ->
                _uiState.update { it.copy(setupPhase = SetupPhase.DOWNLOADING, setupProgressPercent = progress.percent) }

            is BootstrapProgress.Extracting ->
                _uiState.update { it.copy(setupPhase = SetupPhase.EXTRACTING, setupProgressPercent = progress.percent) }

            BootstrapProgress.Finalizing ->
                _uiState.update { it.copy(setupPhase = SetupPhase.FINALIZING, setupProgressPercent = 100) }

            BootstrapProgress.Completed -> completeOnboarding()

            is BootstrapProgress.Failed ->
                _uiState.update { it.copy(setupPhase = SetupPhase.FAILED, setupErrorMessage = progress.message) }
        }
    }

    private fun completeOnboarding() = viewModelScope.launch {
        _uiState.update { it.copy(setupPhase = SetupPhase.DONE) }
        settingsRepository.setOnboardingCompleted(true)
        _effect.emit(OnboardingUiEffect.NavigateToWelcome)
    }

    private fun emit(effect: OnboardingUiEffect) = viewModelScope.launch { _effect.emit(effect) }
}
