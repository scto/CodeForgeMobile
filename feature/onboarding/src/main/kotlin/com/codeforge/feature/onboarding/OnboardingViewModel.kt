// Modul: :feature:onboarding
package com.codeforge.feature.onboarding

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codeforge.core.datastore.SettingsRepository
import com.codeforge.core.domain.repository.SdkSetupOptions
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
    private val termuxEnvironment: TermuxEnvironmentRepository,
    // Überlebt den Prozesstod: Android beendet die App, wenn „Zugriff auf alle Dateien“ erteilt wird.
    private val savedState: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        OnboardingUiState(
            step = savedState.get<Boolean>(KEY_SETUP_STEP)?.let { if (it) OnboardingStep.SETUP else OnboardingStep.INTRO }
                ?: OnboardingStep.INTRO,
            currentIntroPage = savedState.get<Int>(KEY_INTRO_PAGE) ?: 0
        )
    )
    /** UI-Zustand. */
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    private val _effect = Channel<OnboardingUiEffect>(Channel.BUFFERED)
    /** Einmalige Effekte. */
    val effect = _effect.receiveAsFlow()

    /** Verarbeitet eine Aktion des Nutzers. */
    fun onEvent(event: OnboardingUiEvent) {
        when (event) {
            is OnboardingUiEvent.IntroPageChanged -> {
                savedState[KEY_INTRO_PAGE] = event.page
                _uiState.update { it.copy(currentIntroPage = event.page) }
            }

            is OnboardingUiEvent.StoragePermissionResult ->
                _uiState.update { it.copy(storagePermissionGranted = event.granted) }

            is OnboardingUiEvent.InstallPackagesPermissionResult ->
                _uiState.update { it.copy(installPackagesGranted = event.granted) }

            is OnboardingUiEvent.NotificationPermissionResult ->
                _uiState.update { it.copy(notificationPermissionGranted = event.granted) }

            is OnboardingUiEvent.PermissionsRefreshed ->
                _uiState.update {
                    it.copy(
                        storagePermissionGranted = event.storageGranted,
                        installPackagesGranted = event.installPackagesGranted,
                        notificationPermissionGranted = event.notificationGranted
                    )
                }

            OnboardingUiEvent.PermissionsContinueClicked ->
                if (_uiState.value.requiredPermissionsGranted) {
                    savedState[KEY_SETUP_STEP] = true
                    _uiState.update { it.copy(step = OnboardingStep.SETUP) }
                }

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
        _effect.send(OnboardingUiEffect.NavigateToSetupTerminal(SdkSetupOptions().toCommand()))
    }

    private fun fail(message: String) =
        _uiState.update { it.copy(setupPhase = SetupPhase.FAILED, setupErrorMessage = message) }

    private fun send(effect: OnboardingUiEffect) {
        viewModelScope.launch { _effect.send(effect) }
    }

    private companion object {
        const val KEY_INTRO_PAGE = "intro_page"
        const val KEY_SETUP_STEP = "setup_step"
    }
}
