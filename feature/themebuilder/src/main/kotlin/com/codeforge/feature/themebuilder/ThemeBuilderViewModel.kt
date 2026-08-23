// Modul: :feature:themebuilder
package com.codeforge.feature.themebuilder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codeforge.core.datastore.SettingsRepository
import com.codeforge.core.datastore.proto.ThemeConfig
import com.codeforge.core.designsystem.supportsDynamicColor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private val HEX_COLOR_REGEX = Regex("^#[0-9A-Fa-f]{6}$")

private data class HexBuffer(val primary: String, val secondary: String, val tertiary: String)

@HiltViewModel
class ThemeBuilderViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    /** Überschreibt die persistierten Hex-Werte während einer aktiven Custom-Color-Bearbeitung. */
    private val editBuffer = MutableStateFlow<HexBuffer?>(null)

    val uiState: StateFlow<ThemeBuilderUiState> = combine(
        settingsRepository.appSettings,
        editBuffer
    ) { settings, buffer ->
        val theme = settings.theme
        val primary = buffer?.primary ?: theme.customPalette.primary
        val secondary = buffer?.secondary ?: theme.customPalette.secondary
        val tertiary = buffer?.tertiary ?: theme.customPalette.tertiary

        ThemeBuilderUiState(
            themeMode = theme.mode,
            colorSchemeId = theme.colorSchemeId,
            useDynamicColor = theme.useDynamicColor,
            customPrimary = primary,
            customSecondary = secondary,
            customTertiary = tertiary,
            isDynamicColorSupported = supportsDynamicColor(),
            previewTheme = buildPreviewTheme(theme, buffer),
            isLoading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeBuilderUiState())

    fun onEvent(event: ThemeBuilderUiEvent) {
        when (event) {
            is ThemeBuilderUiEvent.ModeSelected -> {
                editBuffer.value = null
                persist { it.toBuilder().setMode(event.mode).build() }
            }

            is ThemeBuilderUiEvent.PresetSelected -> {
                editBuffer.value = null
                persist {
                    it.toBuilder()
                        .setColorSchemeId(event.presetId)
                        .setUseDynamicColor(false)
                        .build()
                }
            }

            ThemeBuilderUiEvent.DynamicColorToggled -> {
                editBuffer.value = null
                persist { it.toBuilder().setUseDynamicColor(!it.useDynamicColor).build() }
            }

            is ThemeBuilderUiEvent.CustomColorChanged -> handleCustomColorChanged(event)
        }
    }

    private fun handleCustomColorChanged(event: ThemeBuilderUiEvent.CustomColorChanged) {
        val current = editBuffer.value ?: run {
            val palette = uiState.value
            HexBuffer(palette.customPrimary, palette.customSecondary, palette.customTertiary)
        }

        val updated = when (event.slot) {
            PaletteSlot.PRIMARY -> current.copy(primary = event.hex)
            PaletteSlot.SECONDARY -> current.copy(secondary = event.hex)
            PaletteSlot.TERTIARY -> current.copy(tertiary = event.hex)
        }
        editBuffer.value = updated

        // Nur bei gültigem Hex-Wert persistieren — Zwischenzustände beim Tippen bleiben lokal.
        if (HEX_COLOR_REGEX.matches(event.hex)) {
            persist { theme ->
                val paletteBuilder = theme.customPalette.toBuilder()
                when (event.slot) {
                    PaletteSlot.PRIMARY -> paletteBuilder.setPrimary(event.hex)
                    PaletteSlot.SECONDARY -> paletteBuilder.setSecondary(event.hex)
                    PaletteSlot.TERTIARY -> paletteBuilder.setTertiary(event.hex)
                }
                theme.toBuilder()
                    .setColorSchemeId("custom")
                    .setUseDynamicColor(false)
                    .setCustomPalette(paletteBuilder)
                    .build()
            }
        }
    }

    private fun buildPreviewTheme(persisted: ThemeConfig, buffer: HexBuffer?): ThemeConfig {
        if (buffer == null) return persisted
        if (!HEX_COLOR_REGEX.matches(buffer.primary)) return persisted

        val paletteBuilder = persisted.customPalette.toBuilder()
            .setPrimary(buffer.primary)
            .setSecondary(buffer.secondary.takeIf { HEX_COLOR_REGEX.matches(it) } ?: buffer.primary)
            .setTertiary(buffer.tertiary.takeIf { HEX_COLOR_REGEX.matches(it) } ?: buffer.primary)

        return persisted.toBuilder()
            .setColorSchemeId("custom")
            .setUseDynamicColor(false)
            .setCustomPalette(paletteBuilder)
            .build()
    }

    private fun persist(transform: (ThemeConfig) -> ThemeConfig) = viewModelScope.launch {
        settingsRepository.updateTheme(transform)
    }
}
