// Modul: :feature:onboarding
package com.codeforge.feature.onboarding

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import com.codeforge.app.TermuxInstaller
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.codeforge.core.resources.R
import com.codeforge.core.resources.Res
import kotlinx.coroutines.flow.collectLatest

@Composable
fun OnboardingRoute(
    modifier: Modifier = Modifier,
    /** [setupCommand]: im Terminal auszuführendes `codeforge-env setup …`. */
    onFinished: (setupCommand: String) -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

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

                OnboardingUiEffect.RunTermuxBootstrapSetup -> {
                    val activity = context.findActivity()
                    if (activity == null) {
                        viewModel.onEvent(OnboardingUiEvent.TermuxBootstrapSetupCompleted(false, Res.string(R.string.onboarding_keine_activity_verfuegbar)))
                    } else {
                        runCatching {
                            TermuxInstaller.setupBootstrapIfNeeded(activity) {
                                viewModel.onEvent(OnboardingUiEvent.TermuxBootstrapSetupCompleted(true))
                            }
                        }.onFailure {
                            viewModel.onEvent(OnboardingUiEvent.TermuxBootstrapSetupCompleted(false, it.message))
                        }
                    }
                }

                is OnboardingUiEffect.NavigateToSetupTerminal -> onFinished(effect.command)
            }
        }
    }

    // Edge-to-Edge: Schritte ohne eigenes Scaffold halten Abstand zu System-/Navigationsleiste, Cutout und IME.
    Box(modifier = modifier.fillMaxSize().safeDrawingPadding()) {
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
                options = uiState.sdkOptions,
                setupPhase = uiState.setupPhase,
                setupErrorMessage = uiState.setupErrorMessage,
                onJdkSelected = { viewModel.onEvent(OnboardingUiEvent.JdkSelected(it)) },
                onNdkSelected = { viewModel.onEvent(OnboardingUiEvent.NdkSelected(it)) },
                onInstallNdkChanged = { viewModel.onEvent(OnboardingUiEvent.InstallNdkChanged(it)) },
                onInstallCmakeChanged = { viewModel.onEvent(OnboardingUiEvent.InstallCmakeChanged(it)) },
                onStartSetup = { viewModel.onEvent(OnboardingUiEvent.StartSetupClicked) },
                onRetry = { viewModel.onEvent(OnboardingUiEvent.RetrySetupClicked) }
            )
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
