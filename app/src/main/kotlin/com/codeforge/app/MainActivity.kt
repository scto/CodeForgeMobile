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
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settingsRepository: SettingsRepository

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
            val isDark = settings.theme.resolveIsDark(systemDark)
            LaunchedEffect(isDark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { isDark },
                    navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { isDark },
                )
            }

            CodeForgeTheme(themeState = settings.theme) {
                CodeForgeNavHost(
                    startOnboarding = !settings.onboardingCompleted
                )
            }
        }
    }
}
