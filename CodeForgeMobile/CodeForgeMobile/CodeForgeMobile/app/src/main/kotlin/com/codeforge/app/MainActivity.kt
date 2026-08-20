// Modul: :app
package com.codeforge.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codeforge.core.datastore.SettingsRepository
import com.codeforge.core.designsystem.CodeForgeTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val settings by settingsRepository.appSettings.collectAsStateWithLifecycle(
                initialValue = com.codeforge.core.datastore.proto.AppSettings.getDefaultInstance()
            )

            CodeForgeTheme(themeState = settings.theme) {
                CodeForgeNavHost(
                    startOnboarding = !settings.onboardingCompleted
                )
            }
        }
    }
}
