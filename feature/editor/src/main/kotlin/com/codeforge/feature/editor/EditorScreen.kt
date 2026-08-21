// Modul: :feature:editor
package com.codeforge.feature.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.flow.collectLatest

@Composable
fun EditorRoute(
    modifier: Modifier = Modifier,
    onNavigate: (String) -> Unit,
    viewModel: EditorViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.effect.collectLatest { effect ->
            when (effect) {
                is EditorUiEffect.ShowSnackbar -> snackbarHostState.showSnackbar(effect.message)
                is EditorUiEffect.NavigateTo -> onNavigate(effect.route)
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackBarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize()) {
            if (uiState.isLoading) {
                CircularProgressIndicator()
            }

            ScrollableTabRow(selectedTabIndex = uiState.activeFileIndex) {
                uiState.openFiles.forEachIndexed { index, file ->
                    Tab(
                        selected = index == uiState.activeFileIndex,
                        onClick = { /* selectTab -> weiteres UiEvent ergänzen */ },
                        text = { androidx.compose.material3.Text(file.path.substringAfterLast('/')) }
                    )
                }
            }

            uiState.openFiles.getOrNull(uiState.activeFileIndex)?.let { active ->
                SoraCodeEditor(
                    modifier = Modifier.fillMaxSize(),
                    content = active.content,
                    language = EditorLanguageType.fromPath(active.path),
                    onContentChanged = { text -> viewModel.onEvent(EditorUiEvent.TextChanged(text)) }
                )
            }
        }
    }
}
