package {{packageName}}

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import {{packageName}}.core.designsystem.theme.AccentPalette
import {{packageName}}.core.designsystem.theme.AppTheme
import {{packageName}}.core.domain.model.AppSettings
import {{packageName}}.core.domain.model.ThemeMode
import {{packageName}}.core.domain.usecase.ObserveSettingsUseCase
import {{packageName}}.ui.AppShell
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val dark = when (settings.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            AppTheme(darkTheme = dark, accent = AccentPalette.valueOf(settings.accent.name)) {
                Surface(color = MaterialTheme.colorScheme.background) { AppShell() }
            }
        }
    }
}

/** Einstellungen für Theme und Akzentfarbe der ganzen App. */
@HiltViewModel
class MainViewModel @Inject constructor(observeSettings: ObserveSettingsUseCase) : ViewModel() {
    val settings: StateFlow<AppSettings> = observeSettings().stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
}
