// Modul: :feature:onboarding
package com.codeforge.feature.onboarding

import androidx.compose.runtime.Immutable
import com.codeforge.core.domain.repository.SdkSetupOptions

/** Schritte der Einrichtung. */
enum class OnboardingStep { INTRO, PERMISSIONS, SETUP }

/** Einrichtung: Termux-Bootstrap entpacken → Setup-Skript bereitstellen → Terminal öffnet und führt es aus. */
enum class SetupPhase { IDLE, INSTALLING_TERMUX, PREPARING_SCRIPT, DONE, FAILED }

/** Zustand der Einrichtung (Schritt, Berechtigungen, Werkzeugwahl, Fortschritt). */
@Immutable
data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.INTRO,
    val introPageCount: Int = 4,
    val currentIntroPage: Int = 0,
    val storagePermissionGranted: Boolean = false,
    val installPackagesGranted: Boolean = false,
    val deletePackagesInfo: Boolean = true,
    val notificationPermissionGranted: Boolean = false,
    val sdkOptions: SdkSetupOptions = SdkSetupOptions(),
    val setupPhase: SetupPhase = SetupPhase.IDLE,
    val setupErrorMessage: String? = null
)

/** Aktionen der Einrichtung. */
sealed interface OnboardingUiEvent {
    /** Einführungsseite gewechselt. */
    data class IntroPageChanged(val page: Int) : OnboardingUiEvent
    /** Einführung beendet. */
    data object IntroFinished : OnboardingUiEvent
    /** Ergebnis der Speicherberechtigung. */
    data class StoragePermissionResult(val granted: Boolean) : OnboardingUiEvent
    /** Ergebnis der Berechtigung zum Installieren von Paketen. */
    data class InstallPackagesPermissionResult(val granted: Boolean) : OnboardingUiEvent
    /** Ergebnis der Benachrichtigungsberechtigung. */
    data class NotificationPermissionResult(val granted: Boolean) : OnboardingUiEvent
    /** Berechtigungen neu gelesen. */
    data class PermissionsRefreshed(
        val storageGranted: Boolean,
        val installPackagesGranted: Boolean,
        val notificationGranted: Boolean
    ) : OnboardingUiEvent
    /** Weiter nach den Berechtigungen. */
    data object PermissionsContinueClicked : OnboardingUiEvent
    /** JDK-Version gewählt. */
    data class JdkSelected(val version: String) : OnboardingUiEvent
    /** NDK-Version gewählt. */
    data class NdkSelected(val version: String) : OnboardingUiEvent
    /** NDK installieren ja/nein. */
    data class InstallNdkChanged(val install: Boolean) : OnboardingUiEvent
    /** CMake installieren ja/nein. */
    data class InstallCmakeChanged(val install: Boolean) : OnboardingUiEvent

    /** Von der UI-Schicht gemeldet, nachdem `TermuxInstaller.setupBootstrapIfNeeded` fertig ist. */
    data class TermuxBootstrapSetupCompleted(val success: Boolean, val errorMessage: String? = null) : OnboardingUiEvent
    /** Einrichtung starten. */
    data object StartSetupClicked : OnboardingUiEvent
    /** Einrichtung wiederholen. */
    data object RetrySetupClicked : OnboardingUiEvent
}

/** Einmalige Effekte. */
sealed interface OnboardingUiEffect {
    /** Speicherberechtigung anfordern. */
    data object RequestStoragePermission : OnboardingUiEffect
    /** Berechtigung zum Installieren von Paketen anfordern. */
    data object RequestInstallPackagesPermission : OnboardingUiEffect
    /** Benachrichtigungsberechtigung anfordern. */
    data object RequestNotificationPermission : OnboardingUiEffect

    /** Activity-abhängig: UI ruft `TermuxInstaller.setupBootstrapIfNeeded(activity, …)` auf. */
    data object RunTermuxBootstrapSetup : OnboardingUiEffect

    /** Onboarding fertig: Terminal öffnen und [command] (`codeforge-env setup …`) ausführen. */
    data class NavigateToSetupTerminal(val command: String) : OnboardingUiEffect
}
