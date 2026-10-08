// Modul: :feature:onboarding
package com.codeforge.feature.onboarding

import androidx.compose.runtime.Immutable
import com.codeforge.core.domain.repository.SdkSetupOptions

enum class OnboardingStep { INTRO, PERMISSIONS, SETUP }

/** Einrichtung: Termux-Bootstrap entpacken → Setup-Skript bereitstellen → Terminal öffnet und führt es aus. */
enum class SetupPhase { IDLE, INSTALLING_TERMUX, PREPARING_SCRIPT, DONE, FAILED }

@Immutable
data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.INTRO,
    val introPageCount: Int = 4,
    val currentIntroPage: Int = 0,
    val storagePermissionGranted: Boolean = false,
    val notificationPermissionGranted: Boolean = false,
    val sdkOptions: SdkSetupOptions = SdkSetupOptions(),
    val setupPhase: SetupPhase = SetupPhase.IDLE,
    val setupErrorMessage: String? = null
)

sealed interface OnboardingUiEvent {
    data class IntroPageChanged(val page: Int) : OnboardingUiEvent
    data object IntroFinished : OnboardingUiEvent
    data class StoragePermissionResult(val granted: Boolean) : OnboardingUiEvent
    data class NotificationPermissionResult(val granted: Boolean) : OnboardingUiEvent
    data object PermissionsContinueClicked : OnboardingUiEvent
    data class JdkSelected(val version: String) : OnboardingUiEvent
    data class NdkSelected(val version: String) : OnboardingUiEvent
    data class InstallNdkChanged(val install: Boolean) : OnboardingUiEvent
    data class InstallCmakeChanged(val install: Boolean) : OnboardingUiEvent

    /** Von der UI-Schicht gemeldet, nachdem `TermuxInstaller.setupBootstrapIfNeeded` fertig ist. */
    data class TermuxBootstrapSetupCompleted(val success: Boolean, val errorMessage: String? = null) : OnboardingUiEvent
    data object StartSetupClicked : OnboardingUiEvent
    data object RetrySetupClicked : OnboardingUiEvent
}

sealed interface OnboardingUiEffect {
    data object RequestStoragePermission : OnboardingUiEffect
    data object RequestNotificationPermission : OnboardingUiEffect

    /** Activity-abhängig: UI ruft `TermuxInstaller.setupBootstrapIfNeeded(activity, …)` auf. */
    data object RunTermuxBootstrapSetup : OnboardingUiEffect

    /** Onboarding fertig: Terminal öffnen und [command] (`codeforge-env setup …`) ausführen. */
    data class NavigateToSetupTerminal(val command: String) : OnboardingUiEffect
}
