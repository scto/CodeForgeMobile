// Modul: :feature:settings
package com.codeforge.feature.settings.appearance

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.codeforge.core.datastore.proto.ThemeMode
import com.codeforge.core.resources.R
import com.codeforge.core.resources.stringRes
import com.codeforge.core.ui.SettingsDivider
import com.codeforge.core.ui.SettingsGroup
import com.codeforge.core.ui.SettingsScaffold
import com.codeforge.core.ui.SettingsSectionLabel
import com.codeforge.core.ui.SettingsSwitchRow

/**
 * Einstellungen → Erscheinungsbild: Hell/Dunkel/System, dynamische Farben, Farbschema (CodeForge-Standard oder aus
 * dem Store), Icon- und Sprachpakete. Eigene Farbschemata entstehen im Theme-Builder.
 */
@Composable
fun AppearanceRoute(
    onBack: () -> Unit,
    onOpenStore: () -> Unit,
    onOpenThemeBuilder: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AppearanceViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    AppearanceContent(state, viewModel::onEvent, onBack, onOpenStore, onOpenThemeBuilder, modifier)
}

/** Zustandsloser Inhalt der Seite Erscheinungsbild (auch für Screenshots). */
@Composable
fun AppearanceContent(
    state: AppearanceUiState,
    onEvent: (AppearanceUiEvent) -> Unit,
    onBack: () -> Unit,
    onOpenStore: () -> Unit,
    onOpenThemeBuilder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsScaffold(title = stringRes(R.string.appearance_title), onBack = onBack, modifier = modifier) { padding ->
        if (state.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@SettingsScaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item {
                SettingsSectionLabel(stringRes(R.string.appearance_section_theme))
                SettingsGroup {
                    Box(Modifier.padding(16.dp)) { ModeSelector(state.mode) { onEvent(AppearanceUiEvent.ModeSelected(it)) } }
                    SettingsDivider()
                    SettingsSwitchRow(
                        title = stringRes(R.string.themebuilder_dynamische_farben),
                        summary = stringRes(dynamicSummaryRes(state.dynamicSupported)),
                        checked = state.useDynamicColor && state.dynamicSupported,
                        enabled = state.dynamicSupported,
                        onCheckedChange = { onEvent(AppearanceUiEvent.DynamicToggled) },
                    )
                }
            }
            item {
                SettingsSectionLabel(stringRes(R.string.themebuilder_farbschema))
                SettingsGroup(Modifier.alpha(if (state.useDynamicColor && state.dynamicSupported) DISABLED_ALPHA else 1f)) {
                    Column(Modifier.padding(16.dp)) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(state.schemes, key = { it.id }) { option ->
                                SchemeCard(option, selected = option.id == state.selectedSchemeId) {
                                    onEvent(AppearanceUiEvent.SchemeSelected(option.key))
                                }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            stringRes(R.string.appearance_scheme_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(onClick = onOpenStore, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Filled.Storefront, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringRes(R.string.appearance_more_in_store), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            OutlinedButton(onClick = onOpenThemeBuilder, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Filled.Palette, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringRes(R.string.appearance_create_own), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
            if (state.iconThemes.isNotEmpty()) {
                item {
                    SettingsSectionLabel(stringRes(R.string.themebuilder_icon_pakete))
                    SettingsGroup {
                        ChipSection(
                            options = state.iconThemes.map { it.key to it.label },
                            selected = state.iconThemeId,
                            onSelect = { onEvent(AppearanceUiEvent.IconThemeSelected(it)) },
                        )
                    }
                }
            }
            if (state.languagePacks.isNotEmpty()) {
                item {
                    SettingsSectionLabel(stringRes(R.string.themebuilder_sprachpakete))
                    SettingsGroup {
                        ChipSection(
                            options = state.languagePacks.map { it.key to it.label },
                            selected = state.languagePackId,
                            onSelect = { onEvent(AppearanceUiEvent.LanguagePackSelected(it)) },
                        )
                    }
                }
            }
        }
    }
}

private fun dynamicSummaryRes(supported: Boolean): Int = if (supported) {
    R.string.themebuilder_verwendet_die_systemfarben_deines_wall
} else {
    R.string.themebuilder_nicht_verfuegbar_auf_diesem_geraet
}

private const val DISABLED_ALPHA = 0.45f

@Composable
private fun ModeSelector(selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    val options = listOf(
        ThemeMode.SYSTEM to R.string.themebuilder_system,
        ThemeMode.LIGHT to R.string.themebuilder_hell,
        ThemeMode.DARK to R.string.themebuilder_dunkel,
    )
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, (mode, label) ->
            SegmentedButton(
                selected = selected == mode || (mode == ThemeMode.SYSTEM && selected == ThemeMode.UNRECOGNIZED),
                onClick = { onSelect(mode) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
            ) { Text(stringRes(label)) }
        }
    }
}

@Composable
private fun SchemeCard(option: SchemeOption, selected: Boolean, onClick: () -> Unit) {
    OutlinedCard(
        onClick = onClick,
        modifier = Modifier.width(132.dp).height(104.dp),
        colors = if (selected) CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer) else CardDefaults.outlinedCardColors(),
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                option.swatches.forEach { argb ->
                    Box(
                        Modifier.size(22.dp).background(Color(argb), CircleShape)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                    )
                }
            }
            Column {
                Text(option.label, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    stringRes(
                        when {
                            option.hasLight && option.hasDark -> R.string.appearance_variant_both
                            option.hasDark -> R.string.themebuilder_erweiterung_dunkel
                            else -> R.string.themebuilder_erweiterung_hell
                        },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun ChipSection(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item(key = "") {
                FilterChip(selected = selected.isBlank(), onClick = { onSelect("") }, label = { Text(stringRes(R.string.themebuilder_icon_standard)) })
            }
            items(options, key = { it.first }) { (key, label) ->
                FilterChip(
                    selected = key == selected,
                    onClick = { onSelect(key) },
                    label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                )
            }
        }
    }
}
