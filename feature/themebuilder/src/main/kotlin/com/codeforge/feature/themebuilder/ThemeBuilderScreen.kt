// Modul: :feature:themebuilder
package com.codeforge.feature.themebuilder

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.codeforge.core.datastore.proto.ThemeConfig
import com.codeforge.core.datastore.proto.ThemeMode
import com.codeforge.core.designsystem.CodeForgeTheme
import com.codeforge.core.designsystem.ThemePreset
import com.codeforge.core.designsystem.ThemePresets
import com.codeforge.core.resources.R
import com.codeforge.core.resources.Res
import com.codeforge.core.resources.stringRes

@Composable
fun ThemeBuilderRoute(
    modifier: Modifier = Modifier,
    viewModel: ThemeBuilderViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    ThemeBuilderScreen(modifier = modifier, uiState = uiState, onEvent = viewModel::onEvent)
}

@Composable
private fun ThemeBuilderScreen(
    modifier: Modifier = Modifier,
    uiState: ThemeBuilderUiState,
    onEvent: (ThemeBuilderUiEvent) -> Unit
) {
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringRes(R.string.themebuilder_theme)) }) }
    ) { padding ->
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item { ThemePreviewCard(uiState.previewTheme) }

            item { ModeSelector(uiState.themeMode, onEvent) }

            item {
                DynamicColorRow(
                    enabled = uiState.isDynamicColorSupported,
                    checked = uiState.useDynamicColor,
                    onToggle = { onEvent(ThemeBuilderUiEvent.DynamicColorToggled) }
                )
            }

            item {
                Text(stringRes(R.string.themebuilder_farbschema), style = MaterialTheme.typography.titleMedium)
            }

            item {
                PresetGrid(
                    selectedId = uiState.colorSchemeId,
                    dynamicColorActive = uiState.useDynamicColor,
                    onPresetSelected = { id -> onEvent(ThemeBuilderUiEvent.PresetSelected(id)) }
                )
            }

            item {
                Spacer(modifier = Modifier.height(4.dp))
                Text(stringRes(R.string.themebuilder_eigene_farben), style = MaterialTheme.typography.titleMedium)
            }

            item {
                CustomColorField(
                    label = Res.string(R.string.themebuilder_primaer),
                    value = uiState.customPrimary,
                    onChange = { onEvent(ThemeBuilderUiEvent.CustomColorChanged(PaletteSlot.PRIMARY, it)) }
                )
            }
            item {
                CustomColorField(
                    label = Res.string(R.string.themebuilder_sekundaer),
                    value = uiState.customSecondary,
                    onChange = { onEvent(ThemeBuilderUiEvent.CustomColorChanged(PaletteSlot.SECONDARY, it)) }
                )
            }
            item {
                CustomColorField(
                    label = Res.string(R.string.themebuilder_tertiaer),
                    value = uiState.customTertiary,
                    onChange = { onEvent(ThemeBuilderUiEvent.CustomColorChanged(PaletteSlot.TERTIARY, it)) }
                )
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun ThemePreviewCard(previewTheme: ThemeConfig) {
    CodeForgeTheme(themeState = previewTheme) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 2.dp
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(stringRes(R.string.common_vorschau), style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {}) { Text(stringRes(R.string.themebuilder_primaer_button)) }
                }
                Spacer(modifier = Modifier.height(12.dp))
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Beispiel-Karte", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringRes(R.string.themebuilder_so_sehen_text_und_oberflaechen),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeSelector(selected: ThemeMode, onEvent: (ThemeBuilderUiEvent) -> Unit) {
    val options = listOf(
        ThemeMode.SYSTEM to Res.string(R.string.themebuilder_system),
        ThemeMode.LIGHT to Res.string(R.string.themebuilder_hell),
        ThemeMode.DARK to Res.string(R.string.themebuilder_dunkel)
    )

    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (mode, label) ->
            SegmentedButton(
                selected = selected == mode,
                onClick = { onEvent(ThemeBuilderUiEvent.ModeSelected(mode)) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size)
            ) {
                Text(label)
            }
        }
    }
}

@Composable
private fun DynamicColorRow(enabled: Boolean, checked: Boolean, onToggle: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringRes(R.string.themebuilder_dynamische_farben), style = MaterialTheme.typography.titleSmall)
                Text(
                    if (enabled) {
                        Res.string(R.string.themebuilder_verwendet_die_systemfarben_deines_wall)
                    } else {
                        Res.string(R.string.themebuilder_nicht_verfuegbar_auf_diesem_geraet)
                    },
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Switch(checked = checked && enabled, onCheckedChange = { onToggle() }, enabled = enabled)
        }
    }
}

@Composable
private fun PresetGrid(
    selectedId: String,
    dynamicColorActive: Boolean,
    onPresetSelected: (String) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.height(180.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(ThemePresets.all) { preset ->
            PresetCard(
                preset = preset,
                isSelected = !dynamicColorActive && preset.id == selectedId,
                onClick = { onPresetSelected(preset.id) }
            )
        }
    }
}

@Composable
private fun PresetCard(preset: ThemePreset, isSelected: Boolean, onClick: () -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        colors = if (isSelected) {
            CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        } else {
            CardDefaults.elevatedCardColors()
        }
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row {
                ColorDot(preset.primaryHex)
                ColorDot(preset.secondaryHex)
                ColorDot(preset.tertiaryHex)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringRes(preset.labelRes), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ColorDot(hex: String) {
    val color = runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(Color.Gray)
    Box(
        modifier = Modifier
            .size(20.dp)
            .padding(2.dp)
            .clip(CircleShape)
            .background(color)
    )
}

@Composable
private fun CustomColorField(label: String, value: String, onChange: (String) -> Unit) {
    val color = runCatching { Color(android.graphics.Color.parseColor(value)) }.getOrNull()

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(color ?: Color.LightGray)
        )
        Spacer(modifier = Modifier.width(12.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            label = { Text(label) },
            placeholder = { Text(stringRes(R.string.themebuilder_hex_hint)) },
            singleLine = true,
            isError = value.isNotBlank() && color == null,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
