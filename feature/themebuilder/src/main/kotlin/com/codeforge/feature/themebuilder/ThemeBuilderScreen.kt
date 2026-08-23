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
        topBar = { TopAppBar(title = { Text("Theme") }) }
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
                Text("Farbschema", style = MaterialTheme.typography.titleMedium)
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
                Text("Eigene Farben", style = MaterialTheme.typography.titleMedium)
            }

            item {
                CustomColorField(
                    label = "Primär",
                    value = uiState.customPrimary,
                    onChange = { onEvent(ThemeBuilderUiEvent.CustomColorChanged(PaletteSlot.PRIMARY, it)) }
                )
            }
            item {
                CustomColorField(
                    label = "Sekundär",
                    value = uiState.customSecondary,
                    onChange = { onEvent(ThemeBuilderUiEvent.CustomColorChanged(PaletteSlot.SECONDARY, it)) }
                )
            }
            item {
                CustomColorField(
                    label = "Tertiär",
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
                Text("Vorschau", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {}) { Text("Primär-Button") }
                }
                Spacer(modifier = Modifier.height(12.dp))
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Beispiel-Karte", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "So sehen Text und Oberflächen mit diesem Theme aus.",
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
        ThemeMode.SYSTEM to "System",
        ThemeMode.LIGHT to "Hell",
        ThemeMode.DARK to "Dunkel"
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
                Text("Dynamische Farben", style = MaterialTheme.typography.titleSmall)
                Text(
                    if (enabled) {
                        "Verwendet die Systemfarben deines Wallpapers (Android 12+)."
                    } else {
                        "Nicht verfügbar auf diesem Gerät (Android 12+ erforderlich)."
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
            Text(preset.label, style = MaterialTheme.typography.bodyMedium)
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
            placeholder = { Text("#RRGGBB") },
            singleLine = true,
            isError = value.isNotBlank() && color == null,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
