/**
 * Modul: :feature:composepreview
 * @author Thomas Schmid
 */
package com.codeforge.feature.composepreview

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.codeforge.core.domain.model.ComposableCandidate
import com.codeforge.core.domain.model.PreviewThemeMode
import com.codeforge.core.resources.R
import com.codeforge.core.resources.stringRes

/** Route der Compose-Vorschau. */
@Composable
fun ComposePreviewRoute(modifier: Modifier = Modifier, viewModel: ComposePreviewViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    ComposePreviewScreen(modifier = modifier, uiState = uiState, onEvent = viewModel::onEvent)
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ComposePreviewScreen(modifier: Modifier = Modifier, uiState: ComposePreviewUiState, onEvent: (ComposePreviewUiEvent) -> Unit) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringRes(R.string.common_preview)) },
                actions = {
                    IconButton(onClick = { onEvent(ComposePreviewUiEvent.Rerender) }) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringRes(R.string.composepreview_neu_rendern))
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (uiState.availableComposables.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        stringRes(R.string.composepreview_oeffne_eine_datei_mit_composable),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                return@Scaffold
            }

            ModeRow(selected = uiState.mode, onSelected = { onEvent(ComposePreviewUiEvent.ModeChanged(it)) })

            ComposableChipRow(
                candidates = uiState.availableComposables,
                selected = uiState.selectedFunctionName,
                onSelected = { name -> onEvent(ComposePreviewUiEvent.FunctionSelected(name)) }
            )

            if (uiState.mode == PreviewMode.GRADLE) {
                com.codeforge.feature.composepreview.gradle.GradlePreviewPane(
                    state = uiState.gradle,
                    selectedFunction = uiState.selectedFunctionName,
                    onRender = { onEvent(ComposePreviewUiEvent.GradleRender(it)) },
                    onOpenImage = { onEvent(ComposePreviewUiEvent.OpenImage(it)) },
                    modifier = Modifier.weight(1f)
                )
                return@Scaffold
            }

            ThemeModeRow(selected = uiState.themeMode, onSelected = { onEvent(ComposePreviewUiEvent.ThemeModeChanged(it)) })

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                ZoomedContent(zoom = uiState.zoom, modifier = Modifier.fillMaxSize()) {
                    RenderStateContent(
                        state = uiState.renderState,
                        onRetry = { onEvent(ComposePreviewUiEvent.Rerender) }
                    )
                }
                if (uiState.isRefreshing) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter))
                }
            }

            ZoomBar(
                zoom = uiState.zoom,
                onZoomOut = { onEvent(ComposePreviewUiEvent.ZoomOut) },
                onZoomIn = { onEvent(ComposePreviewUiEvent.ZoomIn) },
                onReset = { onEvent(ComposePreviewUiEvent.ZoomReset) }
            )
        }
    }
}

/**
 * Vergrößert alles darin (Bild, Texte, Abstände) um [zoom], indem die Dichte skaliert wird; bei mehr als 100 %
 * lässt sich der Inhalt in beide Richtungen verschieben.
 */
@Composable
private fun ZoomedContent(zoom: Float, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    BoxWithConstraints(modifier = modifier) {
        val baseWidth = maxWidth
        val baseHeight = maxHeight
        val density = LocalDensity.current
        val zoomed = remember(density, zoom) { Density(density.density * zoom, density.fontScale) }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .horizontalScroll(rememberScrollState())
        ) {
            CompositionLocalProvider(LocalDensity provides zoomed) {
                // In der vergrößerten Dichte entspricht baseWidth genau zoom × der verfügbaren Breite.
                Box(
                    modifier = Modifier.width(baseWidth).heightIn(min = baseHeight).padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    content()
                }
            }
        }
    }
}

/** Leiste unten: − links, + rechts, Zoomstufe in der Mitte (Tippen = 100 %). */
@Composable
private fun ZoomBar(zoom: Float, onZoomOut: () -> Unit, onZoomIn: () -> Unit, onReset: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxWidth(), tonalElevation = 3.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onZoomOut, enabled = zoom > PreviewZoom.STEPS.first()) {
                Icon(Icons.Filled.ZoomOut, contentDescription = stringRes(R.string.composepreview_verkleinern))
            }
            TextButton(onClick = onReset, modifier = Modifier.weight(1f)) {
                Text(
                    text = "${(zoom * 100).roundToInt()} %",
                    style = MaterialTheme.typography.titleSmall
                )
            }
            IconButton(onClick = onZoomIn, enabled = zoom < PreviewZoom.STEPS.last()) {
                Icon(Icons.Filled.ZoomIn, contentDescription = stringRes(R.string.composepreview_vergroessern))
            }
        }
    }
}

