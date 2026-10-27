// Modul: :feature:themebuilder
package com.codeforge.feature.themebuilder

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.activity.compose.BackHandler
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.codeforge.core.datastore.proto.ThemeConfig
import com.codeforge.core.datastore.proto.ThemeMode
import com.codeforge.core.designsystem.CodeForgeTheme
import com.codeforge.core.designsystem.ThemePreset
import com.codeforge.core.designsystem.ThemePresets
import com.codeforge.core.domain.extension.ExtensionColorTheme
import com.codeforge.core.domain.extension.ExtensionIconTheme
import androidx.compose.material3.FilterChip
import com.codeforge.core.resources.R
import com.codeforge.core.resources.Res
import com.codeforge.core.resources.stringRes
import com.codeforge.core.ui.SettingsTopBar

@Composable
fun ThemeBuilderRoute(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    viewModel: ThemeBuilderViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    ThemeBuilderScreen(modifier = modifier, uiState = uiState, onBack = onBack, onEvent = viewModel::onEvent)
}

@Composable
private fun ThemeBuilderScreen(
    modifier: Modifier = Modifier,
    uiState: ThemeBuilderUiState,
    onBack: () -> Unit = {},
    onEvent: (ThemeBuilderUiEvent) -> Unit
) {
    var activePickerSlot by remember { mutableStateOf<PaletteSlot?>(null) }

    BackHandler(enabled = activePickerSlot != null) {
        activePickerSlot = null
    }

    Scaffold(
        modifier = modifier,
        topBar = { SettingsTopBar(title = stringRes(R.string.themebuilder_theme), onBack = onBack) }
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
            item { ThemePreviewCard(uiState.previewTheme, uiState.previewExtensionTheme) }

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
                PresetRow(
                    selectedId = uiState.colorSchemeId,
                    dynamicColorActive = uiState.useDynamicColor,
                    onPresetSelected = { id -> onEvent(ThemeBuilderUiEvent.PresetSelected(id)) }
                )
            }

            if (uiState.extensionThemes.isNotEmpty()) {
                item {
                    Text(stringRes(R.string.themebuilder_aus_erweiterungen), style = MaterialTheme.typography.titleMedium)
                }
                item {
                    ExtensionThemeRow(
                        themes = uiState.extensionThemes,
                        selectedKey = uiState.colorSchemeId,
                        onSelected = { key -> onEvent(ThemeBuilderUiEvent.PresetSelected(key)) }
                    )
                }
            }

            if (uiState.iconThemes.isNotEmpty()) {
                item {
                    Text(stringRes(R.string.themebuilder_icon_pakete), style = MaterialTheme.typography.titleMedium)
                }
                item {
                    IconThemeRow(
                        themes = uiState.iconThemes,
                        selectedKey = uiState.iconThemeId,
                        onSelected = { key -> onEvent(ThemeBuilderUiEvent.IconThemeSelected(key)) }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(4.dp))
                Text(stringRes(R.string.themebuilder_eigene_farben), style = MaterialTheme.typography.titleMedium)
            }

            item {
                CustomColorField(
                    label = Res.string(R.string.themebuilder_primaer),
                    value = uiState.customPrimary,
                    onEditColor = { activePickerSlot = PaletteSlot.PRIMARY },
                    onChange = { onEvent(ThemeBuilderUiEvent.CustomColorChanged(PaletteSlot.PRIMARY, it)) }
                )
            }
            item {
                CustomColorField(
                    label = Res.string(R.string.themebuilder_sekundaer),
                    value = uiState.customSecondary,
                    onEditColor = { activePickerSlot = PaletteSlot.SECONDARY },
                    onChange = { onEvent(ThemeBuilderUiEvent.CustomColorChanged(PaletteSlot.SECONDARY, it)) }
                )
            }
            item {
                CustomColorField(
                    label = Res.string(R.string.themebuilder_tertiaer),
                    value = uiState.customTertiary,
                    onEditColor = { activePickerSlot = PaletteSlot.TERTIARY },
                    onChange = { onEvent(ThemeBuilderUiEvent.CustomColorChanged(PaletteSlot.TERTIARY, it)) }
                )
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }
        }
    }

    activePickerSlot?.let { slot ->
        val currentHex = when (slot) {
            PaletteSlot.PRIMARY -> uiState.customPrimary
            PaletteSlot.SECONDARY -> uiState.customSecondary
            PaletteSlot.TERTIARY -> uiState.customTertiary
        }
        RgbColorPickerDialog(
            slot = slot,
            initialHex = currentHex,
            onConfirm = { s, hex ->
                onEvent(ThemeBuilderUiEvent.CustomColorChanged(s, hex))
                activePickerSlot = null
            },
            onDismiss = { activePickerSlot = null }
        )
    }
}

