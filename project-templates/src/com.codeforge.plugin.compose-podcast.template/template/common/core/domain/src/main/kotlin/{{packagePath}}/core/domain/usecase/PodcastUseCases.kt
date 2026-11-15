package {{packageName}}.core.domain.usecase

import {{packageName}}.core.domain.model.Category
import {{packageName}}.core.domain.model.DiscoverFeed
import {{packageName}}.core.domain.model.EpisodeItem
import {{packageName}}.core.domain.model.Podcast
import {{packageName}}.core.domain.model.PodcastDetail
import {{packageName}}.core.domain.repository.PodcastRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/** Startseite: Empfehlung, Trends und neue Folgen – optional nur einer [Category]. */
class ObserveDiscoverUseCase @Inject constructor(private val repository: PodcastRepository) {
    operator fun invoke(category: Category?): Flow<DiscoverFeed> = combine(repository.podcasts, repository.episodes) { podcasts, episodes ->
        val visible = podcasts.filter { category == null || it.category == category }
        val byId = podcasts.associateBy { it.id }
        val newest = episodes
            .filter { episode -> visible.any { it.id == episode.podcastId } }
            .sortedBy { it.publishedDaysAgo }
            .take(NEW_EPISODES)
            .mapNotNull { episode -> byId[episode.podcastId]?.let { EpisodeItem(episode, it) } }
        DiscoverFeed(featured = visible.firstOrNull(), trending = visible.drop(1), newEpisodes = newest)
    }

    private companion object {
        const val NEW_EPISODES = 6
    }
}

/** Abonnierte Podcasts. */
class ObserveLibraryUseCase @Inject constructor(private val repository: PodcastRepository) {
    operator fun invoke(): Flow<List<Podcast>> =
        combine(repository.podcasts, repository.subscriptions) { podcasts, subscribed -> podcasts.filter { it.id in subscribed } }
}

/** Ein Podcast mit Folgen (neueste zuerst) und Abo-Status; `null`, wenn es ihn nicht gibt. */
class ObservePodcastDetailUseCase @Inject constructor(private val repository: PodcastRepository) {
    operator fun invoke(id: Long): Flow<PodcastDetail?> =
        combine(repository.podcasts, repository.episodes, repository.subscriptions) { podcasts, episodes, subscribed ->
            podcasts.firstOrNull { it.id == id }?.let { podcast ->
                PodcastDetail(
                    podcast = podcast,
                    episodes = episodes.filter { it.podcastId == id }.sortedBy { it.publishedDaysAgo },
                    subscribed = id in subscribed
                )
            }
        }
}

/** Abonniert einen Podcast oder beendet das Abo. */
class ToggleSubscriptionUseCase @Inject constructor(private val repository: PodcastRepository) {
    suspend operator fun invoke(podcastId: Long) = repository.toggleSubscription(podcastId)
}

/** Sucht Folge und Podcast zu einer ID. */
class FindEpisodeUseCase @Inject constructor(private val repository: PodcastRepository) {
    suspend operator fun invoke(episodeId: Long): EpisodeItem? {
        val episode = repository.episodes.first().firstOrNull { it.id == episodeId }
        val podcast = episode?.let { found -> repository.podcasts.first().firstOrNull { it.id == found.podcastId } }
        return if (episode != null && podcast != null) EpisodeItem(episode, podcast) else null
    }
}
