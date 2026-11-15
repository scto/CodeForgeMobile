package {{packageName}}.core.data

import {{packageName}}.core.domain.model.Accent
import {{packageName}}.core.domain.model.AppSettings
import {{packageName}}.core.domain.model.Note
import {{packageName}}.core.domain.model.NoteColor
import {{packageName}}.core.domain.model.NoteLayout
import {{packageName}}.core.domain.model.SortOrder
import {{packageName}}.core.domain.model.ThemeMode
import {{packageName}}.core.domain.repository.NoteRepository
import {{packageName}}.core.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/** Notizen im Speicher; für Dauerhaftigkeit durch Room ersetzen – Domain und Oberfläche bleiben gleich. */
@Singleton
class InMemoryNoteRepository @Inject constructor() : NoteRepository {
    private val nextId = AtomicLong(SAMPLES.size.toLong())
    private val state = MutableStateFlow(SAMPLES)
    override val notes: StateFlow<List<Note>> = state.asStateFlow()

    override fun note(id: Long): Flow<Note?> = state.map { notes -> notes.firstOrNull { it.id == id } }

    override suspend fun upsert(note: Note): Long {
        val id = if (note.id == 0L) nextId.incrementAndGet() else note.id
        val saved = note.copy(id = id, updatedAt = System.currentTimeMillis())
        state.update { notes -> notes.filterNot { it.id == id } + saved }
        return id
    }

    override suspend fun delete(id: Long) = state.update { notes -> notes.filterNot { it.id == id } }

    private companion object {
        private const val HOUR = 3_600_000L
        private val now = System.currentTimeMillis()
        val SAMPLES = listOf(
            Note(
                1,
                "Willkommen 👋",
                "Tippe auf eine Notiz, um sie zu bearbeiten. Farbe und Anheften findest du im Editor.",
                NoteColor.LAVENDER,
                pinned = true,
                updatedAt = now
            ),
            Note(2, "Einkaufsliste", "• Hafermilch\n• Brot\n• Kaffeebohnen\n• Basilikum", NoteColor.MINT, updatedAt = now - HOUR),
            Note(3, "Podcast-Ideen", "Folge über Jetpack Compose und Designsysteme.", NoteColor.SKY, updatedAt = now - 5 * HOUR),
            Note(4, "Zitat", "„Einfachheit ist die höchste Stufe der Vollendung.“", NoteColor.LEMON, updatedAt = now - 30 * HOUR),
            Note(5, "Workout", "Mo: Laufen 5 km\nMi: Kraft\nFr: Yoga", NoteColor.PEACH, updatedAt = now - 50 * HOUR)
        )
    }
}

/** Einstellungen im Speicher; für Dauerhaftigkeit durch DataStore ersetzen. */
@Singleton
class InMemorySettingsRepository @Inject constructor() : SettingsRepository {
    private val state = MutableStateFlow(AppSettings())
    override val settings: StateFlow<AppSettings> = state.asStateFlow()

    override suspend fun setThemeMode(mode: ThemeMode) = state.update { it.copy(themeMode = mode) }

    override suspend fun setAccent(accent: Accent) = state.update { it.copy(accent = accent) }

    override suspend fun setLayout(layout: NoteLayout) = state.update { it.copy(layout = layout) }

    override suspend fun setSortOrder(order: SortOrder) = state.update { it.copy(sortOrder = order) }
}
