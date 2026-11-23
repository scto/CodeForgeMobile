// Modul: :feature:settings
package com.codeforge.feature.settings.appearance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codeforge.core.datastore.SettingsRepository
import com.codeforge.core.datastore.proto.ThemeConfig
import com.codeforge.core.designsystem.familyLabel
import com.codeforge.core.designsystem.supportsDynamicColor
import com.codeforge.core.domain.extension.ExtensionColorTheme
import com.codeforge.core.domain.extension.ExtensionThemeRepository
import com.codeforge.libs.theme_kit.ColorHex
import com.codeforge.libs.theme_kit.ColorRole
import com.codeforge.libs.theme_kit.ThemeEngine
import com.codeforge.libs.theme_kit.ThemeSpec
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** ViewModel der Seite Erscheinungsbild (Modus, dynamische Farben, Farbschema, Icons, Sprache). */
@HiltViewModel
class AppearanceViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    extensionThemeRepository: ExtensionThemeRepository,
) : ViewModel() {

    /** UI-Zustand. */
    val uiState: StateFlow<AppearanceUiState> = combine(
        settingsRepository.appSettings,
        extensionThemeRepository.themes,
        extensionThemeRepository.iconThemes,
        extensionThemeRepository.languagePacks,
    ) { settings, colorThemes, icons, packs ->
        val theme = settings.theme
        AppearanceUiState(
            mode = theme.mode,
            useDynamicColor = theme.useDynamicColor,
            dynamicSupported = supportsDynamicColor(),
            selectedSchemeId = colorThemes.firstOrNull { it.key == theme.colorSchemeId }?.let(::familyId).orEmpty(),
            schemes = listOf(codeForgeOption) + familiesOf(colorThemes),
            iconThemes = icons,
            iconThemeId = theme.iconThemeId,
            languagePacks = packs,
            languagePackId = theme.languagePackId,
            loading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), AppearanceUiState())

    /** Verarbeitet eine Aktion. */
    fun onEvent(event: AppearanceUiEvent) {
        when (event) {
            is AppearanceUiEvent.ModeSelected -> persist { it.toBuilder().setMode(event.mode).build() }
            AppearanceUiEvent.DynamicToggled -> persist { it.toBuilder().setUseDynamicColor(!it.useDynamicColor).build() }
            is AppearanceUiEvent.SchemeSelected -> persist {
                it.toBuilder().setColorSchemeId(event.key).setUseDynamicColor(false).build()
            }
            is AppearanceUiEvent.IconThemeSelected -> persist { it.toBuilder().setIconThemeId(event.key).build() }
            is AppearanceUiEvent.LanguagePackSelected -> persist { it.toBuilder().setLanguagePackId(event.key).build() }
        }
    }

    private fun persist(transform: (ThemeConfig) -> ThemeConfig) {
        viewModelScope.launch { settingsRepository.updateTheme(transform) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L

        val codeForgeOption: SchemeOption = ThemeEngine.roles(ThemeSpec.CODEFORGE, isDark = false).let { r ->
            SchemeOption("", "", "CodeForge", "", listOf(r[ColorRole.PRIMARY], r[ColorRole.TERTIARY], r[ColorRole.SURFACE_CONTAINER]), hasLight = true, hasDark = true)
        }

        fun familyId(theme: ExtensionColorTheme): String = theme.pluginId + "|" + theme.familyLabel()

        /** Eine Karte je Plugin-Familie; ein Plugin mit mehreren Familien (z. B. GitHub, Dimmed) wird nach Namen getrennt. */
        fun familiesOf(themes: List<ExtensionColorTheme>): List<SchemeOption> =
            themes.groupBy { familyId(it) }.map { (id, variants) ->
                val first = variants.firstOrNull { !it.isDark } ?: variants.first()
                val primary = first.colors["primary"]?.toInt()
                val swatches = if (primary != null) {
                    val spec = ThemeSpec(
                        seed = ColorHex.format(primary),
                        secondary = first.colors["secondary"]?.let { ColorHex.format(it.toInt()) },
                        tertiary = first.colors["tertiary"]?.let { ColorHex.format(it.toInt()) },
                    )
                    val r = ThemeEngine.roles(spec, first.isDark)
                    listOf(r[ColorRole.PRIMARY], r[ColorRole.TERTIARY], first.colors["background"]?.toInt() ?: r[ColorRole.SURFACE_CONTAINER])
                } else {
                    emptyList()
                }
                SchemeOption(
                    id = id,
                    pluginId = first.pluginId,
                    label = first.familyLabel(),
                    key = first.key,
                    swatches = swatches,
                    hasLight = variants.any { !it.isDark },
                    hasDark = variants.any { it.isDark },
                )
            }.sortedBy { it.label.lowercase() }
    }
}
