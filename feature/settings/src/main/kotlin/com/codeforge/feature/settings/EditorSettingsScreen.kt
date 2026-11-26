/**
 * Modul: :feature:settings
 * @author Thomas Schmid
 *
 * Editor-Einstellungen im Stil von CodeAssist: Schrift oben, darunter gruppierte Schalter für Anzeige,
 * Eingabe, Gesten, Sprachfunktionen, Indexierung und Datei-Kopf. Farbschema und Code-Stil haben eigene Seiten.
 */
package com.codeforge.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.codeforge.core.common.FileHeaderContext
import com.codeforge.core.common.FileHeaderRenderer
import com.codeforge.core.designsystem.EditorPreviewSpec
import com.codeforge.core.designsystem.LocalEditorPreview
import com.codeforge.core.resources.R
import com.codeforge.core.resources.stringRes
import com.codeforge.core.ui.SettingsChipsRow
import com.codeforge.core.ui.SettingsDivider
import com.codeforge.core.ui.SettingsGroup
import com.codeforge.core.ui.SettingsHint
import com.codeforge.core.ui.SettingsLinkRow
import com.codeforge.core.ui.SettingsScaffold
import com.codeforge.core.ui.SettingsSectionLabel
import com.codeforge.core.ui.SettingsSliderRow
import com.codeforge.core.ui.SettingsSwitchRow

/** Route der Editor-Einstellungen. */
@Composable
fun EditorSettingsRoute(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onNavigateToLsp: () -> Unit = {},
    viewModel: EditorSettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    EditorSettingsScreen(
        modifier = modifier,
        uiState = uiState,
        onBack = onBack,
        onNavigateToLsp = onNavigateToLsp,
        onEvent = viewModel::onEvent
    )
}

/** Schriftarten des Editors (Asset-Pfad, leer = System). */
internal enum class EditorFontChoice(val assetPath: String) {
    SYSTEM(""),
    JETBRAINS_MONO("fonts/jetbrainsmono_regular.ttf");

    companion object {
        fun fromPath(path: String): EditorFontChoice =
            if (path.contains("jetbrains", ignoreCase = true)) JETBRAINS_MONO else SYSTEM
    }
}

@Composable
private fun EditorSettingsScreen(
    uiState: EditorSettingsUiState,
    onEvent: (EditorSettingsUiEvent) -> Unit,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onNavigateToLsp: () -> Unit = {}
) {
    SettingsScaffold(title = stringRes(R.string.common_editor), onBack = onBack, modifier = modifier) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
        ) {
            FontGroup(uiState, onEvent)
            DisplayGroup(uiState, onEvent)
            CursorGroup(uiState, onEvent)
            InputGroup(uiState, onEvent)
            GesturesGroup(uiState, onEvent)
            LanguageGroup(uiState, onEvent, onNavigateToLsp)
            IndexingGroup(uiState, onEvent)
            FileHeaderGroup(uiState, onEvent)
        }
    }
}

