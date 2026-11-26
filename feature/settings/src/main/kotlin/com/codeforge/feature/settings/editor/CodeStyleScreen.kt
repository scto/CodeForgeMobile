/**
 * Modul: :feature:settings
 * @author Thomas Schmid
 *
 * Code-Stil: Formatieren beim Speichern je Sprache, Einrückung und eigene Formatierer.
 */
package com.codeforge.feature.settings.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.codeforge.core.resources.R
import com.codeforge.core.resources.stringRes
import com.codeforge.core.ui.SettingsChipsRow
import com.codeforge.core.ui.SettingsDivider
import com.codeforge.core.ui.SettingsGroup
import com.codeforge.core.ui.SettingsHint
import com.codeforge.core.ui.SettingsLinkRow
import com.codeforge.core.ui.SettingsScaffold
import com.codeforge.core.ui.SettingsSectionLabel
import com.codeforge.core.ui.SettingsSwitchRow
import com.codeforge.feature.settings.EditorPreviewBox
import com.codeforge.feature.settings.EditorSettingsSection
import com.codeforge.feature.settings.EditorSettingsUiEvent
import com.codeforge.feature.settings.EditorSettingsViewModel

/** Route der Code-Stil-Seite. */
@Composable
fun CodeStyleSettingsRoute(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onNavigateToFormatters: () -> Unit = {},
    viewModel: EditorSettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var language by rememberSaveable { mutableStateOf(CodeStyleLanguage.KOTLIN) }
    val onEvent = viewModel::onEvent
    SettingsScaffold(title = stringRes(R.string.settings_code_style_title), onBack = onBack, modifier = modifier) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
        ) {
            SettingsSectionLabel(stringRes(R.string.settings_editor_section_format)) {
                TextButton(onClick = { onEvent(EditorSettingsUiEvent.ResetSection(EditorSettingsSection.FORMAT_ON_SAVE)) }) {
                    Text(stringRes(R.string.settings_editor_reset_section), style = MaterialTheme.typography.labelSmall)
                }
            }
            SettingsGroup {
                SettingsChipsRow(
                    title = stringRes(R.string.settings_code_style_language),
                    summary = stringRes(R.string.settings_code_style_language_desc),
                    options = CodeStyleLanguage.entries,
                    selected = language,
                    label = { it.label },
                    onSelect = { language = it }
                )
                SettingsDivider()
                SettingsSwitchRow(
                    title = stringRes(R.string.settings_code_style_reformat_on_save),
                    summary = stringRes(R.string.settings_code_style_reformat_on_save_desc, language.label),
                    checked = language.formatOnSave(uiState),
                    onCheckedChange = { onEvent(language.toggleEvent()) }
                )
            }

            SettingsSectionLabel(stringRes(R.string.settings_code_style_indentation))
            SettingsGroup {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringRes(R.string.settings_tab_groesse), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = { onEvent(EditorSettingsUiEvent.TabSizeChanged((uiState.tabSize - 1).coerceAtLeast(1))) }) { Text("–") }
                    Text("${uiState.tabSize}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    IconButton(onClick = { onEvent(EditorSettingsUiEvent.TabSizeChanged((uiState.tabSize + 1).coerceAtMost(8))) }) { Text("+") }
                }
                SettingsDivider()
                SettingsSwitchRow(
                    title = stringRes(R.string.settings_soft_tabs),
                    summary = stringRes(R.string.settings_soft_tabs_desc),
                    checked = uiState.softTabs,
                    onCheckedChange = { onEvent(EditorSettingsUiEvent.SoftTabsToggled) }
                )
                SettingsHint(stringRes(R.string.settings_code_style_indentation_hint))
            }

            SettingsSectionLabel(stringRes(R.string.settings_editor_preview))
            SettingsGroup {
                EditorPreviewBox(uiState, language.sample, Modifier.padding(12.dp))
            }

            SettingsSectionLabel(stringRes(R.string.user_formatters_title))
            SettingsGroup {
                SettingsLinkRow(
                    title = stringRes(R.string.user_formatters_title),
                    summary = stringRes(R.string.user_formatters_desc),
                    icon = Icons.Filled.Build,
                    onClick = onNavigateToFormatters
                )
            }
        }
    }
}
