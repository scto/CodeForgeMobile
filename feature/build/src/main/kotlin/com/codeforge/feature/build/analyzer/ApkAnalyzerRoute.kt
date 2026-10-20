// Modul: :feature:build
package com.codeforge.feature.build.analyzer

import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun ApkAnalyzerRoute(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    initialApkPath: String? = null,
    compareApkPath: String? = null,
    viewModel: ApkAnalyzerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(initialApkPath) {
        if (!initialApkPath.isNullOrBlank() && uiState.analysisResult == null) {
            viewModel.onEvent(ApkAnalyzerUiEvent.LoadApk(initialApkPath))
            if (!compareApkPath.isNullOrBlank()) {
                viewModel.onEvent(ApkAnalyzerUiEvent.CompareWithApk(compareApkPath))
            }
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is ApkAnalyzerUiEffect.ShareReport -> {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, effect.title)
                        putExtra(Intent.EXTRA_TEXT, effect.markdownText)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, effect.title))
                }
                is ApkAnalyzerUiEffect.ShowSnackbar -> {
                    snackbarHostState.showSnackbar(effect.message)
                }
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            ApkAnalyzerScreen(
                uiState = uiState,
                onEvent = viewModel::onEvent,
                onNavigateBack = onNavigateBack
            )
        }
    }
}
