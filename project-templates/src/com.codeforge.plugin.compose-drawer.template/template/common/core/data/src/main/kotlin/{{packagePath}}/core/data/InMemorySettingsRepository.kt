package {{packageName}}.core.data

import {{packageName}}.core.domain.model.AppSettings
import {{packageName}}.core.domain.model.ThemeMode
import {{packageName}}.core.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/** Einstellungen im Speicher; für Dauerhaftigkeit durch DataStore ersetzen. */
@Singleton
class InMemorySettingsRepository @Inject constructor() : SettingsRepository {
    private val state = MutableStateFlow(AppSettings())
    override val settings: StateFlow<AppSettings> = state.asStateFlow()

    override suspend fun setThemeMode(mode: ThemeMode) = state.update { it.copy(themeMode = mode) }

    override suspend fun setDynamicColor(enabled: Boolean) = state.update { it.copy(dynamicColor = enabled) }
}
