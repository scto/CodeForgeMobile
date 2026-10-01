@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
/**
 * Modul: :feature:composepreview
 * @author Thomas Schmid
 */
package com.codeforge.feature.composepreview

import com.codeforge.core.resources.ResGetter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.codeforge.core.domain.model.ComposableCandidate

@Composable
fun ComposePreviewRoute(
    modifier: Modifier = Modifier,
    viewModel: ComposePreviewViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    ComposePreviewScreen(modifier = modifier, uiState = uiState, onEvent = viewModel::onEvent)
}

@Composable
private fun ComposePreviewScreen(
    modifier: Modifier = Modifier,
    uiState: ComposePreviewUiState,
    onEvent: (ComposePreviewUiEvent) -> Unit
) {
    @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(ResGetter.get(com.codeforge.core.resources.R.string.feature_composepreview_title)) },
                actions = {
                    IconButton(onClick = { onEvent(ComposePreviewUiEvent.Rerender) }) {
                        Icon(Icons.Filled.Refresh, contentDescription = ResGetter.get(com.codeforge.core.resources.R.string.feature_composepreview_cd_rerender))
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (uiState.availableComposables.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Öffne eine Datei mit @Composable-Funktionen im Editor.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                return@Scaffold
            }

            ComposableChipRow(
                candidates = uiState.availableComposables,
                selected = uiState.selectedFunctionName,
                onSelected = { name -> onEvent(ComposePreviewUiEvent.FunctionSelected(name)) }
            )

            Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                RenderStateContent(
                    state = uiState.renderState,
                    onRetry = { onEvent(ComposePreviewUiEvent.Rerender) }
                )
            }
        }
    }
}

@Composable
private fun ComposableChipRow(
    candidates: List<ComposableCandidate>,
    selected: String?,
    onSelected: (String) -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(candidates, key = { it.functionName }) { candidate ->
            FilterChip(
                selected = candidate.functionName == selected,
                onClick = { onSelected(candidate.functionName) },
                label = { Text(candidate.functionName) },
                leadingIcon = if (candidate.hasPreviewAnnotation) {
                    { Icon(Icons.Filled.Star, contentDescription = ResGetter.get(com.codeforge.core.resources.R.string.feature_composepreview_cd_preview), modifier = Modifier.padding(2.dp)) }
                } else {
                    null
                }
            )
        }
    }
}

@Composable
private fun RenderStateContent(
    state: PreviewRenderState,
    onRetry: () -> Unit = {}
) {
    when (state) {
        PreviewRenderState.Idle ->
            Text(ResGetter.get(com.codeforge.core.resources.R.string.feature_composepreview_select_function_hint), style = MaterialTheme.typography.bodyMedium)

        PreviewRenderState.Rendering ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CircularProgressIndicator()
                Text(ResGetter.get(com.codeforge.core.resources.R.string.feature_composepreview_compiling_rendering), style = MaterialTheme.typography.bodyMedium)
            }

        is PreviewRenderState.Rendered -> {
            if (state.imageBytes != null) {
                val bitmap = remember(state.imageBytes) {
                    android.graphics.BitmapFactory.decodeByteArray(state.imageBytes, 0, state.imageBytes.size)
                }
                if (bitmap != null) {
                    androidx.compose.foundation.Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = state.functionName,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Text(ResGetter.get(com.codeforge.core.resources.R.string.feature_composepreview_error_decode), color = MaterialTheme.colorScheme.error)
                }
            } else {
                Text(ResGetter.get(com.codeforge.core.resources.R.string.feature_composepreview_no_image), style = MaterialTheme.typography.bodyMedium)
            }
        }

        is PreviewRenderState.Failed ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = state.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
                androidx.compose.material3.Button(onClick = onRetry) {
                    Text(ResGetter.get(com.codeforge.core.resources.R.string.feature_composepreview_retry))
                }
            }
    }
}
