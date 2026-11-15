package {{packageName}}.core.domain.model

/** Rubrik eines Podcasts. */
enum class Category { TECH, SCIENCE, CULTURE, NEWS, COMEDY }

/** Ein Podcast; [seed] bestimmt den Verlauf seines Covers. */
data class Podcast(val id: Long, val title: String, val author: String, val category: Category, val description: String, val seed: Int)

/** Eine Folge. */
data class Episode(
    val id: Long,
    val podcastId: Long,
    val title: String,
    val summary: String,
    val durationSec: Int,
    val publishedDaysAgo: Int
)

/** Folge mit ihrem Podcast (für Listen). */
data class EpisodeItem(val episode: Episode, val podcast: Podcast)

/** Inhalte der Startseite. */
data class DiscoverFeed(val featured: Podcast?, val trending: List<Podcast>, val newEpisodes: List<EpisodeItem>)

/** Podcast mit allen Folgen und Abo-Status. */
data class PodcastDetail(val podcast: Podcast, val episodes: List<Episode>, val subscribed: Boolean)

/** Stand der Wiedergabe; [item] ist `null`, solange nichts geladen ist. */
data class PlaybackState(val item: EpisodeItem? = null, val positionSec: Int = 0, val isPlaying: Boolean = false, val speed: Float = 1f) {
    /** Fortschritt zwischen 0 und 1. */
    val progress: Float
        get() = item?.episode?.durationSec?.takeIf { it > 0 }?.let { positionSec.toFloat() / it } ?: 0f
}

/** Helles, dunkles oder System-Design. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Akzentfarbe der App (Namen wie im Designsystem). */
enum class Accent { VIOLET, OCEAN, SUNSET, FOREST }

/** Einstellungen der App. */
data class AppSettings(val themeMode: ThemeMode = ThemeMode.SYSTEM, val accent: Accent = Accent.SUNSET, val playbackSpeed: Float = 1f) {
    companion object {
        /** Wählbare Wiedergabegeschwindigkeiten. */
        val SPEEDS = listOf(1f, 1.25f, 1.5f, 2f)
    }
}
