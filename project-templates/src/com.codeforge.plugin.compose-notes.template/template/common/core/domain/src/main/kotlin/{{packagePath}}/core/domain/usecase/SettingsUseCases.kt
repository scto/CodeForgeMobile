package {{packageName}}.core.domain.usecase

import {{packageName}}.core.domain.model.Accent
import {{packageName}}.core.domain.model.AppSettings
import {{packageName}}.core.domain.model.NoteLayout
import {{packageName}}.core.domain.model.SortOrder
import {{packageName}}.core.domain.model.ThemeMode
import {{packageName}}.core.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Liefert die Einstellungen. */
class ObserveSettingsUseCase @Inject constructor(private val repository: SettingsRepository) {
    operator fun invoke(): Flow<AppSettings> = repository.settings
}

/** Ändert einzelne Einstellungen. */
class UpdateSettingsUseCase @Inject constructor(private val repository: SettingsRepository) {
    suspend fun themeMode(mode: ThemeMode) = repository.setThemeMode(mode)

    suspend fun accent(accent: Accent) = repository.setAccent(accent)

    suspend fun layout(layout: NoteLayout) = repository.setLayout(layout)

    suspend fun sortOrder(order: SortOrder) = repository.setSortOrder(order)
}
