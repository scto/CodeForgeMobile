// Modul: :app
package com.codeforge.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.isSystemInDarkTheme
import android.graphics.Color
import com.codeforge.core.designsystem.resolveIsDark
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codeforge.core.datastore.SettingsRepository
import com.codeforge.core.designsystem.CodeForgeTheme
import com.codeforge.core.designsystem.findExtensionTheme
import com.codeforge.core.designsystem.findIconTheme
import com.codeforge.core.resources.LanguageOverrides
import com.codeforge.core.domain.extension.ContributionRepository
import com.codeforge.core.domain.extension.ExtensionThemeRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var extensionThemeRepository: ExtensionThemeRepository
    @Inject lateinit var contributionRepository: ContributionRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        // Vor setContent: verhindert Flackern der Systemleisten beim Start.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val settings by settingsRepository.appSettings.collectAsStateWithLifecycle(
                initialValue = com.codeforge.core.datastore.proto.AppSettings.getDefaultInstance()
            )

            // Edge-to-Edge: Systemleisten transparent; Icon-Farbe folgt dem App-Theme (nicht nur dem System).
            val systemDark = isSystemInDarkTheme()
            val extensionThemes by extensionThemeRepository.themes.collectAsStateWithLifecycle()
            val extensionTheme = settings.theme.findExtensionTheme(extensionThemes)
            val iconThemes by extensionThemeRepository.iconThemes.collectAsStateWithLifecycle()
            val iconTheme = settings.theme.findIconTheme(iconThemes)
            val activeContributions by contributionRepository.contributions.collectAsStateWithLifecycle()
            val languagePacks by extensionThemeRepository.languagePacks.collectAsStateWithLifecycle()
            val languagePackKey = settings.theme.languagePackId
            // Sprachpaket aus einem Plugin als Überschreibungsschicht über den App-Texten.
            LaunchedEffect(languagePackKey, languagePacks) {
                val pack = languagePacks.firstOrNull { it.key == languagePackKey }
                LanguageOverrides.set(pack?.strings.orEmpty())
            }
            val isDark = settings.theme.resolveIsDark(systemDark, extensionTheme)
            LaunchedEffect(isDark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { isDark },
                    navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { isDark },
                )
            }

            CodeForgeTheme(themeState = settings.theme, extensionTheme = extensionTheme, iconTheme = iconTheme, grammars = activeContributions.grammars) {
                CodeForgeNavHost(
                    startOnboarding = !settings.onboardingCompleted,
                    useCompactTerminal = settings.terminal.useCompactTerminal
                )
            }
        }
    }
}