@Composable
private fun ModeRow(selected: PreviewMode, onSelected: (PreviewMode) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        PreviewMode.entries.forEach { mode ->
            FilterChip(
                selected = mode == selected,
                onClick = { onSelected(mode) },
                label = {
                    Text(
                        stringRes(
                            if (mode == PreviewMode.LIVE) R.string.composepreview_mode_live else R.string.composepreview_mode_gradle
                        )
                    )
                }
            )
        }
    }
}

@Composable
private fun ThemeModeRow(selected: PreviewThemeMode, onSelected: (PreviewThemeMode) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        listOf(
            PreviewThemeMode.AUTO to R.string.composepreview_modus_auto,
            PreviewThemeMode.LIGHT to R.string.composepreview_modus_hell,
            PreviewThemeMode.DARK to R.string.composepreview_modus_dunkel
        ).forEach { (mode, label) ->
            FilterChip(selected = mode == selected, onClick = { onSelected(mode) }, label = { Text(stringRes(label)) })
        }
    }
}

@Composable
private fun ComposableChipRow(candidates: List<ComposableCandidate>, selected: String?, onSelected: (String) -> Unit) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(candidates, key = { it.functionName }) { candidate ->
            FilterChip(
                selected = candidate.functionName == selected,
                // Funktionen mit Parametern ohne @Preview lassen sich nicht rendern → nicht anwählbar (Vorschau-Funktion ★ nutzen)
                enabled = candidate.hasPreviewAnnotation || !candidate.hasParameters,
                onClick = { onSelected(candidate.functionName) },
                label = { Text(candidate.options.name ?: candidate.functionName) },
                leadingIcon = if (candidate.hasPreviewAnnotation) {
                    { Icon(Icons.Filled.Star, contentDescription = "@Preview", modifier = Modifier.padding(2.dp)) }
                } else {
                    null
                }
            )
        }
    }
}

@Composable
private fun RenderStateContent(state: PreviewRenderState, onRetry: () -> Unit) {
    when (state) {
        PreviewRenderState.Idle ->
            Text(stringRes(R.string.composepreview_waehle_eine_funktion_fuer_die), style = MaterialTheme.typography.bodyMedium)

        is PreviewRenderState.PreparingRuntime -> {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                LinearProgressIndicator(
                    progress = { (state.progress.coerceIn(0, 100)) / 100f },
                    modifier = Modifier.fillMaxWidth(0.7f)
                )
                Text(
                    stringRes(R.string.composepreview_laufzeit_wird_vorbereitet, state.progress.coerceIn(0, 100)),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        PreviewRenderState.Rendering -> {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CircularProgressIndicator()
                Text(
                    stringRes(R.string.composepreview_kompilierung_laeuft),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        is PreviewRenderState.Rendered -> {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (state.imageBytes != null) {
                    val bitmap = remember(state.imageBytes) {
                        android.graphics.BitmapFactory.decodeByteArray(state.imageBytes, 0, state.imageBytes.size)
                    }
                    if (bitmap != null) {
                        androidx.compose.foundation.Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = state.functionName,
                            // Das gerenderte Bild ist transparent und nutzt Standard-Textfarbe (schwarz): auf hellem Untergrund
                            // zeigen, sonst ist es auf dem dunklen App-Hintergrund unsichtbar.
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                        )
                    } else {
                        Text(stringRes(R.string.composepreview_bild_konnte_nicht_dekodiert_werden), color = MaterialTheme.colorScheme.error)
                    }
                } else {
                    Text(
                        stringRes(R.string.common_erkannt, state.functionName),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        stringRes(R.string.composepreview_die_grafische_rendering_pipeline_ist),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                Text(
                    text = stringRes(
                        if (state.engine == com.codeforge.core.domain.model.PreviewEngine.INTERPRETED) R.string.composepreview_engine_interpreted
                        else R.string.composepreview_engine_compiled,
                        state.durationMs.toInt()
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                if (state.usesBuildOutputs) {
                    Text(
                        text = stringRes(R.string.composepreview_build_outputs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                state.engineNote?.let { note ->
                    Text(
                        text = stringRes(R.string.composepreview_engine_fallback, note),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
                if (state.compiledFiles.size > 1) {
                    Text(
                        text = stringRes(R.string.composepreview_mitkompilierte_quellen, state.compiledFiles.joinToString(", ")),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (state.isFallbackTheme) {
                    Text(
                        text = stringRes(R.string.composepreview_rueckfall_standard_theme, state.fallbackReason ?: ""),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        is PreviewRenderState.Failed -> {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(state.message, color = MaterialTheme.colorScheme.error)
                Button(onClick = onRetry) {
                    Text(stringRes(R.string.common_erneut_versuchen))
                }
            }
        }
    }
}
