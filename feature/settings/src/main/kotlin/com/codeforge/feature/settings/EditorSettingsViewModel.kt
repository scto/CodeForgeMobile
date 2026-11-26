/**
 * Modul: :feature:settings
 * @author Thomas Schmid
 */
package com.codeforge.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codeforge.core.datastore.SettingsRepository
import com.codeforge.core.designsystem.theme.TextMateThemeCatalog
import com.codeforge.core.domain.extension.ExtensionColorTheme
import com.codeforge.core.domain.extension.ExtensionThemeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** ViewModel der Editor-Einstellungen. */
@HiltViewModel
class EditorSettingsViewModel internal constructor(
    private val settingsRepository: SettingsRepository,
    pluginThemes: Flow<List<ExtensionColorTheme>>
) : ViewModel() {

    /** UI-Zustand; Editor-Themes: eingebaute und die der installierten Theme-Plugins. */
    val uiState: StateFlow<EditorSettingsUiState> = settingsRepository.appSettings
        .combine(pluginThemes) { settings, themes -> settings to TextMateThemeCatalog.getAvailableThemes(themes) }
        .map { (settings, availableThemes) ->
            EditorSettingsUiState(
                tabSize = settings.editor.tabSize.takeIf { it > 0 } ?: 4,
                useTreeSitter = settings.editor.useTreeSitter,
                textmateTheme = settings.editor.textmateTheme.ifBlank { "auto" },
                lspServerPath = settings.editor.lspServerPath,
                fontSize = settings.editor.fontSize.takeIf { it > 0 } ?: 14,
                fontFamily = settings.editor.fontFamily,
                wordWrap = settings.editor.wordWrap,
                showNonPrintableChars = settings.editor.showNonPrintableChars,
                stickyScrollEnabled = settings.editor.stickyScrollEnabled,
                magnifierEnabled = settings.editor.magnifierEnabled,
                icuWordSelection = !settings.editor.icuWordSelectionDisabled,
                fontLigatures = !settings.editor.fontLigaturesDisabled,
                softTabs = !settings.editor.hardTabs,
                deleteEmptyLineFast = !settings.editor.deleteEmptyLineFastDisabled,
                backspaceDeletesTab = !settings.editor.backspaceDeletesTabDisabled,
                symbolPairAutocompleteEnabled = settings.editor.symbolPairAutocompleteEnabled,
                minimapEnabled = settings.editor.minimapEnabled,
                rainbowBracketsEnabled = settings.editor.rainbowBracketsEnabled,
                codeFoldingEnabled = !settings.editor.codeFoldingDisabled,
                pinchToZoomEnabled = settings.editor.pinchToZoomEnabled,
                desktopShortcutsEnabled = settings.editor.desktopShortcutsEnabled,
                formatOnSaveKotlin = settings.editor.formatOnSaveKotlin,
                formatOnSaveJava = settings.editor.formatOnSaveJava,
                formatOnSaveXml = settings.editor.formatOnSaveXml,
                formatOnSaveJson = settings.editor.formatOnSaveJson,
                lspMaxConcurrentSessions = settings.editor.lspMaxConcurrentSessions.takeIf { it > 0 } ?: 3,
                lspIdleTimeoutMinutes = settings.editor.lspIdleTimeoutMinutes.takeIf { it > 0 } ?: 10,
                projectIndexingEnabled = !settings.editor.projectIndexingDisabled,
                j2kEnabled = !settings.editor.j2KDisabled,
                x2cEnabled = !settings.editor.x2CDisabled,
                showLineNumbers = !settings.editor.lineNumbersHidden,
                pinLineNumbers = settings.editor.pinLineNumbers,
                cursorAnimationEnabled = !settings.editor.cursorAnimationDisabled,
                lineSpacingPercent = settings.editor.lineSpacingPercent.takeIf { it > 0 } ?: 100,
                readOnlyDefault = settings.editor.readOnlyDefault,
                txtWordWrap = settings.editor.txtWordWrap,
                colorPreviewsEnabled = !settings.editor.colorPreviewsDisabled,
                disableSoftKeyboardWithHardwareKeyboard = settings.editor.disableSoftKeyboardWithHardwareKeyboard,
                keyboardSuggestions = settings.editor.keyboardSuggestions,
                symbolBarEnabled = !settings.editor.symbolBarHidden,
                highlightCurrentLine = !settings.editor.currentLineHighlightDisabled,
                indentGuides = !settings.editor.indentGuidesDisabled,
                cursorBlink = !settings.editor.cursorBlinkDisabled,
                cursorAnimationStyle = settings.editor.cursorAnimationStyle,
                scrollbars = !settings.editor.scrollbarsHidden,
                stickyScrollMaxLines = settings.editor.stickyScrollMaxLines.takeIf { it > 0 } ?: DEFAULT_STICKY_LINES,
                lineNumberSelectsLine = settings.editor.lineNumberClickSelectsLine,
                diagnosticStyle = settings.editor.diagnosticStyle,
                inlayHints = !settings.editor.inlayHintsDisabled,
                mouseMode = settings.editor.mouseMode,
                selectionHandleStyle = settings.editor.selectionHandleStyle,
                fastScrollLineTip = !settings.editor.fastScrollTipHidden,
                foldMarksAsLines = settings.editor.foldMarksAsLines,
                letterSpacingPercent = settings.editor.letterSpacingPercent,
                rulerColumn = settings.editor.rulerColumn,
                formatOnPaste = settings.editor.formatOnPaste,
                fileHeaderEnabled = settings.fileHeader.enabled,
                fileHeaderAuthor = settings.fileHeader.author,
                availableThemes = availableThemes,
                isLoading = false
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EditorSettingsUiState())

    @Inject
    constructor(
        settingsRepository: SettingsRepository,
        extensionThemes: ExtensionThemeRepository
    ) : this(settingsRepository, extensionThemes.themes)

    /** Verarbeitet eine Aktion des Nutzers. */
    fun onEvent(event: EditorSettingsUiEvent) {
        viewModelScope.launch {
            when (event) {
                is EditorSettingsUiEvent.TabSizeChanged ->
                    settingsRepository.updateEditor { it.toBuilder().setTabSize(event.size).build() }

                is EditorSettingsUiEvent.LspMaxConcurrentSessionsChanged ->
                    settingsRepository.updateEditor { it.toBuilder().setLspMaxConcurrentSessions(event.count).build() }

                is EditorSettingsUiEvent.LspIdleTimeoutMinutesChanged ->
                    settingsRepository.updateEditor { it.toBuilder().setLspIdleTimeoutMinutes(event.minutes).build() }

                EditorSettingsUiEvent.TreeSitterToggled ->
                    settingsRepository.updateEditor { it.toBuilder().setUseTreeSitter(!it.useTreeSitter).build() }

                is EditorSettingsUiEvent.TextmateThemeChanged ->
                    settingsRepository.updateEditor { it.toBuilder().setTextmateTheme(event.value).build() }

                is EditorSettingsUiEvent.LspServerPathChanged ->
                    settingsRepository.updateEditor { it.toBuilder().setLspServerPath(event.value).build() }

                is EditorSettingsUiEvent.FontSizeChanged ->
                    settingsRepository.updateEditor { it.toBuilder().setFontSize(event.size).build() }

                is EditorSettingsUiEvent.FontFamilyChanged ->
                    settingsRepository.updateEditor { it.toBuilder().setFontFamily(event.value).build() }

                EditorSettingsUiEvent.WordWrapToggled ->
                    settingsRepository.updateEditor { it.toBuilder().setWordWrap(!it.wordWrap).build() }

                EditorSettingsUiEvent.NonPrintableCharsToggled ->
                    settingsRepository.updateEditor {
                        it.toBuilder().setShowNonPrintableChars(!it.showNonPrintableChars).build()
                    }

                EditorSettingsUiEvent.StickyScrollToggled ->
                    settingsRepository.updateEditor {
                        it.toBuilder().setStickyScrollEnabled(!it.stickyScrollEnabled).build()
                    }

                EditorSettingsUiEvent.FontLigaturesToggled ->
                    settingsRepository.updateEditor { it.toBuilder().setFontLigaturesDisabled(!it.fontLigaturesDisabled).build() }

                EditorSettingsUiEvent.SoftTabsToggled ->
                    settingsRepository.updateEditor { it.toBuilder().setHardTabs(!it.hardTabs).build() }

                EditorSettingsUiEvent.DeleteEmptyLineFastToggled ->
                    settingsRepository.updateEditor {
                        it.toBuilder().setDeleteEmptyLineFastDisabled(!it.deleteEmptyLineFastDisabled).build()
                    }

                EditorSettingsUiEvent.BackspaceDeletesTabToggled ->
                    settingsRepository.updateEditor {
                        it.toBuilder().setBackspaceDeletesTabDisabled(!it.backspaceDeletesTabDisabled).build()
                    }

                EditorSettingsUiEvent.IcuWordSelectionToggled ->
                    settingsRepository.updateEditor {
                        it.toBuilder().setIcuWordSelectionDisabled(!it.icuWordSelectionDisabled).build()
                    }

                EditorSettingsUiEvent.MagnifierToggled ->
                    settingsRepository.updateEditor { it.toBuilder().setMagnifierEnabled(!it.magnifierEnabled).build() }

                EditorSettingsUiEvent.SymbolPairAutocompleteToggled ->
                    settingsRepository.updateEditor {
                        it.toBuilder()
                            .setSymbolPairAutocompleteEnabled(!it.symbolPairAutocompleteEnabled)
                            .build()
                    }

                EditorSettingsUiEvent.ProjectIndexingToggled ->
                    settingsRepository.updateEditor {
                        it.toBuilder().setProjectIndexingDisabled(!it.projectIndexingDisabled).build()
                    }

                EditorSettingsUiEvent.J2kToggled ->
                    settingsRepository.updateEditor { it.toBuilder().setJ2KDisabled(!it.j2KDisabled).build() }

                EditorSettingsUiEvent.X2cToggled ->
                    settingsRepository.updateEditor { it.toBuilder().setX2CDisabled(!it.x2CDisabled).build() }

                EditorSettingsUiEvent.MinimapToggled ->
                    settingsRepository.updateEditor { it.toBuilder().setMinimapEnabled(!it.minimapEnabled).build() }

                EditorSettingsUiEvent.CodeFoldingToggled ->
                    settingsRepository.updateEditor { it.toBuilder().setCodeFoldingDisabled(!it.codeFoldingDisabled).build() }

                EditorSettingsUiEvent.RainbowBracketsToggled ->
                    settingsRepository.updateEditor {
                        it.toBuilder().setRainbowBracketsEnabled(!it.rainbowBracketsEnabled).build()
                    }

                EditorSettingsUiEvent.PinchToZoomToggled ->
                    settingsRepository.updateEditor {
                        it.toBuilder().setPinchToZoomEnabled(!it.pinchToZoomEnabled).build()
                    }

                EditorSettingsUiEvent.DesktopShortcutsToggled ->
                    settingsRepository.updateEditor {
                        it.toBuilder().setDesktopShortcutsEnabled(!it.desktopShortcutsEnabled).build()
                    }

                EditorSettingsUiEvent.FormatOnSaveKotlinToggled ->
                    settingsRepository.updateEditor {
                        it.toBuilder().setFormatOnSaveKotlin(!it.formatOnSaveKotlin).build()
                    }

                EditorSettingsUiEvent.FormatOnSaveJavaToggled ->
                    settingsRepository.updateEditor {
                        it.toBuilder().setFormatOnSaveJava(!it.formatOnSaveJava).build()
                    }

                EditorSettingsUiEvent.LineNumbersToggled ->
                    settingsRepository.updateEditor { it.toBuilder().setLineNumbersHidden(!it.lineNumbersHidden).build() }
                EditorSettingsUiEvent.PinLineNumbersToggled ->
                    settingsRepository.updateEditor { it.toBuilder().setPinLineNumbers(!it.pinLineNumbers).build() }
                EditorSettingsUiEvent.CursorAnimationToggled ->
                    settingsRepository.updateEditor { it.toBuilder().setCursorAnimationDisabled(!it.cursorAnimationDisabled).build() }
                is EditorSettingsUiEvent.LineSpacingChanged ->
                    settingsRepository.updateEditor { it.toBuilder().setLineSpacingPercent(event.percent.coerceIn(80, 200)).build() }
                EditorSettingsUiEvent.ReadOnlyDefaultToggled ->
                    settingsRepository.updateEditor { it.toBuilder().setReadOnlyDefault(!it.readOnlyDefault).build() }
                EditorSettingsUiEvent.TxtWordWrapToggled ->
                    settingsRepository.updateEditor { it.toBuilder().setTxtWordWrap(!it.txtWordWrap).build() }
                EditorSettingsUiEvent.ColorPreviewsToggled ->
                    settingsRepository.updateEditor { it.toBuilder().setColorPreviewsDisabled(!it.colorPreviewsDisabled).build() }
                EditorSettingsUiEvent.DisableSoftKeyboardToggled ->
                    settingsRepository.updateEditor {
                        it.toBuilder().setDisableSoftKeyboardWithHardwareKeyboard(!it.disableSoftKeyboardWithHardwareKeyboard).build()
                    }
                EditorSettingsUiEvent.KeyboardSuggestionsToggled ->
                    settingsRepository.updateEditor { it.toBuilder().setKeyboardSuggestions(!it.keyboardSuggestions).build() }

                EditorSettingsUiEvent.FormatOnSaveXmlToggled ->
                    settingsRepository.updateEditor {
                        it.toBuilder().setFormatOnSaveXml(!it.formatOnSaveXml).build()
                    }

                EditorSettingsUiEvent.FormatOnSaveJsonToggled ->
                    settingsRepository.updateEditor {
                        it.toBuilder().setFormatOnSaveJson(!it.formatOnSaveJson).build()
                    }

                EditorSettingsUiEvent.CurrentLineHighlightToggled -> settingsRepository.updateEditor {
                    it.toBuilder().setCurrentLineHighlightDisabled(!it.currentLineHighlightDisabled).build()
                }
                EditorSettingsUiEvent.IndentGuidesToggled -> settingsRepository.updateEditor {
                    it.toBuilder().setIndentGuidesDisabled(!it.indentGuidesDisabled).build()
                }
                EditorSettingsUiEvent.CursorBlinkToggled -> settingsRepository.updateEditor {
                    it.toBuilder().setCursorBlinkDisabled(!it.cursorBlinkDisabled).build()
                }
                EditorSettingsUiEvent.ScrollbarsToggled -> settingsRepository.updateEditor {
                    it.toBuilder().setScrollbarsHidden(!it.scrollbarsHidden).build()
                }
                EditorSettingsUiEvent.LineNumberSelectsLineToggled -> settingsRepository.updateEditor {
                    it.toBuilder().setLineNumberClickSelectsLine(!it.lineNumberClickSelectsLine).build()
                }
                is EditorSettingsUiEvent.CursorAnimationStyleChanged -> settingsRepository.updateEditor {
                    it.toBuilder().setCursorAnimationStyle(event.style).build()
                }
                is EditorSettingsUiEvent.StickyScrollMaxLinesChanged -> settingsRepository.updateEditor {
                    it.toBuilder().setStickyScrollMaxLines(event.lines).build()
                }
                is EditorSettingsUiEvent.DiagnosticStyleChanged -> settingsRepository.updateEditor {
                    it.toBuilder().setDiagnosticStyle(event.style).build()
                }
                EditorSettingsUiEvent.InlayHintsToggled -> settingsRepository.updateEditor {
                    it.toBuilder().setInlayHintsDisabled(!it.inlayHintsDisabled).build()
                }
                EditorSettingsUiEvent.FastScrollTipToggled -> settingsRepository.updateEditor {
                    it.toBuilder().setFastScrollTipHidden(!it.fastScrollTipHidden).build()
                }
                EditorSettingsUiEvent.FoldMarksToggled -> settingsRepository.updateEditor {
                    it.toBuilder().setFoldMarksAsLines(!it.foldMarksAsLines).build()
                }
                EditorSettingsUiEvent.FormatOnPasteToggled -> settingsRepository.updateEditor {
                    it.toBuilder().setFormatOnPaste(!it.formatOnPaste).build()
                }
                is EditorSettingsUiEvent.MouseModeChanged -> settingsRepository.updateEditor {
                    it.toBuilder().setMouseMode(event.mode).build()
                }
                is EditorSettingsUiEvent.SelectionHandleStyleChanged -> settingsRepository.updateEditor {
                    it.toBuilder().setSelectionHandleStyle(event.style).build()
                }
                is EditorSettingsUiEvent.LetterSpacingChanged -> settingsRepository.updateEditor {
                    it.toBuilder().setLetterSpacingPercent(event.percent).build()
                }
                is EditorSettingsUiEvent.RulerColumnChanged -> settingsRepository.updateEditor {
                    it.toBuilder().setRulerColumn(event.column).build()
                }

                EditorSettingsUiEvent.SymbolBarToggled ->
                    settingsRepository.updateEditor { it.toBuilder().setSymbolBarHidden(!it.symbolBarHidden).build() }

                EditorSettingsUiEvent.FileHeaderEnabledToggled ->
                    settingsRepository.updateFileHeader {
                        it.toBuilder().setEnabled(!it.enabled).build()
                    }

                is EditorSettingsUiEvent.FileHeaderAuthorChanged ->
                    settingsRepository.updateFileHeader {
                        it.toBuilder().setAuthor(event.author).build()
                    }

                is EditorSettingsUiEvent.ResetSection -> {
                    when (event.section) {
                        EditorSettingsSection.DISPLAY -> {
                            settingsRepository.updateEditor {
                                it.toBuilder()
                                    .setFontSize(14)
                                    .setFontFamily("")
                                    .setWordWrap(false)
                                    .setShowNonPrintableChars(false)
                                    .setStickyScrollEnabled(true)
                                    .setMagnifierEnabled(true)
                                    .setIcuWordSelectionDisabled(false)
                                    .setFontLigaturesDisabled(false)
                                    .setMinimapEnabled(true)
                                    .setRainbowBracketsEnabled(true)
                                    .setPinchToZoomEnabled(true)
                                    .setLineNumbersHidden(false)
                                    .setPinLineNumbers(false)
                                    .setCursorAnimationDisabled(false)
                                    .setLineSpacingPercent(0)
                                    .setReadOnlyDefault(false)
                                    .setTxtWordWrap(false)
                                    .setColorPreviewsDisabled(false)
                                    .setCodeFoldingDisabled(false)
                                    .setCurrentLineHighlightDisabled(false)
                                    .setIndentGuidesDisabled(false)
                                    .setCursorBlinkDisabled(false)
                                    .setCursorAnimationStyle(0)
                                    .setScrollbarsHidden(false)
                                    .setStickyScrollMaxLines(0)
                                    .setLineNumberClickSelectsLine(false)
                                    .setDiagnosticStyle(0)
                                    .setInlayHintsDisabled(false)
                                    .setFastScrollTipHidden(false)
                                    .setFoldMarksAsLines(false)
                                    .setLetterSpacingPercent(0)
                                    .setRulerColumn(0)
                                    .build()
                            }
                        }
                        EditorSettingsSection.INPUT -> {
                            settingsRepository.updateEditor {
                                it.toBuilder()
                                    .setTabSize(4)
                                    .setHardTabs(false)
                                    .setDeleteEmptyLineFastDisabled(false)
                                    .setBackspaceDeletesTabDisabled(false)
                                    .setSymbolPairAutocompleteEnabled(true)
                                    .setDesktopShortcutsEnabled(true)
                                    .setSymbolBarHidden(false)
                                    .setFormatOnPaste(false)
                                    .setMouseMode(0)
                                    .setSelectionHandleStyle(0)
                                    .setDisableSoftKeyboardWithHardwareKeyboard(false)
                                    .setKeyboardSuggestions(false)
                                    .build()
                            }
                        }
                        EditorSettingsSection.LANGUAGE -> {
                            settingsRepository.updateEditor {
                                it.toBuilder()
                                    .setUseTreeSitter(true)
                                    .setLspMaxConcurrentSessions(3)
                                    .setLspIdleTimeoutMinutes(10)
                                    .build()
                            }
                        }
                        EditorSettingsSection.INDEXING -> {
                            settingsRepository.updateEditor {
                                it.toBuilder().setProjectIndexingDisabled(false).setJ2KDisabled(false).setX2CDisabled(false).build()
                            }
                        }
                        EditorSettingsSection.FORMAT_ON_SAVE -> {
                            settingsRepository.updateEditor {
                                it.toBuilder()
                                    .setFormatOnSaveKotlin(false)
                                    .setFormatOnSaveJava(false)
                                    .setFormatOnSaveXml(false)
                                    .setFormatOnSaveJson(false)
                                    .build()
                            }
                        }
                    }
                }
            }
        }
    }
}

private const val DEFAULT_STICKY_LINES = 3
