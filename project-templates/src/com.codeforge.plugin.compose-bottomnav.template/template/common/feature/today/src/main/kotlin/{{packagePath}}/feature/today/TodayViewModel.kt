package {{packageName}}.feature.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import {{packageName}}.core.domain.usecase.ObserveProfileUseCase
import {{packageName}}.core.domain.usecase.ObserveTaskStatsUseCase
import {{packageName}}.core.domain.usecase.ObserveTasksUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Fasst Profil, Fortschritt und die nächsten offenen Aufgaben zusammen. */
@HiltViewModel
class TodayViewModel @Inject constructor(
    observeProfile: ObserveProfileUseCase,
    observeStats: ObserveTaskStatsUseCase,
    observeTasks: ObserveTasksUseCase
) : ViewModel() {

    val uiState: StateFlow<TodayUiState> = combine(observeProfile(), observeStats(), observeTasks()) { profile, stats, tasks ->
        TodayUiState(
            name = profile.name,
            dailyGoal = profile.dailyGoal,
            stats = stats,
            nextTasks = tasks.filterNot { it.done }.take(NEXT_COUNT).map { it.title },
            isLoading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), TodayUiState())

    private companion object {
        const val NEXT_COUNT = 3
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
