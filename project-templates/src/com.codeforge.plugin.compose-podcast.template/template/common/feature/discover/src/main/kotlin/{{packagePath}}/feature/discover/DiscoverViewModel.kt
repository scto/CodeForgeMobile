package {{packageName}}.feature.discover

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import {{packageName}}.core.domain.model.Category
import {{packageName}}.core.domain.model.DiscoverFeed
import {{packageName}}.core.domain.model.PlaybackState
import {{packageName}}.core.domain.usecase.ObserveDiscoverUseCase
import {{packageName}}.core.domain.usecase.PlayEpisodeUseCase
import {{packageName}}.core.domain.usecase.PlaybackUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Zustand der Startseite. */
@Immutable
data class DiscoverUiState(
    val category: Category? = null,
    val feed: DiscoverFeed = DiscoverFeed(null, emptyList(), emptyList()),
    val playback: PlaybackState = PlaybackState(),
    val isLoading: Boolean = true
)

/** Aktionen der Startseite. */
sealed interface DiscoverUiEvent {
    data class CategorySelected(val category: Category?) : DiscoverUiEvent
    data class PlayEpisode(val episodeId: Long) : DiscoverUiEvent
    data object TogglePlay : DiscoverUiEvent
}

/** Startseite: Kategorie als eigener Zustand, Inhalte und Wiedergabe aus der Domain. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DiscoverViewModel @Inject constructor(
    observeDiscover: ObserveDiscoverUseCase,
    private val playEpisode: PlayEpisodeUseCase,
    private val playback: PlaybackUseCase
) : ViewModel() {

    private val category = MutableStateFlow<Category?>(null)

    val uiState: StateFlow<DiscoverUiState> = combine(
        category.flatMapLatest { selected -> observeDiscover(selected) },
        category,
        playback.state
    ) { feed, selected, playbackState ->
        DiscoverUiState(selected, feed, playbackState, isLoading = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), DiscoverUiState())

    fun onEvent(event: DiscoverUiEvent) {
        when (event) {
            is DiscoverUiEvent.CategorySelected -> category.value = event.category
            is DiscoverUiEvent.PlayEpisode -> viewModelScope.launch { playEpisode(event.episodeId) }
            DiscoverUiEvent.TogglePlay -> playback.togglePlay()
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
