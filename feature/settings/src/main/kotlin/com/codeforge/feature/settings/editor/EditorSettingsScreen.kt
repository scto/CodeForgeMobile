@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
/**
 * Modul: :feature:settings:editor
 * @author Thomas Schmid
 */
package com.codeforge.feature.settings.editor

import com.codeforge.core.resources.ResGetter

import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun EditorSettingsRoute(
    onNavigateBack: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: EditorSettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    EditorSettingsScreen(
        onNavigateBack = onNavigateBack,
        modifier = modifier,
        uiState = uiState,
        onEvent = viewModel::onEvent
    )
}

@Composable
private fun EditorSettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    uiState: EditorSettingsUiState,
    onEvent: (EditorSettingsUiEvent) -> Unit
) {
    val config = uiState.editorConfig
    val documentTreeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        uri?.let {
            val docId = DocumentsContract.getTreeDocumentId(it)
            val split = docId.split(":")
            val path = if (split[0].equals("primary", ignoreCase = true)) {
                android.os.Environment.getExternalStorageDirectory().toString() + "/" + split.getOrElse(1) { "" }
            } else {
                "/storage/${split[0]}/${split.getOrElse(1) { "" }}"
            }
            onEvent(EditorSettingsUiEvent.WorkspaceDirectoryChanged(path))
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = ResGetter.get(com.codeforge.core.resources.R.string.action_back)
                        )
                    }
                },
                title = { Text(ResGetter.get(com.codeforge.core.resources.R.string.editor_settings_title)) }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // --- ABSCHNITT 1: General (Allgemein) ---
            SectionHeader(title = ResGetter.get(com.codeforge.core.resources.R.string.feature_settings_editor_gen_title))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SettingSwitchRow(
                        title = ResGetter.get(com.codeforge.core.resources.R.string.feature_settings_editor_hover_info),
                        subtitle = "LSP Hover-Informationen und Dokumentation anzeigen",
                        checked = config.hoverInfoEnabled,
                        onCheckedChange = { onEvent(EditorSettingsUiEvent.UpdateEditorConfig { b -> b.setHoverInfoEnabled(it) }) }
                    )

                    SettingSwitchRow(
                        title = "Inlay Hints",
                        subtitle = "Inlay-Parameter- und Typ-Hinweise im Code einblenden",
                        checked = config.inlayHintsEnabled,
                        onCheckedChange = { onEvent(EditorSettingsUiEvent.UpdateEditorConfig { b -> b.setInlayHintsEnabled(it) }) }
                    )

                    SettingSwitchRow(
                        title = "Magnifier (Lupe)",
                        subtitle = "Lupe beim Auswählen von Text anzeigen",
                        checked = config.magnifierEnabled,
                        onCheckedChange = { onEvent(EditorSettingsUiEvent.UpdateEditorConfig { b -> b.setMagnifierEnabled(it) }) }
                    )

                    SettingSwitchRow(
                        title = "Schriftart-Ligaturen",
                        subtitle = "Programmier-Ligaturen (->, ==, !=, =>) aktivieren",
                        checked = config.ligatureEnabled,
                        onCheckedChange = { onEvent(EditorSettingsUiEvent.UpdateEditorConfig { b -> b.setLigatureEnabled(it) }) }
                    )

                    SettingSwitchRow(
                        title = "Cursor-Animation",
                        subtitle = "Weichen Übergang beim Bewegen des Cursors aktivieren",
                        checked = config.cursorAnimation,
                        onCheckedChange = { onEvent(EditorSettingsUiEvent.UpdateEditorConfig { b -> b.setCursorAnimation(it) }) }
                    )

                    SettingSwitchRow(
                        title = "Signatur-Hilfe",
                        subtitle = "LSP Signatur-Hilfe bei Funktionsaufrufen anzeigen",
                        checked = config.signatureHelpEnabled,
                        onCheckedChange = { onEvent(EditorSettingsUiEvent.UpdateEditorConfig { b -> b.setSignatureHelpEnabled(it) }) }
                    )

                    SettingSwitchRow(
                        title = "Symbol-Leiste anzeigen",
                        subtitle = "Schnellzugriffsleiste für Symbole unter dem Editor einblenden",
                        checked = config.symbolBarVisible,
                        onCheckedChange = { onEvent(EditorSettingsUiEvent.UpdateEditorConfig { b -> b.setSymbolBarVisible(it) }) }
                    )

                    SettingSwitchRow(
                        title = "Zeilenumbruch (Word Wrap)",
                        subtitle = "Lange Zeilen automatisch an der Bildschirmschranke umbrechen",
                        checked = config.wordWrap,
                        onCheckedChange = { onEvent(EditorSettingsUiEvent.UpdateEditorConfig { b -> b.setWordWrap(it) }) }
                    )
                }
            }

            // --- ABSCHNITT 2: Themes & Farbschemata (Visuelle Vorschau) ---
            SectionHeader(title = "Editor Themes & Farbschemata")

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val currentThemeKey = config.textmateTheme.ifBlank { "codeforge" }
                EDITOR_THEME_OPTIONS.forEach { theme ->
                    EditorThemePreviewCard(
                        theme = theme,
                        isSelected = currentThemeKey.equals(theme.key, ignoreCase = true),
                        onSelect = {
                            onEvent(EditorSettingsUiEvent.UpdateEditorConfig { b -> b.setTextmateTheme(theme.key) })
                        }
                    )
                }
            }

            // --- ABSCHNITT 3: Display (Darstellung) ---
            SectionHeader(title = "Display (Darstellung)")

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        val currentFontSize = if (config.fontSize > 0) config.fontSize else 14
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Textgröße", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = "${currentFontSize} sp",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Slider(
                            value = currentFontSize.toFloat(),
                            onValueChange = { newValue ->
                                onEvent(EditorSettingsUiEvent.UpdateEditorConfig { b -> b.setFontSize(newValue.toInt()) })
                            },
                            valueRange = 8f..36f,
                            steps = 27,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Column(modifier = Modifier.fillMaxWidth()) {
                        val currentTabSize = if (config.tabSize > 0) config.tabSize else 4
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Tab-Größe (Einrückung)", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = "$currentTabSize Leerzeichen",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Slider(
                            value = currentTabSize.toFloat(),
                            onValueChange = { newValue ->
                                onEvent(EditorSettingsUiEvent.UpdateEditorConfig { b -> b.setTabSize(newValue.toInt()) })
                            },
                            valueRange = 1f..8f,
                            steps = 6,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    SettingSwitchRow(
                        title = "Vervollständigungs-Animation",
                        subtitle = "Animation des Autovervollständigungs-Fensters aktivieren",
                        checked = config.completionAnimEnabled,
                        onCheckedChange = { onEvent(EditorSettingsUiEvent.UpdateEditorConfig { b -> b.setCompletionAnimEnabled(it) }) }
                    )

                    SettingSwitchRow(
                        title = "Zeilennummern anzeigen",
                        subtitle = "Fortlaufende Zeilennummern links im Editor anzeigen",
                        checked = config.showLineNumbers,
                        onCheckedChange = { onEvent(EditorSettingsUiEvent.UpdateEditorConfig { b -> b.setShowLineNumbers(it) }) }
                    )

                    SettingSwitchRow(
                        title = "Zeilennummern anheften",
                        subtitle = "Zeilennummern beim horizontalen Scrollen fixieren",
                        checked = config.pinLineNumbers,
                        onCheckedChange = { onEvent(EditorSettingsUiEvent.UpdateEditorConfig { b -> b.setPinLineNumbers(it) }) }
                    )

                    SettingSwitchRow(
                        title = "Minimap anzeigen",
                        subtitle = "Miniatur-Codeübersicht am rechten Rand einblenden",
                        checked = config.showMinimap,
                        onCheckedChange = { onEvent(EditorSettingsUiEvent.UpdateEditorConfig { b -> b.setShowMinimap(it) }) }
                    )

                    SettingSwitchRow(
                        title = "Aktuelle Zeile hervorheben",
                        subtitle = "Die Zeile mit dem Cursor farblich hervorheben",
                        checked = config.highlightCurrentLineEnabled,
                        onCheckedChange = { onEvent(EditorSettingsUiEvent.UpdateEditorConfig { b -> b.setHighlightCurrentLineEnabled(it) }) }
                    )

                    SettingSwitchRow(
                        title = "Aktuellen Block hervorheben",
                        subtitle = "Zusammengehörenden Codeblock farblich hervorheben",
                        checked = config.highlightCurrentBlockEnabled,
                        onCheckedChange = { onEvent(EditorSettingsUiEvent.UpdateEditorConfig { b -> b.setHighlightCurrentBlockEnabled(it) }) }
                    )

                    SettingSwitchRow(
                        title = "Sticky Textauswahl",
                        subtitle = "Textauswahl-Markierung beim Scrollen fixiert halten",
                        checked = config.stickyTextSelectionEnabled,
                        onCheckedChange = { onEvent(EditorSettingsUiEvent.UpdateEditorConfig { b -> b.setStickyTextSelectionEnabled(it) }) }
                    )

                    SettingSwitchRow(
                        title = "Scrollbalken anzeigen",
                        subtitle = "Horizontale und vertikale Scrollbalken anzeigen",
                        checked = config.scrollbarEnabled,
                        onCheckedChange = { onEvent(EditorSettingsUiEvent.UpdateEditorConfig { b -> b.setScrollbarEnabled(it) }) }
                    )

                    SettingSwitchRow(
                        title = "Erste Zeilennummer immer sichtbar",
                        subtitle = "Zeile 1 auch bei weitem Scrollen immer anzeigen",
                        checked = config.firstLineNumberAlwaysVisible,
                        onCheckedChange = { onEvent(EditorSettingsUiEvent.UpdateEditorConfig { b -> b.setFirstLineNumberAlwaysVisible(it) }) }
                    )

                    SettingSwitchRow(
                        title = "Bidi-Richtungsindikator",
                        subtitle = "Indikator für bidirektionale Texte (RTL/LTR) anzeigen",
                        checked = config.bidiIndicatorEnabled,
                        onCheckedChange = { onEvent(EditorSettingsUiEvent.UpdateEditorConfig { b -> b.setBidiIndicatorEnabled(it) }) }
                    )

                    SettingSwitchRow(
                        title = "Abgerundeter Text-Hintergrund",
                        subtitle = "Text-Hintergründe mit abgerundeten Ecken rendern",
                        checked = config.roundTextBackgroundEnabled,
                        onCheckedChange = { onEvent(EditorSettingsUiEvent.UpdateEditorConfig { b -> b.setRoundTextBackgroundEnabled(it) }) }
                    )

                    SettingSwitchRow(
                        title = "Side Block Line",
                        subtitle = "Vertikale Block-Verbindungslinien am Rand zeichnen",
                        checked = config.sideBlockLineEnabled,
                        onCheckedChange = { onEvent(EditorSettingsUiEvent.UpdateEditorConfig { b -> b.setSideBlockLineEnabled(it) }) }
                    )

                    SettingSwitchRow(
                        title = "Sticky Scroll",
                        subtitle = "Die aktuelle Codeblock-Überschrift oben fixieren",
                        checked = config.stickyScroll,
                        onCheckedChange = { onEvent(EditorSettingsUiEvent.UpdateEditorConfig { b -> b.setStickyScroll(it) }) }
                    )

                    SettingSwitchRow(
                        title = "Scroll Fling",
                        subtitle = "Schwungvolles Scrollen (Fling) aktivieren",
                        checked = config.scrollFlingEnabled,
                        onCheckedChange = { onEvent(EditorSettingsUiEvent.UpdateEditorConfig { b -> b.setScrollFlingEnabled(it) }) }
                    )

                    SettingSwitchRow(
                        title = "Overscroll Effekt",
                        subtitle = "Overscroll-Effekt an den Grenzen des Editors aktivieren",
                        checked = config.overscrollEnabled,
                        onCheckedChange = { onEvent(EditorSettingsUiEvent.UpdateEditorConfig { b -> b.setOverscrollEnabled(it) }) }
                    )
                }
            }

            // --- ABSCHNITT 3: CodeEditing ---
            SectionHeader(title = "CodeEditing (Code-Bearbeitung)")

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SettingSwitchRow(
                        title = "Auto Indent (Automatische Einrückung)",
                        subtitle = "Neue Zeilen automatisch an der vorherigen Einrückung ausrichten",
                        checked = config.autoIndentEnabled,
                        onCheckedChange = { onEvent(EditorSettingsUiEvent.UpdateEditorConfig { b -> b.setAutoIndentEnabled(it) }) }
                    )

                    SettingSwitchRow(
                        title = "Mehrfache Leerzeichen löschen",
                        subtitle = "Beim Rückschritt komplette Tab-Schritte löschen",
                        checked = config.deleteMultiSpacesEnabled,
                        onCheckedChange = { onEvent(EditorSettingsUiEvent.UpdateEditorConfig { b -> b.setDeleteMultiSpacesEnabled(it) }) }
                    )

                    SettingSwitchRow(
                        title = "Klammern-Hervorhebung",
                        subtitle = "Passende zusammengehörende Klammern farblich markieren",
                        checked = config.bracketHighlightEnabled,
                        onCheckedChange = { onEvent(EditorSettingsUiEvent.UpdateEditorConfig { b -> b.setBracketHighlightEnabled(it) }) }
                    )

                    SettingSwitchRow(
                        title = "Klammern fett darstellen",
                        subtitle = "Zusammengehörende Klammern fett gedruckt hervorheben",
                        checked = config.boldMatchingBracketsEnabled,
                        onCheckedChange = { onEvent(EditorSettingsUiEvent.UpdateEditorConfig { b -> b.setBoldMatchingBracketsEnabled(it) }) }
                    )

                    SettingSwitchRow(
                        title = "Leere Zeilen schnell löschen",
                        subtitle = "Leere Zeilen bei Rückschritt sofort entfernen",
                        checked = config.deleteEmptyLineFastEnabled,
                        onCheckedChange = { onEvent(EditorSettingsUiEvent.UpdateEditorConfig { b -> b.setDeleteEmptyLineFastEnabled(it) }) }
                    )

                    SettingSwitchRow(
                        title = "Eingefügten Text formatieren",
                        subtitle = "Eingefügten Code automatisch an die Einrückung anpassen",
                        checked = config.formatPastedTextEnabled,
                        onCheckedChange = { onEvent(EditorSettingsUiEvent.UpdateEditorConfig { b -> b.setFormatPastedTextEnabled(it) }) }
                    )

                    SettingSwitchRow(
                        title = "Symbolpaar-Vervollständigung",
                        subtitle = "Klammern und Anführungszeichen automatisch paarweise einfügen",
                        checked = config.symbolPairCompletionEnabled,
                        onCheckedChange = { onEvent(EditorSettingsUiEvent.UpdateEditorConfig { b -> b.setSymbolPairCompletionEnabled(it) }) }
                    )

                    SettingSwitchRow(
                        title = ResGetter.get(com.codeforge.core.resources.R.string.feature_settings_editor_icu_lib),
                        subtitle = "Erweiterte ICU-Wortauswahl bei Doppel-Tippen nutzen",
                        checked = config.useIcuEnabled,
                        onCheckedChange = { onEvent(EditorSettingsUiEvent.UpdateEditorConfig { b -> b.setUseIcuEnabled(it) }) }
                    )
                }
            }

            // --- ABSCHNITT 4: Input Method (Eingabemethoden) ---
            SectionHeader(title = ResGetter.get(com.codeforge.core.resources.R.string.feature_settings_editor_input_method))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SettingSwitchRow(
                        title = ResGetter.get(com.codeforge.core.resources.R.string.feature_settings_editor_soft_kbd),
                        subtitle = "Bildschirmtastatur beim Tippen im Editor verwenden",
                        checked = config.softKeyboardEnabled,
                        onCheckedChange = { onEvent(EditorSettingsUiEvent.UpdateEditorConfig { b -> b.setSoftKeyboardEnabled(it) }) }
                    )

                    SettingSwitchRow(
                        title = ResGetter.get(com.codeforge.core.resources.R.string.feature_settings_editor_hide_soft_kbd),
                        subtitle = "Bildschirmtastatur automatisch ausblenden wenn Hardware-Tastatur angeschlossen ist",
                        checked = config.disableSoftKbdIfHardKbd,
                        onCheckedChange = { onEvent(EditorSettingsUiEvent.UpdateEditorConfig { b -> b.setDisableSoftKbdIfHardKbd(it) }) }
                    )

                    SettingSwitchRow(
                        title = ResGetter.get(com.codeforge.core.resources.R.string.feature_settings_editor_advanced_home_end),
                        subtitle = "Pos1/Ende springt zuerst zum ersten Zeichen, dann zum Zeilenanfang",
                        checked = config.enhancedHomeEndEnabled,
                        onCheckedChange = { onEvent(EditorSettingsUiEvent.UpdateEditorConfig { b -> b.setEnhancedHomeEndEnabled(it) }) }
                    )

                    SettingSwitchRow(
                        title = ResGetter.get(com.codeforge.core.resources.R.string.feature_settings_editor_line_home_end),
                        subtitle = "Pos1/Ende am visuellen Umbruchzeilenrand ausrichten",
                        checked = config.rowBasedHomeEndEnabled,
                        onCheckedChange = { onEvent(EditorSettingsUiEvent.UpdateEditorConfig { b -> b.setRowBasedHomeEndEnabled(it) }) }
                    )

                    SettingSwitchRow(
                        title = ResGetter.get(com.codeforge.core.resources.R.string.feature_settings_editor_completion_typing),
                        subtitle = "Autovervollständigung auch während aktiver Tastatureingabe (Composing) anzeigen",
                        checked = config.autoCompletionOnComposingEnabled,
                        onCheckedChange = { onEvent(EditorSettingsUiEvent.UpdateEditorConfig { b -> b.setAutoCompletionOnComposingEnabled(it) }) }
                    )

                    SettingSwitchRow(
                        title = ResGetter.get(com.codeforge.core.resources.R.string.feature_settings_editor_fullscreen_mode),
                        subtitle = "Tastatur-Vollbildmodus auf mobilen Geräten zulassen",
                        checked = config.allowFullscreenEnabled,
                        onCheckedChange = { onEvent(EditorSettingsUiEvent.UpdateEditorConfig { b -> b.setAllowFullscreenEnabled(it) }) }
                    )

                    SettingSwitchRow(
                        title = ResGetter.get(com.codeforge.core.resources.R.string.feature_settings_editor_mouse_context),
                        subtitle = "Rechtsklick-Kontextmenü für Maussteuerung aktivieren",
                        checked = config.mouseContextMenuEnabled,
                        onCheckedChange = { onEvent(EditorSettingsUiEvent.UpdateEditorConfig { b -> b.setMouseContextMenuEnabled(it) }) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                uncheckedBorderColor = MaterialTheme.colorScheme.outline
            )
        )
    }
}

