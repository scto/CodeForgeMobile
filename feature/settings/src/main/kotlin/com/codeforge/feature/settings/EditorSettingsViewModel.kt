/**
 * Modul: :feature:settings
 * @author Thomas Schmid
 */
package com.codeforge.feature.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codeforge.core.datastore.SettingsRepository
import com.codeforge.core.designsystem.theme.TextMateThemeCatalog
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class EditorSettingsViewModel internal constructor(
    private val settingsRepository: SettingsRepository,
    themesDir: File?
) : ViewModel() {

    @Inject
    constructor(
        settingsRepository: SettingsRepository,
        @ApplicationContext context: Context
    ) : this(settingsRepository, File(context.filesDir, "extensions/themes"))

    private val availableThemes = TextMateThemeCatalog.getAvailableThemes(themesDir)

    val uiState: StateFlow<EditorSettingsUiState> = settingsRepository.appSettings
        .map { settings ->
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
                symbolPairAutocompleteEnabled = settings.editor.symbolPairAutocompleteEnabled,
                minimapEnabled = settings.editor.minimapEnabled,
                rainbowBracketsEnabled = settings.editor.rainbowBracketsEnabled,
                pinchToZoomEnabled = settings.editor.pinchToZoomEnabled,
                desktopShortcutsEnabled = settings.editor.desktopShortcutsEnabled,
                formatOnSaveKotlin = settings.editor.formatOnSaveKotlin,
                formatOnSaveJava = settings.editor.formatOnSaveJava,
                formatOnSaveXml = settings.editor.formatOnSaveXml,
                formatOnSaveJson = settings.editor.formatOnSaveJson,
                lspMaxConcurrentSessions = settings.editor.lspMaxConcurrentSessions.takeIf { it > 0 } ?: 3,
                lspIdleTimeoutMinutes = settings.editor.lspIdleTimeoutMinutes.takeIf { it > 0 } ?: 10,
                fileHeaderEnabled = settings.fileHeader.enabled,
                fileHeaderAuthor = settings.fileHeader.author,
                availableThemes = availableThemes,
                isLoading = false
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EditorSettingsUiState(availableThemes = availableThemes))

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

                EditorSettingsUiEvent.MagnifierToggled ->
                    settingsRepository.updateEditor { it.toBuilder().setMagnifierEnabled(!it.magnifierEnabled).build() }

                EditorSettingsUiEvent.SymbolPairAutocompleteToggled ->
                    settingsRepository.updateEditor {
                        it.toBuilder()
                            .setSymbolPairAutocompleteEnabled(!it.symbolPairAutocompleteEnabled)
                            .build()
                    }

                EditorSettingsUiEvent.MinimapToggled ->
                    settingsRepository.updateEditor { it.toBuilder().setMinimapEnabled(!it.minimapEnabled).build() }

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

                EditorSettingsUiEvent.FormatOnSaveXmlToggled ->
                    settingsRepository.updateEditor {
                        it.toBuilder().setFormatOnSaveXml(!it.formatOnSaveXml).build()
                    }

                EditorSettingsUiEvent.FormatOnSaveJsonToggled ->
                    settingsRepository.updateEditor {
                        it.toBuilder().setFormatOnSaveJson(!it.formatOnSaveJson).build()
                    }

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
                                    .setTextmateTheme("auto")
                                    .setFontSize(14)
                                    .setFontFamily("")
                                    .setWordWrap(false)
                                    .setShowNonPrintableChars(false)
                                    .setStickyScrollEnabled(true)
                                    .setMagnifierEnabled(true)
                                    .setMinimapEnabled(true)
                                    .setRainbowBracketsEnabled(true)
                                    .setPinchToZoomEnabled(true)
                                    .build()
                            }
                        }
                        EditorSettingsSection.INPUT -> {
                            settingsRepository.updateEditor {
                                it.toBuilder()
                                    .setTabSize(4)
                                    .setSymbolPairAutocompleteEnabled(true)
                                    .setDesktopShortcutsEnabled(true)
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
