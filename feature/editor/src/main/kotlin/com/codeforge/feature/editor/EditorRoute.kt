@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
/**
 * Modul: :feature:editor
 * @author Thomas Schmid
 */
package com.codeforge.feature.editor

import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.rememberDrawerState
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
    onNavigateBack: () -> Unit = {},
    viewModel: EditorViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    LaunchedEffect(Unit) {
        viewModel.effect.collectLatest { effect ->
            when (effect) {
                is EditorUiEffect.ShowSnackbar -> snackbarHostState.showSnackbar(effect.message)
                is EditorUiEffect.NavigateTo -> {
                    if (effect.route == "..") onNavigateBack() else onNavigate(effect.route)
                }
                EditorUiEffect.NavigateBack -> onNavigateBack()
                else -> {}
            }
        }
    }

    EditorScreen(
        modifier = modifier,
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        drawerState = drawerState,
        effectFlow = viewModel.effect,
        onEvent = viewModel::onEvent
    )
}