data class EditorThemeOption(
    val key: String,
    val name: String,
    val isDark: Boolean,
    val backgroundColor: Color,
    val lineNumberColor: Color,
    val keywordColor: Color,
    val stringColor: Color,
    val textColor: Color
)

val EDITOR_THEME_OPTIONS = listOf(
    // --- Native Kotlin Color Schemes ---
    EditorThemeOption("CodeForge 2 Dark", "CodeForge 2 Dark", true, Color(0xFF121318), Color(0xFF707070), Color(0xFFFF4D4D), Color(0xFF6A8759), Color(0xFFDFDFDF)),
    EditorThemeOption("CodeForge 2 Light", "CodeForge 2 Light", false, Color(0xFFFAFAFA), Color(0xFF888888), Color(0xFFD32F2F), Color(0xFF547C44), Color(0xFF242730)),
    EditorThemeOption("Dracula", "Dracula", true, Color(0xFF282A36), Color(0xFF6272A4), Color(0xFFFF79C6), Color(0xFFF1FA8C), Color(0xFFF8F8F2)),
    EditorThemeOption("Monokai", "Monokai", true, Color(0xFF272822), Color(0xFF75715E), Color(0xFFF92672), Color(0xFFE6DB74), Color(0xFFF8F8F2)),
    EditorThemeOption("Nord Dark", "Nord Dark", true, Color(0xFF2E3440), Color(0xFF4C566A), Color(0xFF81A1C1), Color(0xFFA3BE8C), Color(0xFFD8DEE9)),
    EditorThemeOption("Material Palenight", "Material Palenight", true, Color(0xFF292D3E), Color(0xFF676E95), Color(0xFFC792EA), Color(0xFFC3E88D), Color(0xFFA6ACCD)),
    EditorThemeOption("Tokyo Night", "Tokyo Night", true, Color(0xFF1A1B26), Color(0xFF3B4261), Color(0xFFBB9AF7), Color(0xFF9ECE6A), Color(0xFFA9B1D6)),
    EditorThemeOption("GitHub Light", "GitHub Light", false, Color(0xFFFFFFFF), Color(0xFF6A737D), Color(0xFFD73A49), Color(0xFF032F62), Color(0xFF24292E)),
    EditorThemeOption("Solarized Light", "Solarized Light", false, Color(0xFFFDF6E3), Color(0xFF93A1A1), Color(0xFF859900), Color(0xFF2AA198), Color(0xFF657B83)),
    EditorThemeOption("One Light", "One Light", false, Color(0xFFFAFAFA), Color(0xFFA0A1A7), Color(0xFFA626A4), Color(0xFF50A14F), Color(0xFF383A42)),
    EditorThemeOption("Rose Pine Dawn", "Rose Pine Dawn", false, Color(0xFFFAF4ED), Color(0xFF9893A5), Color(0xFF31748F), Color(0xFFEA9D34), Color(0xFF575279)),
    EditorThemeOption("Material Light", "Material Light", false, Color(0xFFFAFAFA), Color(0xFF90A4AE), Color(0xFF39ADB5), Color(0xFF91B859), Color(0xFF263238)),

    // --- TextMate Asset Schemes ---
    EditorThemeOption("codeforge", "CodeForge Dark (TM)", true, Color(0xFF1E2330), Color(0xFF4C5561), Color(0xFF6366F1), Color(0xFF06B6D4), Color(0xFFF1F5F9)),
    EditorThemeOption("quietlight", "Quiet Light (TM)", false, Color(0xFFF5F5F5), Color(0xFFAAAAAA), Color(0xFF7A3E9D), Color(0xFF448C27), Color(0xFF333333)),
    EditorThemeOption("ayu-dark", "Ayu Dark (TM)", true, Color(0xFF0F1419), Color(0xFF4C5561), Color(0xFFFF8F40), Color(0xFFAAAFFF), Color(0xFFE6B450)),
    EditorThemeOption("ayu_light", "Ayu Light (TM)", false, Color(0xFFFAFAFA), Color(0xFFABB2BF), Color(0xFFFF9940), Color(0xFF86B300), Color(0xFF57606A)),
    EditorThemeOption("ayu_mirage", "Ayu Mirage (TM)", true, Color(0xFF1F2430), Color(0xFF607080), Color(0xFFFFCC66), Color(0xFFAAE5A4), Color(0xFFCBCCC6)),
    EditorThemeOption("solarized_dark", "Solarized Dark (TM)", true, Color(0xFF002B36), Color(0xFF586E75), Color(0xFF859900), Color(0xFF2AA198), Color(0xFF839496)),
    EditorThemeOption("eclipse_dark", "Eclipse Dark (TM)", true, Color(0xFF1F1F1F), Color(0xFF5C5C5C), Color(0xFFDD2867), Color(0xFF17C6A3), Color(0xFFD4D4D4)),
    EditorThemeOption("eclipse_light", "Eclipse Light (TM)", false, Color(0xFFFFFFFF), Color(0xFF999999), Color(0xFF7F0055), Color(0xFF2A00FF), Color(0xFF000000)),
    EditorThemeOption("onedark", "One Dark (TM)", true, Color(0xFF282C34), Color(0xFF5C6370), Color(0xFFC678DD), Color(0xFF98C379), Color(0xFFABB2BF))
)

@Composable
private fun EditorThemePreviewCard(
    theme: EditorThemeOption,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(12.dp)
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = isSelected, onClick = onSelect)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = theme.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (theme.isDark) ResGetter.get(com.codeforge.core.resources.R.string.feature_settings_editor_theme_dark) else ResGetter.get(com.codeforge.core.resources.R.string.feature_settings_editor_theme_light),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Visual Code Editor Live Preview Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(85.dp)
                    .background(theme.backgroundColor, RoundedCornerShape(8.dp))
                    .padding(8.dp)
            ) {
                Column {
                    Row {
                        Text("1 ", color = theme.lineNumberColor, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                        Text("fun ", color = theme.keywordColor, fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("main", color = theme.textColor, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                        Text("() {", color = theme.textColor, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                    }
                    Row {
                        Text("2 ", color = theme.lineNumberColor, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                        Text("    val ", color = theme.keywordColor, fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("theme = ", color = theme.textColor, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                        Text("\"${theme.name}\"", color = theme.stringColor, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                    }
                    Row {
                        Text("3 ", color = theme.lineNumberColor, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                        Text("}", color = theme.textColor, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