@Composable
private fun ResetLabel(title: String, onReset: () -> Unit) {
    SettingsSectionLabel(title) {
        TextButton(onClick = onReset) {
            Text(stringRes(R.string.settings_editor_reset_section), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun FontGroup(uiState: EditorSettingsUiState, onEvent: (EditorSettingsUiEvent) -> Unit) {
    var fontSize by remember(uiState.fontSize) { mutableFloatStateOf(uiState.fontSize.toFloat()) }
    var spacing by remember(uiState.lineSpacingPercent) { mutableFloatStateOf(uiState.lineSpacingPercent.toFloat()) }
    var letters by remember(uiState.letterSpacingPercent) { mutableFloatStateOf(uiState.letterSpacingPercent.toFloat()) }
    ResetLabel(stringRes(R.string.settings_editor_section_display)) {
        onEvent(EditorSettingsUiEvent.ResetSection(EditorSettingsSection.DISPLAY))
    }
    SettingsGroup {
        SettingsSliderRow(
            title = stringRes(R.string.settings_editor_font_size_slider),
            value = fontSize,
            onValueChange = { fontSize = it },
            onValueChangeFinished = { onEvent(EditorSettingsUiEvent.FontSizeChanged(fontSize.toInt())) },
            valueRange = 8f..32f,
            steps = 23,
            valueLabel = "${fontSize.toInt()} sp"
        )
        SettingsChipsRow(
            title = stringRes(R.string.settings_editor_font_family),
            options = EditorFontChoice.entries,
            selected = EditorFontChoice.fromPath(uiState.fontFamily),
            label = {
                when (it) {
                    EditorFontChoice.SYSTEM -> stringRes(R.string.settings_editor_font_system)
                    EditorFontChoice.JETBRAINS_MONO -> stringRes(R.string.settings_editor_font_jetbrains)
                }
            },
            onSelect = { onEvent(EditorSettingsUiEvent.FontFamilyChanged(it.assetPath)) }
        )
        SettingsSliderRow(
            title = stringRes(R.string.settings_line_spacing),
            summary = stringRes(R.string.settings_line_spacing_desc),
            value = spacing,
            onValueChange = { spacing = it },
            onValueChangeFinished = { onEvent(EditorSettingsUiEvent.LineSpacingChanged(spacing.toInt())) },
            valueRange = 80f..200f,
            steps = 11,
            valueLabel = "${spacing.toInt()} %"
        )
        SettingsSliderRow(
            title = stringRes(R.string.settings_letter_spacing),
            value = letters,
            onValueChange = { letters = it },
            onValueChangeFinished = { onEvent(EditorSettingsUiEvent.LetterSpacingChanged(letters.toInt())) },
            valueRange = 0f..MAX_LETTER_SPACING,
            steps = MAX_LETTER_SPACING.toInt() - 1,
            valueLabel = String.format(java.util.Locale.ROOT, "%.2f em", letters / PERCENT)
        )
        SettingsChipsRow(
            title = stringRes(R.string.settings_ruler),
            summary = stringRes(R.string.settings_ruler_desc),
            options = RULER_OPTIONS,
            selected = uiState.rulerColumn,
            label = { if (it == 0) stringRes(R.string.settings_ruler_off) else "$it" },
            onSelect = { onEvent(EditorSettingsUiEvent.RulerColumnChanged(it)) }
        )
        SettingsDivider()
        EditorPreviewBox(uiState, EditorPreviewSpec.SAMPLE_KOTLIN, Modifier.padding(12.dp))
    }
}

@Composable
private fun SwitchList(rows: List<SwitchSpec>) {
    rows.forEachIndexed { index, row ->
        if (index > 0) SettingsDivider()
        SettingsSwitchRow(title = row.title, summary = row.summary, checked = row.checked, onCheckedChange = { row.onToggle() })
    }
}

private data class SwitchSpec(val title: String, val summary: String?, val checked: Boolean, val onToggle: () -> Unit)

@Composable
private fun DisplayGroup(uiState: EditorSettingsUiState, onEvent: (EditorSettingsUiEvent) -> Unit) {
    Spacer(Modifier.height(12.dp))
    SettingsGroup {
        SwitchList(
            listOf(
                SwitchSpec(stringRes(R.string.settings_font_ligatures), stringRes(R.string.settings_font_ligatures_desc), uiState.fontLigatures) {
                    onEvent(EditorSettingsUiEvent.FontLigaturesToggled)
                },
                SwitchSpec(stringRes(R.string.settings_code_folding), stringRes(R.string.settings_code_folding_desc), uiState.codeFoldingEnabled) {
                    onEvent(EditorSettingsUiEvent.CodeFoldingToggled)
                },
                SwitchSpec(stringRes(R.string.settings_zeilenumbruch_word_wrap), stringRes(R.string.settings_word_wrap_desc), uiState.wordWrap) {
                    onEvent(EditorSettingsUiEvent.WordWrapToggled)
                },
                SwitchSpec(stringRes(R.string.settings_txt_word_wrap), stringRes(R.string.settings_txt_word_wrap_desc), uiState.txtWordWrap) {
                    onEvent(EditorSettingsUiEvent.TxtWordWrapToggled)
                },
                SwitchSpec(stringRes(R.string.settings_line_numbers), stringRes(R.string.settings_line_numbers_desc), uiState.showLineNumbers) {
                    onEvent(EditorSettingsUiEvent.LineNumbersToggled)
                },
                // Anheften wirkt nur beim seitlichen Scrollen – mit Zeilenumbruch gibt es keins.
                SwitchSpec(
                    stringRes(R.string.settings_pin_line_numbers),
                    stringRes(if (uiState.wordWrap) R.string.settings_pin_line_numbers_wrap_hint else R.string.settings_pin_line_numbers_desc),
                    uiState.pinLineNumbers
                ) { onEvent(EditorSettingsUiEvent.PinLineNumbersToggled) },
                SwitchSpec(stringRes(R.string.settings_sticky_scroll), stringRes(R.string.settings_zeigt_den_umschliessenden_code_block), uiState.stickyScrollEnabled) {
                    onEvent(EditorSettingsUiEvent.StickyScrollToggled)
                },
                SwitchSpec(stringRes(R.string.settings_current_line), stringRes(R.string.settings_current_line_desc), uiState.highlightCurrentLine) {
                    onEvent(EditorSettingsUiEvent.CurrentLineHighlightToggled)
                },
                SwitchSpec(stringRes(R.string.settings_indent_guides), stringRes(R.string.settings_indent_guides_desc), uiState.indentGuides) {
                    onEvent(EditorSettingsUiEvent.IndentGuidesToggled)
                },
                SwitchSpec(stringRes(R.string.settings_scrollbars), stringRes(R.string.settings_scrollbars_desc), uiState.scrollbars) {
                    onEvent(EditorSettingsUiEvent.ScrollbarsToggled)
                },
                SwitchSpec(stringRes(R.string.settings_line_number_selects), stringRes(R.string.settings_line_number_selects_desc), uiState.lineNumberSelectsLine) {
                    onEvent(EditorSettingsUiEvent.LineNumberSelectsLineToggled)
                },
                SwitchSpec(stringRes(R.string.settings_inlay_hints), stringRes(R.string.settings_inlay_hints_desc), uiState.inlayHints) {
                    onEvent(EditorSettingsUiEvent.InlayHintsToggled)
                },
                SwitchSpec(stringRes(R.string.settings_fast_scroll_tip), stringRes(R.string.settings_fast_scroll_tip_desc), uiState.fastScrollLineTip) {
                    onEvent(EditorSettingsUiEvent.FastScrollTipToggled)
                },
                SwitchSpec(stringRes(R.string.settings_fold_marks), stringRes(R.string.settings_fold_marks_desc), uiState.foldMarksAsLines) {
                    onEvent(EditorSettingsUiEvent.FoldMarksToggled)
                },
                SwitchSpec(stringRes(R.string.settings_minimap), stringRes(R.string.settings_minimap_desc), uiState.minimapEnabled) {
                    onEvent(EditorSettingsUiEvent.MinimapToggled)
                },
                SwitchSpec(stringRes(R.string.settings_rainbow_brackets), stringRes(R.string.settings_rainbow_brackets_desc), uiState.rainbowBracketsEnabled) {
                    onEvent(EditorSettingsUiEvent.RainbowBracketsToggled)
                },
                SwitchSpec(stringRes(R.string.settings_color_previews), stringRes(R.string.settings_color_previews_desc), uiState.colorPreviewsEnabled) {
                    onEvent(EditorSettingsUiEvent.ColorPreviewsToggled)
                },
                SwitchSpec(
                    stringRes(R.string.settings_nicht_druckbare_zeichen_anzeigen),
                    stringRes(R.string.settings_leerzeichen_tabs_und_zeilenumbrueche_a),
                    uiState.showNonPrintableChars
                ) { onEvent(EditorSettingsUiEvent.NonPrintableCharsToggled) },
                SwitchSpec(stringRes(R.string.settings_read_only_default), stringRes(R.string.settings_read_only_default_desc), uiState.readOnlyDefault) {
                    onEvent(EditorSettingsUiEvent.ReadOnlyDefaultToggled)
                }
            )
        )
        if (uiState.stickyScrollEnabled) {
            SettingsDivider()
            SettingsChipsRow(
                title = stringRes(R.string.settings_sticky_lines),
                options = STICKY_LINE_OPTIONS,
                selected = uiState.stickyScrollMaxLines,
                label = { "$it" },
                onSelect = { onEvent(EditorSettingsUiEvent.StickyScrollMaxLinesChanged(it)) }
            )
        }
        SettingsDivider()
        SettingsChipsRow(
            title = stringRes(R.string.settings_diagnostic_style),
            options = DIAGNOSTIC_STYLES.indices.toList(),
            selected = uiState.diagnosticStyle,
            label = { stringRes(DIAGNOSTIC_STYLES[it]) },
            onSelect = { onEvent(EditorSettingsUiEvent.DiagnosticStyleChanged(it)) }
        )
    }
}

private val STICKY_LINE_OPTIONS = listOf(1, 2, 3, 5, 8)
private val RULER_OPTIONS = listOf(0, 80, 100, 120)
private const val MAX_LETTER_SPACING = 20f
private const val PERCENT = 100f
private val MOUSE_MODES = listOf(R.string.settings_mouse_auto, R.string.settings_mouse_always, R.string.settings_mouse_never)
private val HANDLE_STYLES = listOf(R.string.settings_handle_drop, R.string.settings_handle_side_drop)
private val DIAGNOSTIC_STYLES = listOf(
    R.string.settings_diagnostic_wavy,
    R.string.settings_diagnostic_line,
    R.string.settings_diagnostic_double,
    R.string.settings_diagnostic_none
)
private val CURSOR_ANIMATIONS = listOf(
    R.string.settings_cursor_anim_move,
    R.string.settings_cursor_anim_fade,
    R.string.settings_cursor_anim_scale
)

@Composable
private fun CursorGroup(uiState: EditorSettingsUiState, onEvent: (EditorSettingsUiEvent) -> Unit) {
    SettingsSectionLabel(stringRes(R.string.settings_section_cursor))
    SettingsGroup {
        SettingsSwitchRow(
            title = stringRes(R.string.settings_cursor_blink),
            checked = uiState.cursorBlink,
            onCheckedChange = { onEvent(EditorSettingsUiEvent.CursorBlinkToggled) }
        )
        SettingsDivider()
        SettingsSwitchRow(
            title = stringRes(R.string.settings_cursor_animation),
            summary = stringRes(R.string.settings_cursor_animation_desc),
            checked = uiState.cursorAnimationEnabled,
            onCheckedChange = { onEvent(EditorSettingsUiEvent.CursorAnimationToggled) }
        )
        if (uiState.cursorAnimationEnabled) {
            SettingsChipsRow(
                title = stringRes(R.string.settings_cursor_anim_style),
                options = CURSOR_ANIMATIONS.indices.toList(),
                selected = uiState.cursorAnimationStyle,
                label = { stringRes(CURSOR_ANIMATIONS[it]) },
                onSelect = { onEvent(EditorSettingsUiEvent.CursorAnimationStyleChanged(it)) }
            )
        }
    }
}

@Composable
private fun Stepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        IconButton(onClick = onMinus) { Text("–") }
        Text(value, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        IconButton(onClick = onPlus) { Text("+") }
    }
}

@Composable
private fun InputGroup(uiState: EditorSettingsUiState, onEvent: (EditorSettingsUiEvent) -> Unit) {
    ResetLabel(stringRes(R.string.settings_editor_section_input)) {
        onEvent(EditorSettingsUiEvent.ResetSection(EditorSettingsSection.INPUT))
    }
    SettingsGroup {
        SwitchList(
            listOf(
                SwitchSpec(stringRes(R.string.settings_symbol_bar_enabled), stringRes(R.string.settings_symbol_bar_enabled_desc), uiState.symbolBarEnabled) {
                    onEvent(EditorSettingsUiEvent.SymbolBarToggled)
                },
                SwitchSpec(stringRes(R.string.settings_format_on_paste), stringRes(R.string.settings_format_on_paste_desc), uiState.formatOnPaste) {
                    onEvent(EditorSettingsUiEvent.FormatOnPasteToggled)
                },
                SwitchSpec(stringRes(R.string.settings_delete_empty_line_fast), stringRes(R.string.settings_delete_empty_line_fast_desc), uiState.deleteEmptyLineFast) {
                    onEvent(EditorSettingsUiEvent.DeleteEmptyLineFastToggled)
                },
                SwitchSpec(stringRes(R.string.settings_backspace_deletes_tab), stringRes(R.string.settings_backspace_deletes_tab_desc), uiState.backspaceDeletesTab) {
                    onEvent(EditorSettingsUiEvent.BackspaceDeletesTabToggled)
                },
                SwitchSpec(stringRes(R.string.settings_klammern_anfuehrungszeichen_automatisc), null, uiState.symbolPairAutocompleteEnabled) {
                    onEvent(EditorSettingsUiEvent.SymbolPairAutocompleteToggled)
                },
                SwitchSpec(stringRes(R.string.settings_keyboard_suggestions), stringRes(R.string.settings_keyboard_suggestions_desc), uiState.keyboardSuggestions) {
                    onEvent(EditorSettingsUiEvent.KeyboardSuggestionsToggled)
                },
                SwitchSpec(
                    stringRes(R.string.settings_disable_soft_keyboard),
                    stringRes(R.string.settings_disable_soft_keyboard_desc),
                    uiState.disableSoftKeyboardWithHardwareKeyboard
                ) { onEvent(EditorSettingsUiEvent.DisableSoftKeyboardToggled) },
                SwitchSpec(stringRes(R.string.settings_desktop_shortcuts), stringRes(R.string.settings_desktop_shortcuts_desc), uiState.desktopShortcutsEnabled) {
                    onEvent(EditorSettingsUiEvent.DesktopShortcutsToggled)
                }
            )
        )
    }
}

@Composable
private fun GesturesGroup(uiState: EditorSettingsUiState, onEvent: (EditorSettingsUiEvent) -> Unit) {
    SettingsSectionLabel(stringRes(R.string.settings_section_gestures))
    SettingsGroup {
        SettingsChipsRow(
            title = stringRes(R.string.settings_mouse_mode),
            summary = stringRes(R.string.settings_mouse_mode_desc),
            options = MOUSE_MODES.indices.toList(),
            selected = uiState.mouseMode,
            label = { stringRes(MOUSE_MODES[it]) },
            onSelect = { onEvent(EditorSettingsUiEvent.MouseModeChanged(it)) }
        )
        SettingsChipsRow(
            title = stringRes(R.string.settings_handle_style),
            options = HANDLE_STYLES.indices.toList(),
            selected = uiState.selectionHandleStyle,
            label = { stringRes(HANDLE_STYLES[it]) },
            onSelect = { onEvent(EditorSettingsUiEvent.SelectionHandleStyleChanged(it)) }
        )
        SettingsDivider()
        SwitchList(
            listOf(
                SwitchSpec(stringRes(R.string.settings_pinch_to_zoom), stringRes(R.string.settings_pinch_to_zoom_desc), uiState.pinchToZoomEnabled) {
                    onEvent(EditorSettingsUiEvent.PinchToZoomToggled)
                },
                SwitchSpec(stringRes(R.string.settings_text_lupe_magnifier), stringRes(R.string.settings_vergroesserungsglas_bei_cursor_platzie), uiState.magnifierEnabled) {
                    onEvent(EditorSettingsUiEvent.MagnifierToggled)
                },
                SwitchSpec(stringRes(R.string.settings_icu_word_selection), stringRes(R.string.settings_icu_word_selection_desc), uiState.icuWordSelection) {
                    onEvent(EditorSettingsUiEvent.IcuWordSelectionToggled)
                }
            )
        )
    }
}

@Composable
private fun LanguageGroup(uiState: EditorSettingsUiState, onEvent: (EditorSettingsUiEvent) -> Unit, onNavigateToLsp: () -> Unit) {
    ResetLabel(stringRes(R.string.settings_editor_section_language)) {
        onEvent(EditorSettingsUiEvent.ResetSection(EditorSettingsSection.LANGUAGE))
    }
    SettingsGroup {
        SettingsSwitchRow(
            title = stringRes(R.string.settings_tree_sitter_verwenden),
            summary = stringRes(R.string.settings_praeziseres_syntax_highlighting_statt),
            checked = uiState.useTreeSitter,
            onCheckedChange = { onEvent(EditorSettingsUiEvent.TreeSitterToggled) }
        )
        SettingsDivider()
        SettingsLinkRow(
            title = stringRes(R.string.settings_editor_lsp_manage),
            summary = stringRes(R.string.settings_editor_lsp_manage_desc),
            icon = Icons.Filled.Dns,
            onClick = onNavigateToLsp
        )
        SettingsDivider()
        Stepper(
            label = stringRes(R.string.settings_lsp_max_sessions_label),
            value = "${uiState.lspMaxConcurrentSessions}",
            onMinus = { onEvent(EditorSettingsUiEvent.LspMaxConcurrentSessionsChanged((uiState.lspMaxConcurrentSessions - 1).coerceAtLeast(1))) },
            onPlus = { onEvent(EditorSettingsUiEvent.LspMaxConcurrentSessionsChanged((uiState.lspMaxConcurrentSessions + 1).coerceAtMost(10))) }
        )
        Stepper(
            label = stringRes(R.string.settings_lsp_idle_timeout_label),
            value = "${uiState.lspIdleTimeoutMinutes}m",
            onMinus = { onEvent(EditorSettingsUiEvent.LspIdleTimeoutMinutesChanged((uiState.lspIdleTimeoutMinutes - 1).coerceAtLeast(1))) },
            onPlus = { onEvent(EditorSettingsUiEvent.LspIdleTimeoutMinutesChanged((uiState.lspIdleTimeoutMinutes + 5).coerceAtMost(120))) }
        )
    }
}

@Composable
private fun IndexingGroup(uiState: EditorSettingsUiState, onEvent: (EditorSettingsUiEvent) -> Unit) {
    ResetLabel(stringRes(R.string.settings_editor_section_indexing)) {
        onEvent(EditorSettingsUiEvent.ResetSection(EditorSettingsSection.INDEXING))
    }
    SettingsGroup {
        SwitchList(
            listOf(
                SwitchSpec(stringRes(R.string.settings_project_indexing), stringRes(R.string.settings_project_indexing_desc), uiState.projectIndexingEnabled) {
                    onEvent(EditorSettingsUiEvent.ProjectIndexingToggled)
                },
                SwitchSpec(stringRes(R.string.settings_j2k_enabled), stringRes(R.string.settings_j2k_enabled_desc), uiState.j2kEnabled) {
                    onEvent(EditorSettingsUiEvent.J2kToggled)
                },
                SwitchSpec(stringRes(R.string.settings_x2c_enabled), stringRes(R.string.settings_x2c_enabled_desc), uiState.x2cEnabled) {
                    onEvent(EditorSettingsUiEvent.X2cToggled)
                }
            )
        )
    }
}

@Composable
private fun FileHeaderGroup(uiState: EditorSettingsUiState, onEvent: (EditorSettingsUiEvent) -> Unit) {
    SettingsSectionLabel(stringRes(R.string.settings_editor_section_file_header))
    SettingsGroup {
        SettingsSwitchRow(
            title = stringRes(R.string.settings_editor_file_header_enabled),
            checked = uiState.fileHeaderEnabled,
            onCheckedChange = { onEvent(EditorSettingsUiEvent.FileHeaderEnabledToggled) }
        )
        if (uiState.fileHeaderEnabled) {
            FileHeaderEditor(uiState, onEvent)
        }
    }
}

@Composable
private fun ColumnScope.FileHeaderEditor(uiState: EditorSettingsUiState, onEvent: (EditorSettingsUiEvent) -> Unit) {
    OutlinedTextField(
        value = uiState.fileHeaderAuthor,
        onValueChange = { onEvent(EditorSettingsUiEvent.FileHeaderAuthorChanged(it)) },
        label = { Text(stringRes(R.string.settings_editor_file_header_author)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
    )
    val sampleHeader = FileHeaderRenderer.render(
        extension = "kt",
        context = FileHeaderContext(
            filename = "Sample.kt",
            moduleName = ":feature:sample",
            author = uiState.fileHeaderAuthor.ifBlank { "Author" },
            year = "2026"
        )
    ).orEmpty()
    SettingsHint(stringRes(R.string.settings_editor_file_header_preview))
    Text(
        text = sampleHeader,
        fontFamily = FontFamily.Monospace,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
    )
}

/** Live-Vorschau: Theme, Schrift, Größe, Zeilenabstand, Zeilennummern, Umbruch und Ligaturen wie im Editor. */
@Composable
internal fun EditorPreviewBox(uiState: EditorSettingsUiState, sample: String, modifier: Modifier = Modifier) {
    val preview = LocalEditorPreview.current ?: return
    val spec = remember(
        uiState.textmateTheme, uiState.fontSize, uiState.fontFamily, uiState.showLineNumbers, uiState.wordWrap,
        uiState.lineSpacingPercent, uiState.fontLigatures, uiState.showNonPrintableChars, uiState.tabSize,
        uiState.useTreeSitter, sample,
    ) {
        EditorPreviewSpec(
            theme = uiState.textmateTheme,
            fontSizeSp = uiState.fontSize,
            fontFamily = uiState.fontFamily,
            showLineNumbers = uiState.showLineNumbers,
            wordWrap = uiState.wordWrap,
            lineSpacingPercent = uiState.lineSpacingPercent,
            fontLigatures = uiState.fontLigatures,
            showNonPrintableChars = uiState.showNonPrintableChars,
            tabSize = uiState.tabSize,
            useTreeSitter = uiState.useTreeSitter,
            sample = sample,
        )
    }
    preview(spec, modifier.fillMaxWidth().height(PREVIEW_HEIGHT_DP.dp).clip(MaterialTheme.shapes.medium))
}

private const val PREVIEW_HEIGHT_DP = 220
