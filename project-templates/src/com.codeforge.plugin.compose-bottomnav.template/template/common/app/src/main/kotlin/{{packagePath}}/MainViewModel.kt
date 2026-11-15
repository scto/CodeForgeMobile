package {{packageName}}

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import {{packageName}}.core.domain.usecase.ObserveTaskStatsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Zahl der offenen Aufgaben für das Abzeichen in der unteren Leiste. */
@HiltViewModel
class MainViewModel @Inject constructor(observeStats: ObserveTaskStatsUseCase) : ViewModel() {
    val openTasks: StateFlow<Int> = observeStats()
        .map { it.open }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), 0)

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
