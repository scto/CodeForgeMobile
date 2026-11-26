/**
 * Modul: :feature:settings
 * @author Thomas Schmid
 *
 * Farbschema des Editors: Auswahl aus eingebauten und per Plugin installierten TextMate-Themes,
 * Live-Vorschau mit Kotlin-, XML- und Markdown-Beispiel.
 */
package com.codeforge.feature.settings.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.codeforge.core.designsystem.EditorPreviewSpec
import com.codeforge.core.designsystem.theme.TextMateThemeItem
import com.codeforge.core.resources.R
import com.codeforge.core.resources.stringRes
import com.codeforge.core.ui.SettingsDivider
import com.codeforge.core.ui.SettingsGroup
import com.codeforge.core.ui.SettingsHint
import com.codeforge.core.ui.SettingsLinkRow
import com.codeforge.core.ui.SettingsScaffold
import com.codeforge.core.ui.SettingsSectionLabel
import com.codeforge.feature.settings.EditorPreviewBox
import com.codeforge.feature.settings.EditorSettingsUiEvent
import com.codeforge.feature.settings.EditorSettingsViewModel

private val PREVIEW_SAMPLES = listOf(
    EditorPreviewSpec.SAMPLE_KOTLIN to "Kotlin",
    EditorPreviewSpec.SAMPLE_XML to "XML",
    EditorPreviewSpec.SAMPLE_MARKDOWN to "Markdown"
)

private const val AUTO_THEME = "auto"

/** Route der Farbschema-Seite. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColorSchemeSettingsRoute(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onOpenStore: () -> Unit = {},
    viewModel: EditorSettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var sampleIndex by rememberSaveable { mutableIntStateOf(0) }
    SettingsScaffold(title = stringRes(R.string.settings_color_scheme_title), onBack = onBack, modifier = modifier) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
        ) {
            SettingsSectionLabel(stringRes(R.string.settings_color_scheme_schemes))
            SettingsGroup {
                ThemeChips(
                    selectedId = uiState.textmateTheme,
                    themes = uiState.availableThemes,
                    onSelect = { viewModel.onEvent(EditorSettingsUiEvent.TextmateThemeChanged(it)) }
                )
                SettingsHint(stringRes(R.string.settings_color_scheme_hint))
                SettingsDivider()
                SettingsLinkRow(
                    title = stringRes(R.string.settings_color_scheme_more),
                    summary = stringRes(R.string.settings_color_scheme_more_desc),
                    icon = Icons.Filled.Storefront,
                    onClick = onOpenStore
                )
            }
            SettingsSectionLabel(stringRes(R.string.settings_editor_preview))
            SettingsGroup {
                PrimaryTabRow(selectedTabIndex = sampleIndex) {
                    PREVIEW_SAMPLES.forEachIndexed { index, (_, label) ->
                        Tab(selected = sampleIndex == index, onClick = { sampleIndex = index }, text = { Text(label) })
                    }
                }
                EditorPreviewBox(uiState, PREVIEW_SAMPLES[sampleIndex].first, Modifier.padding(12.dp))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ThemeChips(selectedId: String, themes: List<TextMateThemeItem>, onSelect: (String) -> Unit) {
    val selected = selectedId.ifBlank { AUTO_THEME }
    val (bundled, plugins) = themes.partition { it.filePath == null }
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = selected == AUTO_THEME,
                onClick = { onSelect(AUTO_THEME) },
                label = { Text(stringRes(R.string.settings_editor_theme_auto)) }
            )
            bundled.forEach { theme ->
                FilterChip(
                    selected = theme.id.equals(selected, ignoreCase = true),
                    onClick = { onSelect(theme.id) },
                    label = { Text(theme.displayName) }
                )
            }
        }
        if (plugins.isNotEmpty()) {
            Text(
                stringRes(R.string.settings_color_scheme_from_plugins),
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                plugins.forEach { theme ->
                    FilterChip(
                        selected = theme.id.equals(selected, ignoreCase = true),
                        onClick = { onSelect(theme.id) },
                        label = { Text(theme.displayName) }
                    )
                }
            }
        }
    }
}
