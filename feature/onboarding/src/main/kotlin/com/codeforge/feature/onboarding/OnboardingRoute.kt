// Modul: :feature:onboarding
package com.codeforge.feature.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.flow.collectLatest

@Composable
fun OnboardingRoute(
    modifier: Modifier = Modifier,
    onFinished: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    val storagePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> viewModel.onEvent(OnboardingUiEvent.StoragePermissionResult(granted)) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> viewModel.onEvent(OnboardingUiEvent.NotificationPermissionResult(granted)) }

    LaunchedEffect(Unit) {
        viewModel.effect.collectLatest { effect ->
            when (effect) {
                OnboardingUiEffect.RequestStoragePermission ->
                    storagePermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)

                OnboardingUiEffect.RequestNotificationPermission ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }

                OnboardingUiEffect.NavigateToWelcome -> onFinished()
            }
        }
    }

    when (uiState.step) {
        OnboardingStep.INTRO -> IntroPagerScreen(
            currentPage = uiState.currentIntroPage,
            onPageChanged = { page -> viewModel.onEvent(OnboardingUiEvent.IntroPageChanged(page)) },
            onFinished = { viewModel.onEvent(OnboardingUiEvent.IntroFinished) }
        )

        OnboardingStep.PERMISSIONS -> PermissionScreen(
            storageGranted = uiState.storagePermissionGranted,
            notificationGranted = uiState.notificationPermissionGranted,
            onRequestStorage = { storagePermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE) },
            onRequestNotification = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    viewModel.onEvent(OnboardingUiEvent.NotificationPermissionResult(true))
                }
            },
            onContinue = { viewModel.onEvent(OnboardingUiEvent.PermissionsContinueClicked) }
        )

        OnboardingStep.SETUP -> SetupScreen(
            selectedDistro = uiState.selectedDistro,
            setupPhase = uiState.setupPhase,
            setupProgressPercent = uiState.setupProgressPercent,
            setupErrorMessage = uiState.setupErrorMessage,
            onDistroSelected = { distro -> viewModel.onEvent(OnboardingUiEvent.DistroSelected(distro)) },
            onStartSetup = { viewModel.onEvent(OnboardingUiEvent.StartSetupClicked) },
            onRetry = { viewModel.onEvent(OnboardingUiEvent.RetrySetupClicked) }
        )
    }
}
