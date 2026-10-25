/**
 * Modul: :feature:settings
 * @author Thomas Schmid
 */
package com.codeforge.feature.settings

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.codeforge.core.common.FileHeaderContext
import com.codeforge.core.common.FileHeaderRenderer
import com.codeforge.core.resources.R
import com.codeforge.core.resources.Res
import com.codeforge.core.resources.stringRes
import com.codeforge.core.ui.SettingsTopBar

@Composable
fun EditorSettingsRoute(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onNavigateToSnippets: () -> Unit = {},
    viewModel: EditorSettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    EditorSettingsScreen(
        modifier = modifier,
        uiState = uiState,
        onBack = onBack,
        onNavigateToSnippets = onNavigateToSnippets,
        onEvent = viewModel::onEvent
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorSettingsScreen(
    modifier: Modifier = Modifier,
    uiState: EditorSettingsUiState,
    onBack: () -> Unit = {},
    onNavigateToSnippets: () -> Unit = {},
    onEvent: (EditorSettingsUiEvent) -> Unit
) {
    Scaffold(
        modifier = modifier,
        topBar = { SettingsTopBar(title = stringRes(R.string.common_editor), onBack = onBack) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // --- SNIPPETS ---
            Card(
                modifier = Modifier.fillMaxWidth(),
                onClick = onNavigateToSnippets
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringRes(R.string.settings_editor_snippets), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringRes(R.string.settings_editor_snippets_desc),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = stringRes(R.string.settings_editor_snippets)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --- DARSTELLUNG (APPEARANCE) ---
            SectionHeader(
                title = stringRes(R.string.settings_editor_section_display),
                onReset = { onEvent(EditorSettingsUiEvent.ResetSection(EditorSettingsSection.DISPLAY)) }
            )

            // TextMate Theme Dropdown
            ThemeDropdown(
                selectedThemeId = uiState.textmateTheme,
                availableThemes = uiState.availableThemes,
                onThemeSelected = { onEvent(EditorSettingsUiEvent.TextmateThemeChanged(it)) }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Font Family Dropdown
            FontFamilyDropdown(
                selectedFontFamily = uiState.fontFamily,
                onFontFamilySelected = { onEvent(EditorSettingsUiEvent.FontFamilyChanged(it)) }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Schriftgröße Slider mit Vorschau
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringRes(R.string.settings_editor_font_size_slider), style = MaterialTheme.typography.titleSmall)
                        Text("${uiState.fontSize} sp", style = MaterialTheme.typography.labelLarge)
                    }
                    Slider(
                        value = uiState.fontSize.toFloat(),
                        onValueChange = { onEvent(EditorSettingsUiEvent.FontSizeChanged(it.toInt())) },
                        valueRange = 8f..32f,
                        steps = 23
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(8.dp)
                            )
                            .padding(12.dp)
                    ) {
                        Text(
                            text = stringRes(R.string.settings_editor_font_size_preview_sample),
                            fontSize = uiState.fontSize.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            EditorToggleRow(
                title = Res.string(R.string.settings_zeilenumbruch_word_wrap),
                checked = uiState.wordWrap,
                onToggle = { onEvent(EditorSettingsUiEvent.WordWrapToggled) }
            )
            EditorToggleRow(
                title = Res.string(R.string.settings_nicht_druckbare_zeichen_anzeigen),
                description = Res.string(R.string.settings_leerzeichen_tabs_und_zeilenumbrueche_a),
                checked = uiState.showNonPrintableChars,
                onToggle = { onEvent(EditorSettingsUiEvent.NonPrintableCharsToggled) }
            )
            EditorToggleRow(
                title = Res.string(R.string.settings_sticky_scroll),
                description = Res.string(R.string.settings_zeigt_den_umschliessenden_code_block),
                checked = uiState.stickyScrollEnabled,
                onToggle = { onEvent(EditorSettingsUiEvent.StickyScrollToggled) }
            )
            EditorToggleRow(
                title = Res.string(R.string.settings_minimap),
                description = Res.string(R.string.settings_minimap_desc),
                checked = uiState.minimapEnabled,
                onToggle = { onEvent(EditorSettingsUiEvent.MinimapToggled) }
            )
            EditorToggleRow(
                title = Res.string(R.string.settings_rainbow_brackets),
                description = Res.string(R.string.settings_rainbow_brackets_desc),
                checked = uiState.rainbowBracketsEnabled,
                onToggle = { onEvent(EditorSettingsUiEvent.RainbowBracketsToggled) }
            )
            EditorToggleRow(
                title = Res.string(R.string.settings_text_lupe_magnifier),
                description = Res.string(R.string.settings_vergroesserungsglas_bei_cursor_platzie),
                checked = uiState.magnifierEnabled,
                onToggle = { onEvent(EditorSettingsUiEvent.MagnifierToggled) }
            )
            EditorToggleRow(
                title = Res.string(R.string.settings_pinch_to_zoom),
                description = Res.string(R.string.settings_pinch_to_zoom_desc),
                checked = uiState.pinchToZoomEnabled,
                onToggle = { onEvent(EditorSettingsUiEvent.PinchToZoomToggled) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // --- EINGABE (INPUT) ---
            SectionHeader(
                title = stringRes(R.string.settings_editor_section_input),
                onReset = { onEvent(EditorSettingsUiEvent.ResetSection(EditorSettingsSection.INPUT)) }
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringRes(R.string.settings_tab_groesse), style = MaterialTheme.typography.titleSmall)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { onEvent(EditorSettingsUiEvent.TabSizeChanged((uiState.tabSize - 1).coerceAtLeast(1))) }) {
                            Text("–")
                        }
                        Text("${uiState.tabSize}", modifier = Modifier.padding(horizontal = 8.dp))
                        IconButton(onClick = { onEvent(EditorSettingsUiEvent.TabSizeChanged((uiState.tabSize + 1).coerceAtMost(8))) }) {
                            Text("+")
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            EditorToggleRow(
                title = Res.string(R.string.settings_klammern_anfuehrungszeichen_automatisc),
                checked = uiState.symbolPairAutocompleteEnabled,
                onToggle = { onEvent(EditorSettingsUiEvent.SymbolPairAutocompleteToggled) }
            )
            EditorToggleRow(
                title = Res.string(R.string.settings_desktop_shortcuts),
                description = Res.string(R.string.settings_desktop_shortcuts_desc),
                checked = uiState.desktopShortcutsEnabled,
                onToggle = { onEvent(EditorSettingsUiEvent.DesktopShortcutsToggled) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // --- SPRACHFUNKTIONEN (LANGUAGE) ---
            SectionHeader(
                title = stringRes(R.string.settings_editor_section_language),
                onReset = { onEvent(EditorSettingsUiEvent.ResetSection(EditorSettingsSection.LANGUAGE)) }
            )

            EditorToggleRow(
                title = Res.string(R.string.settings_tree_sitter_verwenden),
                description = Res.string(R.string.settings_praeziseres_syntax_highlighting_statt),
                checked = uiState.useTreeSitter,
                onToggle = { onEvent(EditorSettingsUiEvent.TreeSitterToggled) }
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(stringRes(R.string.settings_lsp_section_title), style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringRes(R.string.settings_lsp_max_sessions_label), style = MaterialTheme.typography.bodyMedium)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { onEvent(EditorSettingsUiEvent.LspMaxConcurrentSessionsChanged((uiState.lspMaxConcurrentSessions - 1).coerceAtLeast(1))) }) {
                                Text("–")
                            }
                            Text("${uiState.lspMaxConcurrentSessions}", modifier = Modifier.padding(horizontal = 8.dp))
                            IconButton(onClick = { onEvent(EditorSettingsUiEvent.LspMaxConcurrentSessionsChanged((uiState.lspMaxConcurrentSessions + 1).coerceAtMost(10))) }) {
                                Text("+")
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringRes(R.string.settings_lsp_idle_timeout_label), style = MaterialTheme.typography.bodyMedium)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { onEvent(EditorSettingsUiEvent.LspIdleTimeoutMinutesChanged((uiState.lspIdleTimeoutMinutes - 1).coerceAtLeast(1))) }) {
                                Text("–")
                            }
                            Text("${uiState.lspIdleTimeoutMinutes}m", modifier = Modifier.padding(horizontal = 8.dp))
                            IconButton(onClick = { onEvent(EditorSettingsUiEvent.LspIdleTimeoutMinutesChanged((uiState.lspIdleTimeoutMinutes + 5).coerceAtMost(120))) }) {
                                Text("+")
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --- FORMAT BEIM SPEICHERN ---
            SectionHeader(
                title = stringRes(R.string.settings_editor_section_format),
                onReset = { onEvent(EditorSettingsUiEvent.ResetSection(EditorSettingsSection.FORMAT_ON_SAVE)) }
            )

            EditorToggleRow(
                title = Res.string(R.string.settings_format_on_save_kotlin),
                description = Res.string(R.string.settings_format_on_save_kotlin_desc),
                checked = uiState.formatOnSaveKotlin,
                onToggle = { onEvent(EditorSettingsUiEvent.FormatOnSaveKotlinToggled) }
            )
            EditorToggleRow(
                title = Res.string(R.string.settings_format_on_save_java),
                description = Res.string(R.string.settings_format_on_save_java_desc),
                checked = uiState.formatOnSaveJava,
                onToggle = { onEvent(EditorSettingsUiEvent.FormatOnSaveJavaToggled) }
            )
            EditorToggleRow(
                title = Res.string(R.string.settings_format_on_save_xml),
                description = Res.string(R.string.settings_format_on_save_xml_desc),
                checked = uiState.formatOnSaveXml,
                onToggle = { onEvent(EditorSettingsUiEvent.FormatOnSaveXmlToggled) }
            )
            EditorToggleRow(
                title = Res.string(R.string.settings_format_on_save_json),
                description = Res.string(R.string.settings_format_on_save_json_desc),
                checked = uiState.formatOnSaveJson,
                onToggle = { onEvent(EditorSettingsUiEvent.FormatOnSaveJsonToggled) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // --- DATEIKOPF (FILE HEADER) ---
            SectionHeader(title = stringRes(R.string.settings_editor_section_file_header))

            EditorToggleRow(
                title = Res.string(R.string.settings_editor_file_header_enabled),
                checked = uiState.fileHeaderEnabled,
                onToggle = { onEvent(EditorSettingsUiEvent.FileHeaderEnabledToggled) }
            )

            if (uiState.fileHeaderEnabled) {
                OutlinedTextField(
                    value = uiState.fileHeaderAuthor,
                    onValueChange = { onEvent(EditorSettingsUiEvent.FileHeaderAuthorChanged(it)) },
                    label = { Text(stringRes(R.string.settings_editor_file_header_author)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                val sampleHeader = FileHeaderRenderer.render(
                    extension = "kt",
                    context = FileHeaderContext(
                        filename = "Sample.kt",
                        moduleName = ":feature:sample",
                        author = uiState.fileHeaderAuthor.ifBlank { "Author" },
                        year = "2026"
                    )
                ).orEmpty()

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            stringRes(R.string.settings_editor_file_header_preview),
                            style = MaterialTheme.typography.labelMedium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = sampleHeader,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    onReset: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        if (onReset != null) {
            TextButton(onClick = onReset) {
                Text(stringRes(R.string.settings_editor_reset_section), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
    Spacer(modifier = Modifier.height(4.dp))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeDropdown(
    selectedThemeId: String,
    availableThemes: List<com.codeforge.core.designsystem.theme.TextMateThemeItem>,
    onThemeSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val autoLabel = stringRes(R.string.settings_editor_theme_auto)
    val currentLabel = if (selectedThemeId.isBlank() || selectedThemeId == "auto") {
        autoLabel
    } else {
        availableThemes.find { it.id.equals(selectedThemeId, ignoreCase = true) }?.displayName ?: autoLabel
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = currentLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringRes(R.string.settings_textmate_theme)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text(autoLabel) },
                onClick = {
                    onThemeSelected("auto")
                    expanded = false
                }
            )
            availableThemes.forEach { theme ->
                DropdownMenuItem(
                    text = { Text(theme.displayName) },
                    onClick = {
                        onThemeSelected(theme.id)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FontFamilyDropdown(
    selectedFontFamily: String,
    onFontFamilySelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val systemLabel = stringRes(R.string.settings_editor_font_system)
    val jetbrainsLabel = stringRes(R.string.settings_editor_font_jetbrains)

    val currentLabel = if (selectedFontFamily.contains("jetbrains", ignoreCase = true)) {
        jetbrainsLabel
    } else {
        systemLabel
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = currentLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringRes(R.string.settings_editor_font_family)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text(systemLabel) },
                onClick = {
                    onFontFamilySelected("")
                    expanded = false
                }
            )
            DropdownMenuItem(
                text = { Text(jetbrainsLabel) },
                onClick = {
                    onFontFamilySelected("fonts/jetbrainsmono_regular.ttf")
                    expanded = false
                }
            )
        }
    }
}

@Composable
private fun EditorToggleRow(
    modifier: Modifier = Modifier,
    title: String,
    description: String? = null,
    checked: Boolean,
    onToggle: () -> Unit
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                if (description != null) {
                    Text(description, style = MaterialTheme.typography.bodySmall)
                }
            }
            Switch(checked = checked, onCheckedChange = { onToggle() })
        }
    }
    Spacer(modifier = Modifier.height(12.dp))
}
