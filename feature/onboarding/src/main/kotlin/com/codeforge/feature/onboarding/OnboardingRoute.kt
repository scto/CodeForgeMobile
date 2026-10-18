// Modul: :feature:onboarding
package com.codeforge.feature.onboarding

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.codeforge.app.TermuxInstaller
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
    val lifecycleOwner = LocalLifecycleOwner.current

    fun refreshPermissions() {
        viewModel.onEvent(
            OnboardingUiEvent.PermissionsRefreshed(
                storageGranted = context.isStoragePermissionGranted(),
                installPackagesGranted = context.packageManager.canRequestPackageInstalls(),
                notificationGranted = context.isNotificationPermissionGranted()
            )
        )
    }

    DisposableEffect(lifecycleOwner, context) {
        refreshPermissions()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val legacyStoragePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results.values.all { it } && results.isNotEmpty()
        viewModel.onEvent(OnboardingUiEvent.StoragePermissionResult(granted || context.isStoragePermissionGranted()))
    }

    val settingsStorageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.onEvent(OnboardingUiEvent.StoragePermissionResult(context.isStoragePermissionGranted()))
    }

    val installPackagesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.onEvent(
            OnboardingUiEvent.InstallPackagesPermissionResult(context.packageManager.canRequestPackageInstalls())
        )
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> viewModel.onEvent(OnboardingUiEvent.NotificationPermissionResult(granted)) }

    fun requestStoragePermission() {
        if (context.isStoragePermissionGranted()) {
            viewModel.onEvent(OnboardingUiEvent.StoragePermissionResult(true))
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intent = Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
            runCatching { settingsStorageLauncher.launch(intent) }.onFailure {
                runCatching {
                    settingsStorageLauncher.launch(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                }
            }
        } else {
            legacyStoragePermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            )
        }
    }

    fun requestInstallPackagesPermission() {
        if (context.packageManager.canRequestPackageInstalls()) {
            viewModel.onEvent(OnboardingUiEvent.InstallPackagesPermissionResult(true))
            return
        }
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        )
        runCatching { installPackagesLauncher.launch(intent) }
    }

    fun requestNotificationPermission() {
        if (context.isNotificationPermissionGranted()) {
            viewModel.onEvent(OnboardingUiEvent.NotificationPermissionResult(true))
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.onEvent(OnboardingUiEvent.NotificationPermissionResult(true))
        }
    }

    LaunchedEffect(Unit) {
        viewModel.effect.collectLatest { effect ->
            when (effect) {
                OnboardingUiEffect.RequestStoragePermission -> requestStoragePermission()
                OnboardingUiEffect.RequestInstallPackagesPermission -> requestInstallPackagesPermission()
                OnboardingUiEffect.RequestNotificationPermission -> requestNotificationPermission()

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

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
        // Edge-to-Edge: Schritte ohne eigenes Scaffold halten Abstand zu System-/Navigationsleiste, Cutout und IME.
        Box(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            when (uiState.step) {
                OnboardingStep.INTRO -> IntroPagerScreen(
                    currentPage = uiState.currentIntroPage,
                    onPageChanged = { page -> viewModel.onEvent(OnboardingUiEvent.IntroPageChanged(page)) },
                    onFinished = { viewModel.onEvent(OnboardingUiEvent.IntroFinished) }
                )

                OnboardingStep.PERMISSIONS -> PermissionScreen(
                    storageGranted = uiState.storagePermissionGranted,
                    installPackagesGranted = uiState.installPackagesGranted,
                    deletePackagesInfo = uiState.deletePackagesInfo,
                    notificationGranted = uiState.notificationPermissionGranted,
                    onRequestStorage = ::requestStoragePermission,
                    onRequestInstallPackages = ::requestInstallPackagesPermission,
                    onRequestNotification = ::requestNotificationPermission,
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
}

private fun Context.isStoragePermissionGranted(): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }

private fun Context.isNotificationPermissionGranted(): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
