package {{packageName}}.core.data

import {{packageName}}.core.domain.model.Category
import {{packageName}}.core.domain.model.Episode
import {{packageName}}.core.domain.model.EpisodeItem
import {{packageName}}.core.domain.model.Podcast
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SimulatedPlaybackControllerTest {
    private val item = EpisodeItem(
        Episode(1, 1, "Folge", "", durationSec = 10, publishedDaysAgo = 0),
        Podcast(1, "Pod", "A", Category.TECH, "", 0)
    )

    @Test
    fun positionAdvancesWithSpeedAndStopsAtEnd() = runTest {
        val controller = SimulatedPlaybackController(backgroundScope)
        controller.play(item, speed = 2f)
        advanceTimeBy(3_000)
        runCurrent()
        assertEquals(6, controller.state.value.positionSec)

        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(10, controller.state.value.positionSec)
        assertFalse(controller.state.value.isPlaying)
    }

    @Test
    fun pauseAndSkipKeepPositionInRange() = runTest {
        val controller = SimulatedPlaybackController(backgroundScope)
        controller.play(item, speed = 1f)
        controller.togglePlay()
        controller.skip(30)
        assertEquals(10, controller.state.value.positionSec)
        controller.skip(-30)
        assertEquals(0, controller.state.value.positionSec)
    }
}
