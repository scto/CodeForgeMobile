package {{packageName}}.core.domain

import {{packageName}}.core.domain.model.Category
import {{packageName}}.core.domain.model.Episode
import {{packageName}}.core.domain.model.Podcast
import {{packageName}}.core.domain.repository.PodcastRepository
import {{packageName}}.core.domain.usecase.ObserveDiscoverUseCase
import {{packageName}}.core.domain.usecase.ObserveLibraryUseCase
import {{packageName}}.core.domain.usecase.ObservePodcastDetailUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PodcastUseCasesTest {
    private class FakeRepository : PodcastRepository {
        override val podcasts = MutableStateFlow(
            listOf(
                Podcast(1, "Code", "A", Category.TECH, "", 1),
                Podcast(2, "Sterne", "B", Category.SCIENCE, "", 2),
                Podcast(3, "Bits", "C", Category.TECH, "", 3)
            )
        )
        override val episodes = MutableStateFlow(
            listOf(
                Episode(10, 1, "Alt", "", 600, publishedDaysAgo = 9),
                Episode(11, 1, "Neu", "", 600, publishedDaysAgo = 1),
                Episode(20, 2, "Mond", "", 600, publishedDaysAgo = 2),
                Episode(30, 3, "Chips", "", 600, publishedDaysAgo = 3)
            )
        )
        override val subscriptions = MutableStateFlow(setOf(2L))
        override suspend fun toggleSubscription(podcastId: Long) =
            subscriptions.update { if (podcastId in it) it - podcastId else it + podcastId }
    }

    @Test
    fun discoverFiltersByCategoryAndSortsNewest() = runTest {
        val feed = ObserveDiscoverUseCase(FakeRepository())(Category.TECH).first()
        assertEquals("Code", feed.featured?.title)
        assertEquals(listOf("Bits"), feed.trending.map { it.title })
        assertEquals(listOf("Neu", "Chips", "Alt"), feed.newEpisodes.map { it.episode.title })
    }

    @Test
    fun libraryAndDetailFollowSubscriptions() = runTest {
        val repository = FakeRepository()
        assertEquals(listOf("Sterne"), ObserveLibraryUseCase(repository)().first().map { it.title })
        repository.toggleSubscription(1)
        val detail = ObservePodcastDetailUseCase(repository)(1).first()!!
        assertTrue(detail.subscribed)
        assertEquals(listOf("Neu", "Alt"), detail.episodes.map { it.title })
    }
}
