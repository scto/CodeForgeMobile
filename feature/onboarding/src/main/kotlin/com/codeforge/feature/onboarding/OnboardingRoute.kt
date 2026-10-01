package com.codeforge.feature.onboarding

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.flow.collectLatest

@Composable
fun OnboardingRoute(
    modifier: Modifier = Modifier,
    onFinished: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val genericActivityResultLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        checkPermissions(context, viewModel)
    }

    val legacyStorageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> viewModel.onEvent(OnboardingUiEvent.StoragePermissionResult(granted)) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> viewModel.onEvent(OnboardingUiEvent.NotificationPermissionResult(granted)) }

    fun requestStorage() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                genericActivityResultLauncher.launch(intent)
            } catch (e: Exception) {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                genericActivityResultLauncher.launch(intent)
            }
        } else {
            legacyStorageLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    fun requestBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                genericActivityResultLauncher.launch(intent)
            } catch (e: Exception) {
                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                genericActivityResultLauncher.launch(intent)
            }
        }
    }

    fun requestWriteSecureSettings() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            genericActivityResultLauncher.launch(intent)
        } catch (e: Exception) {
            checkPermissions(context, viewModel)
        }
    }

    fun requestInstallPackages() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                genericActivityResultLauncher.launch(intent)
            } catch (e: Exception) {
                checkPermissions(context, viewModel)
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                checkPermissions(context, viewModel)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        checkPermissions(context, viewModel)
        viewModel.effect.collectLatest { effect ->
            when (effect) {
                OnboardingUiEffect.RequestStoragePermission -> requestStorage()

                OnboardingUiEffect.RequestNotificationPermission ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }

                OnboardingUiEffect.RequestBatteryOptimization -> requestBatteryOptimization()

                OnboardingUiEffect.RequestWriteSecureSettings -> requestWriteSecureSettings()

                OnboardingUiEffect.RequestInstallPackages -> requestInstallPackages()

                OnboardingUiEffect.NavigateToWelcome -> onFinished()
            }
        }
    }

    Scaffold(modifier = modifier) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (uiState.step) {
                OnboardingStep.INTRO -> IntroPagerScreen(
                    currentPage = uiState.currentIntroPage,
                    onPageChanged = { page -> viewModel.onEvent(OnboardingUiEvent.IntroPageChanged(page)) },
                    onFinished = { viewModel.onEvent(OnboardingUiEvent.IntroFinished) }
                )

                OnboardingStep.PERMISSIONS -> PermissionScreen(
                    storageGranted = uiState.storagePermissionGranted,
                    notificationGranted = uiState.notificationPermissionGranted,
                    batteryOptimizationGranted = uiState.batteryOptimizationGranted,
                    writeSecureSettingsGranted = uiState.writeSecureSettingsGranted,
                    installPackagesGranted = uiState.installPackagesGranted,
                    onRequestStorage = { requestStorage() },
                    onRequestNotification = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            viewModel.onEvent(OnboardingUiEvent.NotificationPermissionResult(true))
                        }
                    },
                    onRequestBatteryOptimization = { requestBatteryOptimization() },
                    onRequestWriteSecureSettings = { requestWriteSecureSettings() },
                    onRequestInstallPackages = { requestInstallPackages() },
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
    }
}

private fun checkPermissions(context: android.content.Context, viewModel: OnboardingViewModel) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        viewModel.onEvent(OnboardingUiEvent.StoragePermissionResult(Environment.isExternalStorageManager()))
    } else {
        val granted = androidx.core.content.ContextCompat.checkSelfPermission(
            context, Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        viewModel.onEvent(OnboardingUiEvent.StoragePermissionResult(granted))
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val granted = androidx.core.content.ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        viewModel.onEvent(OnboardingUiEvent.NotificationPermissionResult(granted))
    } else {
        viewModel.onEvent(OnboardingUiEvent.NotificationPermissionResult(true))
    }

    val powerManager = context.getSystemService(android.content.Context.POWER_SERVICE) as? android.os.PowerManager
    val batteryIgnored = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && powerManager != null) {
        powerManager.isIgnoringBatteryOptimizations(context.packageName)
    } else {
        true
    }
    viewModel.onEvent(OnboardingUiEvent.BatteryOptimizationResult(batteryIgnored))

    val secureGranted = androidx.core.content.ContextCompat.checkSelfPermission(
        context, "android.permission.WRITE_SECURE_SETTINGS"
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    viewModel.onEvent(OnboardingUiEvent.WriteSecureSettingsResult(secureGranted))

    val installGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.packageManager.canRequestPackageInstalls()
    } else {
        true
    }
    viewModel.onEvent(OnboardingUiEvent.InstallPackagesResult(installGranted))
}
