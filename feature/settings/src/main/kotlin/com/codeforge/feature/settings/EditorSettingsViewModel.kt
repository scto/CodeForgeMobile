/**
 * Modul: :feature:settings
 * @author Thomas Schmid
 */
package com.codeforge.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codeforge.core.datastore.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EditorSettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val uiState: StateFlow<EditorSettingsUiState> = settingsRepository.appSettings
        .map { settings ->
            EditorSettingsUiState(
                tabSize = settings.editor.tabSize.takeIf { it > 0 } ?: 4,
                useTreeSitter = settings.editor.useTreeSitter,
                textmateTheme = settings.editor.textmateTheme,
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
                isLoading = false
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EditorSettingsUiState())

    fun onEvent(event: EditorSettingsUiEvent) {
        viewModelScope.launch {
            when (event) {
                is EditorSettingsUiEvent.TabSizeChanged ->
                    settingsRepository.updateEditor { it.toBuilder().setTabSize(event.size).build() }

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
            }
        }
    }

}