@Composable
private fun ThemePreviewCard(previewTheme: ThemeConfig, extensionTheme: ExtensionColorTheme?) {
    CodeForgeTheme(themeState = previewTheme, extensionTheme = extensionTheme) {
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
                        Text(stringRes(R.string.themebuilder_beispiel_karte), style = MaterialTheme.typography.bodyLarge)
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
private fun PresetRow(
    selectedId: String,
    dynamicColorActive: Boolean,
    onPresetSelected: (String) -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp)
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
private fun IconThemeRow(
    themes: List<ExtensionIconTheme>,
    selectedKey: String,
    onSelected: (String) -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp)
    ) {
        item(key = "default") {
            FilterChip(
                selected = selectedKey.isBlank(),
                onClick = { onSelected("") },
                label = { Text(stringRes(R.string.themebuilder_icon_standard)) }
            )
        }
        items(themes, key = { it.key }) { theme ->
            FilterChip(
                selected = theme.key == selectedKey,
                onClick = { onSelected(theme.key) },
                label = { Text(theme.label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            )
        }
    }
}

@Composable
private fun ExtensionThemeRow(
    themes: List<ExtensionColorTheme>,
    selectedKey: String,
    onSelected: (String) -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp)
    ) {
        items(themes, key = { it.key }) { theme ->
            val isSelected = theme.key == selectedKey
            val primary = Color(((theme.colors["primary"] ?: 0xFF888888L).toInt()))
            val secondary = Color(((theme.colors["secondary"] ?: theme.colors["primary"] ?: 0xFF888888L).toInt()))
            val surface = Color(((theme.colors["background"] ?: if (theme.isDark) 0xFF121212L else 0xFFFFFFFFL).toInt()))
            OutlinedCard(
                modifier = Modifier.width(140.dp).height(115.dp),
                onClick = { onSelected(theme.key) },
                colors = if (isSelected) {
                    CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                } else {
                    CardDefaults.outlinedCardColors()
                },
                border = if (isSelected) {
                    BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                } else {
                    BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                }
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(10.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(primary, secondary, surface).forEach { c ->
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .background(c, CircleShape)
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                            )
                        }
                    }
                    Column {
                        Text(theme.label, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            stringRes(if (theme.isDark) R.string.themebuilder_erweiterung_dunkel else R.string.themebuilder_erweiterung_hell),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PresetCard(preset: ThemePreset, isSelected: Boolean, onClick: () -> Unit) {
    val primary = runCatching { Color(android.graphics.Color.parseColor(preset.primaryHex)) }.getOrDefault(Color.Gray)
    val secondary = runCatching { Color(android.graphics.Color.parseColor(preset.secondaryHex)) }.getOrDefault(Color.Gray)
    val tertiary = runCatching { Color(android.graphics.Color.parseColor(preset.tertiaryHex)) }.getOrDefault(Color.Gray)

    OutlinedCard(
        modifier = Modifier
            .width(140.dp)
            .height(115.dp),
        onClick = onClick,
        colors = if (isSelected) {
            CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        } else {
            CardDefaults.outlinedCardColors()
        },
        border = if (isSelected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(primary)
                )
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(secondary)
                )
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(tertiary)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
            ) {
                Box(modifier = Modifier.weight(1f).fillMaxHeight().background(primary))
                Box(modifier = Modifier.weight(1f).fillMaxHeight().background(secondary))
                Box(modifier = Modifier.weight(1f).fillMaxHeight().background(tertiary))
            }

            Text(
                text = stringRes(preset.labelRes),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun CustomColorField(
    label: String,
    value: String,
    onEditColor: () -> Unit,
    onChange: (String) -> Unit
) {
    val color = runCatching { Color(android.graphics.Color.parseColor(value)) }.getOrNull()

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(color ?: Color.LightGray)
                .clickable(onClick = onEditColor)
        )
        Spacer(modifier = Modifier.width(12.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            label = { Text(label) },
            placeholder = { Text(stringRes(R.string.themebuilder_hex_hint)) },
            singleLine = true,
            isError = value.isNotBlank() && color == null,
            trailingIcon = {
                IconButton(onClick = onEditColor) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = stringRes(R.string.themebuilder_palette_anpassen)
                    )
                }
            },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun RgbColorPickerDialog(
    slot: PaletteSlot,
    initialHex: String,
    onConfirm: (PaletteSlot, String) -> Unit,
    onDismiss: () -> Unit
) {
    val initialRgb = remember(initialHex) {
        RgbColorUtils.hexToRgb(initialHex) ?: Triple(33, 150, 243)
    }
    var red by remember { mutableFloatStateOf(initialRgb.first.toFloat()) }
    var green by remember { mutableFloatStateOf(initialRgb.second.toFloat()) }
    var blue by remember { mutableFloatStateOf(initialRgb.third.toFloat()) }
    var hexText by remember { mutableStateOf(RgbColorUtils.rgbToHex(initialRgb.first, initialRgb.second, initialRgb.third)) }

    fun updateFromRgb(r: Int, g: Int, b: Int) {
        red = r.toFloat()
        green = g.toFloat()
        blue = b.toFloat()
        hexText = RgbColorUtils.rgbToHex(r, g, b)
    }

    val currentColor = Color(red.toInt(), green.toInt(), blue.toInt())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringRes(R.string.themebuilder_farbauswahl)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(currentColor),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = hexText,
                        color = if (red * 0.299 + green * 0.587 + blue * 0.114 > 186) Color.Black else Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                ColorSliderRow(
                    label = stringRes(R.string.themebuilder_rot),
                    value = red,
                    color = Color.Red,
                    onValueChange = {
                        red = it
                        hexText = RgbColorUtils.rgbToHex(red.toInt(), green.toInt(), blue.toInt())
                    }
                )

                ColorSliderRow(
                    label = stringRes(R.string.themebuilder_gruen),
                    value = green,
                    color = Color.Green,
                    onValueChange = {
                        green = it
                        hexText = RgbColorUtils.rgbToHex(red.toInt(), green.toInt(), blue.toInt())
                    }
                )

                ColorSliderRow(
                    label = stringRes(R.string.themebuilder_blau),
                    value = blue,
                    color = Color.Blue,
                    onValueChange = {
                        blue = it
                        hexText = RgbColorUtils.rgbToHex(red.toInt(), green.toInt(), blue.toInt())
                    }
                )

                OutlinedTextField(
                    value = hexText,
                    onValueChange = { text ->
                        hexText = text
                        RgbColorUtils.hexToRgb(text)?.let { (r, g, b) ->
                            red = r.toFloat()
                            green = g.toFloat()
                            blue = b.toFloat()
                        }
                    },
                    label = { Text(stringRes(R.string.themebuilder_farbwert)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = stringRes(R.string.themebuilder_schnellauswahl),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(RgbColorUtils.QUICK_PRESETS) { presetHex ->
                        val presetColor = runCatching { Color(android.graphics.Color.parseColor(presetHex)) }.getOrDefault(Color.Gray)
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(presetColor)
                                .clickable {
                                    RgbColorUtils.hexToRgb(presetHex)?.let { (r, g, b) ->
                                        updateFromRgb(r, g, b)
                                    }
                                }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(slot, hexText)
                }
            ) {
                Text(stringRes(R.string.themebuilder_farbe_anwenden))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringRes(R.string.common_abbrechen))
            }
        }
    )
}

@Composable
private fun ColorSliderRow(
    label: String,
    value: Float,
    color: Color,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodySmall)
            Text(value.toInt().toString(), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0f..255f,
            colors = SliderDefaults.colors(
                thumbColor = color,
                activeTrackColor = color.copy(alpha = 0.7f)
            )
        )
    }
}
