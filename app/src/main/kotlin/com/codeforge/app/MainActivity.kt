// Modul: :app
package com.codeforge.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codeforge.core.common.logging.AppLogger
import com.codeforge.core.datastore.SettingsRepository
import com.codeforge.core.datastore.proto.ThemeMode
import com.codeforge.core.designsystem.CodeForgeTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLogger.step("MainActivity", "onCreate initialized")
        enableEdgeToEdge()
        setContent {
            val settings by settingsRepository.appSettings.collectAsStateWithLifecycle(
                initialValue = null,
            )

            val currentTheme = settings?.theme
            val systemDark = isSystemInDarkTheme()
            val isDark =
                when (currentTheme?.mode) {
                    ThemeMode.DARK -> true
                    ThemeMode.LIGHT -> false
                    else -> systemDark
                }

            DisposableEffect(isDark) {
                AppLogger.d("MainActivity", "Applying edge-to-edge style: isDark=$isDark")
                enableEdgeToEdge(
                    statusBarStyle =
                        androidx.activity.SystemBarStyle.auto(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT,
                        ) { isDark },
                    navigationBarStyle =
                        androidx.activity.SystemBarStyle.auto(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT,
                        ) { isDark },
                )
                onDispose {}
            }

            CodeForgeTheme(themeState = currentTheme) {
                if (settings == null) {
                    AppLogger.d("MainActivity", "Waiting for DataStore settings initialization...")
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            color = Color(0xFF6366F1),
                        )
                    }
                } else {
                    AppLogger.step("MainActivity", "DataStore loaded. Onboarding completed: ${settings!!.onboardingCompleted}")
                    CodeForgeNavHost(
                        startOnboarding = !settings!!.onboardingCompleted,
                    )
                }
            }
        }
    }
}
