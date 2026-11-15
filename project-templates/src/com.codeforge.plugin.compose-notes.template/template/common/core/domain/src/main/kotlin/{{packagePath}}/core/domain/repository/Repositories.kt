package {{packageName}}.core.domain.repository

import {{packageName}}.core.domain.model.Accent
import {{packageName}}.core.domain.model.AppSettings
import {{packageName}}.core.domain.model.Note
import {{packageName}}.core.domain.model.NoteLayout
import {{packageName}}.core.domain.model.SortOrder
import {{packageName}}.core.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow

/** Quelle der Notizen. */
interface NoteRepository {
    val notes: Flow<List<Note>>

    fun note(id: Long): Flow<Note?>

    /** Legt an ([Note.id] = 0) oder überschreibt; liefert die ID. */
    suspend fun upsert(note: Note): Long

    suspend fun delete(id: Long)
}

/** Gespeicherte Einstellungen. */
interface SettingsRepository {
    val settings: Flow<AppSettings>

    suspend fun setThemeMode(mode: ThemeMode)

    suspend fun setAccent(accent: Accent)

    suspend fun setLayout(layout: NoteLayout)

    suspend fun setSortOrder(order: SortOrder)
}
