package {{packageName}}.feature.library

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import {{packageName}}.core.domain.model.Podcast
import {{packageName}}.core.domain.model.PodcastDetail
import {{packageName}}.core.domain.usecase.ObserveLibraryUseCase
import {{packageName}}.core.domain.usecase.ObservePodcastDetailUseCase
import {{packageName}}.core.domain.usecase.PlayEpisodeUseCase
import {{packageName}}.core.domain.usecase.ToggleSubscriptionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Zustand der Bibliothek. */
@Immutable
data class LibraryUiState(val podcasts: List<Podcast> = emptyList(), val isLoading: Boolean = true)

/** Abonnierte Podcasts. */
@HiltViewModel
class LibraryViewModel @Inject constructor(observeLibrary: ObserveLibraryUseCase) : ViewModel() {
    val uiState: StateFlow<LibraryUiState> = observeLibrary()
        .map { LibraryUiState(it, isLoading = false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), LibraryUiState())
}

/** Zustand der Podcast-Seite; [detail] ist `null`, solange geladen wird oder der Podcast fehlt. */
@Immutable
data class PodcastUiState(val detail: PodcastDetail? = null)

/** Aktionen der Podcast-Seite. */
sealed interface PodcastUiEvent {
    data object SubscribeToggled : PodcastUiEvent
    data class PlayEpisode(val episodeId: Long) : PodcastUiEvent
}

/** Ein Podcast mit Folgen; die ID kommt aus der Route. */
@HiltViewModel
class PodcastViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeDetail: ObservePodcastDetailUseCase,
    private val toggleSubscription: ToggleSubscriptionUseCase,
    private val playEpisode: PlayEpisodeUseCase
) : ViewModel() {
    private val podcastId: Long = checkNotNull(savedStateHandle[PodcastDestination.ARG_ID])

    val uiState: StateFlow<PodcastUiState> = observeDetail(podcastId)
        .map(::PodcastUiState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), PodcastUiState())

    fun onEvent(event: PodcastUiEvent) {
        viewModelScope.launch {
            when (event) {
                PodcastUiEvent.SubscribeToggled -> toggleSubscription(podcastId)
                is PodcastUiEvent.PlayEpisode -> playEpisode(event.episodeId)
            }
        }
    }
}

private const val STOP_TIMEOUT_MS = 5_000L
