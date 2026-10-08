// Modul: :feature:onboarding
package com.codeforge.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codeforge.core.datastore.SettingsRepository
import com.codeforge.core.domain.repository.TermuxEnvironmentRepository
import com.codeforge.core.resources.R
import com.codeforge.core.resources.Res
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Ablauf der Einrichtung (kein PRoot/Rootfs mehr):
 * 1. Termux-Bootstrap entpacken (Effekt [OnboardingUiEffect.RunTermuxBootstrapSetup], braucht eine Activity).
 *    Der Bootstrap enthält u. a. JDK 17 und das Paket `codeforge-env`.
 * 2. Setup-Skript sicherstellen (falls nicht als Paket enthalten).
 * 3. Onboarding als abgeschlossen markieren und das Terminal mit `codeforge-env setup …` öffnen —
 *    dort installiert das Skript JDK, cmdline-tools, platform-tools, NDK und CMake sichtbar.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val termuxEnvironment: TermuxEnvironmentRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    private val _effect = Channel<OnboardingUiEffect>(Channel.BUFFERED)
    val effect = _effect.receiveAsFlow()

    fun onEvent(event: OnboardingUiEvent) {
        when (event) {
            is OnboardingUiEvent.IntroPageChanged ->
                _uiState.update { it.copy(currentIntroPage = event.page) }

            OnboardingUiEvent.IntroFinished -> {
                _uiState.update { it.copy(step = OnboardingStep.PERMISSIONS) }
                send(OnboardingUiEffect.RequestStoragePermission)
            }

            is OnboardingUiEvent.StoragePermissionResult ->
                _uiState.update { it.copy(storagePermissionGranted = event.granted) }

            is OnboardingUiEvent.NotificationPermissionResult ->
                _uiState.update { it.copy(notificationPermissionGranted = event.granted) }

            OnboardingUiEvent.PermissionsContinueClicked ->
                _uiState.update { it.copy(step = OnboardingStep.SETUP) }

            is OnboardingUiEvent.JdkSelected ->
                _uiState.update { it.copy(sdkOptions = it.sdkOptions.copy(jdk = event.version)) }
            is OnboardingUiEvent.NdkSelected ->
                _uiState.update { it.copy(sdkOptions = it.sdkOptions.copy(ndk = event.version)) }
            is OnboardingUiEvent.InstallNdkChanged ->
                _uiState.update { it.copy(sdkOptions = it.sdkOptions.copy(installNdk = event.install)) }
            is OnboardingUiEvent.InstallCmakeChanged ->
                _uiState.update { it.copy(sdkOptions = it.sdkOptions.copy(installCmake = event.install)) }

            OnboardingUiEvent.StartSetupClicked, OnboardingUiEvent.RetrySetupClicked -> startSetup()
            is OnboardingUiEvent.TermuxBootstrapSetupCompleted -> onBootstrapDone(event)
        }
    }

    private fun startSetup() {
        _uiState.update { it.copy(setupPhase = SetupPhase.INSTALLING_TERMUX, setupErrorMessage = null) }
        send(OnboardingUiEffect.RunTermuxBootstrapSetup)
    }

    private fun onBootstrapDone(event: OnboardingUiEvent.TermuxBootstrapSetupCompleted) {
        if (!event.success || !termuxEnvironment.isBootstrapInstalled()) {
            fail(event.errorMessage ?: Res.string(R.string.onboarding_termux_bootstrap_konnte_nicht_eingeric))
            return
        }
        _uiState.update { it.copy(setupPhase = SetupPhase.PREPARING_SCRIPT) }
        viewModelScope.launch {
            termuxEnvironment.installSdkScript()
                .onSuccess { completeOnboarding() }
                .onFailure { fail(it.message ?: Res.string(R.string.onboarding_setup_skript_konnte_nicht_bereitgestel)) }
        }
    }

    private suspend fun completeOnboarding() {
        _uiState.update { it.copy(setupPhase = SetupPhase.DONE) }
        settingsRepository.setOnboardingCompleted(true)
        _effect.send(OnboardingUiEffect.NavigateToSetupTerminal(_uiState.value.sdkOptions.toCommand()))
    }

    private fun fail(message: String) =
        _uiState.update { it.copy(setupPhase = SetupPhase.FAILED, setupErrorMessage = message) }

    private fun send(effect: OnboardingUiEffect) {
        viewModelScope.launch { _effect.send(effect) }
    }
}
