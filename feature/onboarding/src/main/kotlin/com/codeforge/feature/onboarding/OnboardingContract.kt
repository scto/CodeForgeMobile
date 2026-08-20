// Modul: :feature:onboarding
package com.codeforge.feature.onboarding

import androidx.compose.runtime.Immutable

enum class OnboardingStep { INTRO, PERMISSIONS, SETUP }

enum class SetupPhase { IDLE, DOWNLOADING, EXTRACTING, FINALIZING, DONE, FAILED }

@Immutable
data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.INTRO,
    val introPageCount: Int = 4,
    val currentIntroPage: Int = 0,
    val storagePermissionGranted: Boolean = false,
    val notificationPermissionGranted: Boolean = false,
    val selectedDistro: String = "alpine",
    val setupPhase: SetupPhase = SetupPhase.IDLE,
    val setupProgressPercent: Int = 0,
    val setupErrorMessage: String? = null
)

sealed interface OnboardingUiEvent {
    data class IntroPageChanged(val page: Int) : OnboardingUiEvent
    data object IntroFinished : OnboardingUiEvent
    data class StoragePermissionResult(val granted: Boolean) : OnboardingUiEvent
    data class NotificationPermissionResult(val granted: Boolean) : OnboardingUiEvent
    data object PermissionsContinueClicked : OnboardingUiEvent
    data class DistroSelected(val distro: String) : OnboardingUiEvent
    data object StartSetupClicked : OnboardingUiEvent
    data object RetrySetupClicked : OnboardingUiEvent
}

sealed interface OnboardingUiEffect {
    data object RequestStoragePermission : OnboardingUiEffect
    data object RequestNotificationPermission : OnboardingUiEffect
    data object NavigateToWelcome : OnboardingUiEffect
}
