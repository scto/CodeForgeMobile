package {{packageName}}.feature.library

import androidx.lifecycle.SavedStateHandle
import {{packageName}}.core.domain.model.AppSettings
import {{packageName}}.core.domain.model.Accent
import {{packageName}}.core.domain.model.Category
import {{packageName}}.core.domain.model.Episode
import {{packageName}}.core.domain.model.EpisodeItem
import {{packageName}}.core.domain.model.PlaybackState
import {{packageName}}.core.domain.model.Podcast
import {{packageName}}.core.domain.model.ThemeMode
import {{packageName}}.core.domain.repository.PlaybackController
import {{packageName}}.core.domain.repository.PodcastRepository
import {{packageName}}.core.domain.repository.SettingsRepository
import {{packageName}}.core.domain.usecase.FindEpisodeUseCase
import {{packageName}}.core.domain.usecase.ObservePodcastDetailUseCase
import {{packageName}}.core.domain.usecase.PlayEpisodeUseCase
import {{packageName}}.core.domain.usecase.ToggleSubscriptionUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PodcastViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    private class FakePodcasts : PodcastRepository {
        override val podcasts = MutableStateFlow(listOf(Podcast(1, "Pod", "A", Category.TECH, "", 0)))
        override val episodes = MutableStateFlow(listOf(Episode(10, 1, "Folge", "", 600, 1)))
        override val subscriptions = MutableStateFlow(emptySet<Long>())
        override suspend fun toggleSubscription(podcastId: Long) =
            subscriptions.update { if (podcastId in it) it - podcastId else it + podcastId }
    }

    private class FakeController : PlaybackController {
        override val state = MutableStateFlow(PlaybackState())
        override fun play(item: EpisodeItem, speed: Float) {
            state.value = PlaybackState(item, isPlaying = true, speed = speed)
        }
        override fun togglePlay() = Unit
        override fun seekTo(positionSec: Int) = Unit
        override fun skip(deltaSec: Int) = Unit
        override fun setSpeed(speed: Float) = Unit
    }

    private class FakeSettings : SettingsRepository {
        override val settings = MutableStateFlow(AppSettings(playbackSpeed = 1.5f))
        override suspend fun setThemeMode(mode: ThemeMode) = Unit
        override suspend fun setAccent(accent: Accent) = Unit
        override suspend fun setPlaybackSpeed(speed: Float) = Unit
    }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun subscribeAndPlayWithDefaultSpeed() = runTest(dispatcher) {
        val podcasts = FakePodcasts()
        val controller = FakeController()
        val viewModel = PodcastViewModel(
            SavedStateHandle(mapOf(PodcastDestination.ARG_ID to 1L)),
            ObservePodcastDetailUseCase(podcasts),
            ToggleSubscriptionUseCase(podcasts),
            PlayEpisodeUseCase(FindEpisodeUseCase(podcasts), controller, FakeSettings())
        )
        val collector = launch { viewModel.uiState.collect {} }
        viewModel.onEvent(PodcastUiEvent.SubscribeToggled)
        assertTrue(viewModel.uiState.value.detail!!.subscribed)

        viewModel.onEvent(PodcastUiEvent.PlayEpisode(10))
        assertEquals("Folge", controller.state.value.item?.episode?.title)
        assertEquals(1.5f, controller.state.value.speed)
        collector.cancel()
    }
}
