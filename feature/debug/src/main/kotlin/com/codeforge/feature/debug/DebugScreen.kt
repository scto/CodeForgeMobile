package com.codeforge.feature.debug

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.codeforge.core.resources.R
import com.codeforge.core.resources.stringRes

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugBottomSheet(
    projectRoot: String,
    onDismissRequest: () -> Unit,
    onNavigateToSource: (String, Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DebugViewModel = hiltViewModel()
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    LaunchedEffect(projectRoot) {
        viewModel.setProjectRoot(projectRoot)
    }

    val state by viewModel.uiState.collectAsState()

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        modifier = modifier
    ) {
        DebugScreen(
            state = state,
            onIntent = viewModel::handleIntent,
            onNavigateToSource = { path, line ->
                onNavigateToSource(path, line)
            },
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.75f)
        )
    }
}

@Composable
fun DebugRoute(
    projectRoot: String,
    onNavigateToSource: (String, Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DebugViewModel = hiltViewModel()
) {
    LaunchedEffect(projectRoot) {
        viewModel.setProjectRoot(projectRoot)
    }

    val state by viewModel.uiState.collectAsState()

    DebugScreen(
        state = state,
        onIntent = viewModel::handleIntent,
        onNavigateToSource = onNavigateToSource,
        modifier = modifier
    )
}

@Composable
fun DebugScreen(
    state: DebugUiState,
    onIntent: (DebugUiIntent) -> Unit,
    onNavigateToSource: (String, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        // Toolbar oben
        DebugToolbar(
            state = state,
            onIntent = onIntent
        )

        // Fehlermeldung
        if (state.errorMessage != null) {
            Snackbar(
                modifier = Modifier.padding(8.dp),
                action = {
                    IconButton(onClick = { onIntent(DebugUiIntent.ClearError) }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringRes(R.string.common_schliessen),
                            tint = MaterialTheme.colorScheme.inversePrimary
                        )
                    }
                }
            ) {
                Text(text = state.errorMessage)
            }
        }

        // Tab-Reiter
        val tabs = listOf(
            DebugTab.CALL_STACK to stringRes(R.string.debug_tab_call_stack),
            DebugTab.VARIABLES to stringRes(R.string.debug_tab_variables),
            DebugTab.EVALUATE to stringRes(R.string.debug_tab_evaluate),
            DebugTab.BREAKPOINTS to stringRes(R.string.debug_tab_breakpoints)
        )

        val selectedTabIndex = tabs.indexOfFirst { it.first == state.selectedTab }.coerceAtLeast(0)

        TabRow(
            selectedTabIndex = selectedTabIndex,
            modifier = Modifier.fillMaxWidth()
        ) {
            tabs.forEachIndexed { index, (tab, title) ->
                Tab(
                    selected = index == selectedTabIndex,
                    onClick = { onIntent(DebugUiIntent.SelectTab(tab)) },
                    text = { Text(title) }
                )
            }
        }

        // Tab-Inhalte
        when (state.selectedTab) {
            DebugTab.CALL_STACK -> {
                CallStackTab(
                    state = state,
                    onIntent = onIntent,
                    onNavigateToSource = onNavigateToSource
                )
            }
            DebugTab.VARIABLES -> {
                VariablesTab(
                    state = state,
                    onIntent = onIntent
                )
            }
            DebugTab.EVALUATE -> {
                EvaluateTab(
                    state = state,
                    onIntent = onIntent
                )
            }
            DebugTab.BREAKPOINTS -> {
                BreakpointsTab(
                    state = state,
                    onIntent = onIntent,
                    onNavigateToSource = onNavigateToSource
                )
            }
        }
    }
}
