package {{packageName}}.core.domain.model

/** Farbe einer Notizkarte. */
enum class NoteColor { NONE, ROSE, PEACH, LEMON, MINT, SKY, LAVENDER }

/** Eine Notiz; [id] 0 bedeutet „noch nicht gespeichert“. */
data class Note(
    val id: Long = 0,
    val title: String = "",
    val content: String = "",
    val color: NoteColor = NoteColor.NONE,
    val pinned: Boolean = false,
    val updatedAt: Long = 0
) {
    /** Leer: weder Titel noch Text. */
    val isBlank: Boolean get() = title.isBlank() && content.isBlank()
}

/** Angeheftete und übrige Notizen getrennt. */
data class NoteSections(val pinned: List<Note>, val others: List<Note>) {
    val total: Int get() = pinned.size + others.size
}

/** Helles, dunkles oder System-Design. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Akzentfarbe der App (Namen wie im Designsystem). */
enum class Accent { VIOLET, OCEAN, SUNSET, FOREST }

/** Raster oder Liste. */
enum class NoteLayout { GRID, LIST }

/** Sortierung der Notizen. */
enum class SortOrder { UPDATED, TITLE }

/** Einstellungen der App. */
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accent: Accent = Accent.VIOLET,
    val layout: NoteLayout = NoteLayout.GRID,
    val sortOrder: SortOrder = SortOrder.UPDATED
)
