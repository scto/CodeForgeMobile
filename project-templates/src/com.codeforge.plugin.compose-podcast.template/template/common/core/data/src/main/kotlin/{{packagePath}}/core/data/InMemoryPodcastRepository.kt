package {{packageName}}.core.data

import {{packageName}}.core.domain.model.Category
import {{packageName}}.core.domain.model.Episode
import {{packageName}}.core.domain.model.Podcast
import {{packageName}}.core.domain.repository.PodcastRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/** Beispielkatalog im Speicher; durch eine RSS-/API-Quelle ersetzbar, ohne Domain und Oberfläche zu ändern. */
@Singleton
class InMemoryPodcastRepository @Inject constructor() : PodcastRepository {
    override val podcasts: Flow<List<Podcast>> = flowOf(PODCASTS)
    override val episodes: Flow<List<Episode>> = flowOf(EPISODES)
    private val subscribed = MutableStateFlow(setOf(1L, 3L))
    override val subscriptions: Flow<Set<Long>> = subscribed

    override suspend fun toggleSubscription(podcastId: Long) = subscribed.update { if (podcastId in it) it - podcastId else it + podcastId }

    private companion object {
        val PODCASTS = listOf(
            Podcast(1, "Compose Kitchen", "Lena Fischer", Category.TECH, "Rezepte für schöne Oberflächen mit Jetpack Compose.", 0),
            Podcast(2, "Sternenstaub", "Dr. Jonas Weber", Category.SCIENCE, "Astronomie für alle – vom Mond bis zu schwarzen Löchern.", 4),
            Podcast(3, "Kotlin am Morgen", "Mia & Ben", Category.TECH, "Zehn Minuten Kotlin zum Kaffee.", 1),
            Podcast(4, "Zeitgeist", "Redaktion Mitte", Category.NEWS, "Die Woche in 30 Minuten eingeordnet.", 5),
            Podcast(5, "Lachfalten", "Tom & Jerry Schulz", Category.COMEDY, "Alltag, aber lustig.", 2),
            Podcast(6, "Kulturbeutel", "Sophie Klein", Category.CULTURE, "Bücher, Filme und Musik, die bleiben.", 6),
            Podcast(7, "Quantensprung", "Uni-Radio", Category.SCIENCE, "Physik verständlich erklärt.", 7),
            Podcast(8, "Bit für Bit", "Pixelpost", Category.TECH, "Hardware, Gadgets und Open Source.", 3)
        )

        // Vor EPISODES deklarieren: Eigenschaften werden in Reihenfolge initialisiert.
        val TOPICS = listOf("Neuanfang", "Hinter den Kulissen", "Große Fragen", "Live vom Festival", "Mythen", "Werkzeugkiste")

        val EPISODES = PODCASTS.flatMap { podcast ->
            List(EPISODES_PER_PODCAST) { index ->
                Episode(
                    id = podcast.id * 100 + index,
                    podcastId = podcast.id,
                    title = "${podcast.title} #${EPISODES_PER_PODCAST * 10 - index}: ${TOPICS[(podcast.id.toInt() + index) % TOPICS.size]}",
                    summary = "In dieser Folge: ${TOPICS[(podcast.id.toInt() + index + 1) % TOPICS.size]} und eure Fragen.",
                    durationSec = (MIN_MINUTES + (podcast.id.toInt() * 7 + index * 11) % SPREAD_MINUTES) * SECONDS_PER_MINUTE,
                    publishedDaysAgo = index * 7 + podcast.id.toInt() % 5
                )
            }
        }

        const val EPISODES_PER_PODCAST = 4
        const val MIN_MINUTES = 18
        const val SPREAD_MINUTES = 40
        const val SECONDS_PER_MINUTE = 60
    }
}
