package com.codeforge.feature.assetstudio.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.codeforge.core.resources.R
import com.codeforge.core.resources.stringRes
import com.codeforge.feature.assetstudio.AssetStudioEffect
import com.codeforge.feature.assetstudio.AssetStudioIntent
import com.codeforge.feature.assetstudio.AssetStudioUiState
import com.codeforge.feature.assetstudio.AssetStudioViewModel
import com.codeforge.feature.assetstudio.StudioMode
import com.codeforge.feature.assetstudio.StudioSource
import com.codeforge.feature.assetstudio.logic.IconShape

/** Route des Asset-Studios. */
@Composable
fun AssetStudioRoute(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: AssetStudioViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { viewModel.onIntent(AssetStudioIntent.ImageChosen(it.toString())) }
    }
    val pickXml = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { viewModel.onIntent(AssetStudioIntent.XmlChosen(it.toString())) }
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { if (it is AssetStudioEffect.Message) snackbar.showSnackbar(it.text) }
    }
    LaunchedEffect(viewModel) { if (viewModel.launchPickerOnStart) pickImage.launch("image/*") }

    if (state.showM3Picker) {
        M3IconPicker(
            onPick = { viewModel.onIntent(AssetStudioIntent.M3Chosen(it)) },
            onBack = { viewModel.onIntent(AssetStudioIntent.ShowM3Picker(false)) },
            modifier = modifier,
        )
    } else {
        AssetStudioScreen(
            state = state,
            snackbar = snackbar,
            onIntent = viewModel::onIntent,
            onBack = onBack,
            onPickImage = { pickImage.launch("image/*") },
            onPickXml = { pickXml.launch("*/*") },
            modifier = modifier,
        )
    }
}

/** Bildschirm des Asset-Studios mit Vorschau und Optionen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetStudioScreen(
    state: AssetStudioUiState,
    snackbar: SnackbarHostState,
    onIntent: (AssetStudioIntent) -> Unit,
    onBack: () -> Unit,
    onPickImage: () -> Unit,
    onPickXml: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isIcon = state.mode == StudioMode.ICON
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringRes(R.string.assetstudio_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringRes(R.string.common_zurueck))
                    }
                },
            )
        },
        bottomBar = {
            Button(
                onClick = { onIntent(AssetStudioIntent.Generate) },
                enabled = !state.isWorking,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            ) {
                if (state.isWorking) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text(stringRes(R.string.assetstudio_generate))
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = isIcon, onClick = { onIntent(AssetStudioIntent.SetMode(StudioMode.ICON)) },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                ) { Text(stringRes(R.string.assetstudio_mode_icon)) }
                SegmentedButton(
                    selected = !isIcon, onClick = { onIntent(AssetStudioIntent.SetMode(StudioMode.DRAWABLE)) },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                ) { Text(stringRes(R.string.assetstudio_mode_drawable)) }
            }

            PreviewCard(state)

            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringRes(R.string.assetstudio_source), style = MaterialTheme.typography.titleLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = onPickImage, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringRes(R.string.assetstudio_select_image), textAlign = TextAlign.Center)
                        }
                        OutlinedButton(onClick = onPickXml, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.Code, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringRes(R.string.assetstudio_load_xml))
                        }
                    }
                    OutlinedButton(onClick = { onIntent(AssetStudioIntent.ShowM3Picker(true)) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringRes(R.string.assetstudio_m3_icons))
                    }
                    Text(
                        state.source?.let { stringRes(R.string.assetstudio_source_selected, it.label) }
                            ?: stringRes(R.string.assetstudio_source_hint),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        stringRes(if (isIcon) R.string.assetstudio_icon_settings else R.string.assetstudio_drawable_settings),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    OutlinedTextField(
                        value = state.name,
                        onValueChange = { onIntent(AssetStudioIntent.SetName(it)) },
                        label = { Text(stringRes(R.string.assetstudio_icon_name)) },
                        singleLine = true,
                        isError = !state.nameValid,
                        supportingText = if (!state.nameValid) ({ Text(stringRes(R.string.assetstudio_name_invalid)) }) else null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (isIcon) {
                        Text(stringRes(R.string.assetstudio_shape), style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconShape.entries.forEach { shape ->
                                FilterChip(
                                    selected = state.shape == shape,
                                    onClick = { onIntent(AssetStudioIntent.SetShape(shape)) },
                                    label = { Text(stringRes(shapeLabel(shape))) },
                                )
                            }
                        }
                        Text(stringRes(R.string.assetstudio_background), style = MaterialTheme.typography.titleMedium)
                        ColorPickRow(state.backgroundArgb, onColor = { onIntent(AssetStudioIntent.SetBackground(it)) })
                        Text(stringRes(R.string.assetstudio_scale, (state.scale * 100).toInt()), style = MaterialTheme.typography.titleMedium)
                        Slider(
                            value = state.scale,
                            onValueChange = { onIntent(AssetStudioIntent.SetScale(it)) },
                            valueRange = 0.3f..1f,
                        )
                    }
                    if (state.source is StudioSource.Vector) {
                        Text(stringRes(R.string.assetstudio_icon_color), style = MaterialTheme.typography.titleMedium)
                        ColorPickRow(
                            argb = state.tintArgb ?: 0xFF000000.toInt(),
                            onColor = { onIntent(AssetStudioIntent.SetTint(it)) },
                            trailing = {
                                FilterChip(
                                    selected = state.tintArgb == null,
                                    onClick = { onIntent(AssetStudioIntent.SetTint(null)) },
                                    label = { Text(stringRes(R.string.assetstudio_original_colors)) },
                                )
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.size(8.dp))
        }
    }
}

private fun shapeLabel(shape: IconShape): Int = when (shape) {
    IconShape.CIRCLE -> R.string.assetstudio_shape_circle
    IconShape.SQUARE -> R.string.assetstudio_shape_square
    IconShape.ROUNDED -> R.string.assetstudio_shape_rounded
}

private fun IconShape.toComposeShape(): Shape = when (this) {
    IconShape.CIRCLE -> CircleShape
    IconShape.SQUARE -> RectangleShape
    IconShape.ROUNDED -> RoundedCornerShape(22)
}

@Composable
private fun PreviewCard(state: AssetStudioUiState) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringRes(R.string.assetstudio_preview), style = MaterialTheme.typography.titleLarge)
            val isIcon = state.mode == StudioMode.ICON
            val shape = if (isIcon) state.shape.toComposeShape() else RoundedCornerShape(22)
            val bg = if (isIcon) Color(state.backgroundArgb) else MaterialTheme.colorScheme.surfaceVariant
            Box(
                modifier = Modifier.size(120.dp).clip(shape).background(bg),
                contentAlignment = Alignment.Center,
            ) {
                val inner = Modifier.fillMaxSize(if (isIcon) state.scale else 0.8f)
                when (val s = state.source) {
                    is StudioSource.Raster -> Image(bitmap = s.image, contentDescription = null, contentScale = ContentScale.Fit, modifier = inner)
                    is StudioSource.Vector -> Image(
                        painter = rememberVectorPainter(s.vector),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        colorFilter = state.tintArgb?.let { ColorFilter.tint(Color(it)) },
                        modifier = inner,
                    )
                    null -> Unit
                }
            }
            Text("120dp × 120dp", style = MaterialTheme.typography.titleMedium)
        }
    }
}
