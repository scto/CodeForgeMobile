package com.codeforge.app.workspace

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun WorkspaceRoute(viewModel: WorkspaceViewModel = hiltViewModel()) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle().value
    WorkspaceScreen(
        uiState = uiState,
        onFileSelected = { path -> viewModel.openFile(path) },
        onContentChanged = viewModel::onContentChanged,
    )
}
