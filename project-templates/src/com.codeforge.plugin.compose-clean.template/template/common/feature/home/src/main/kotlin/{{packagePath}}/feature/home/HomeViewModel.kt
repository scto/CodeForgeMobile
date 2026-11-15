package {{packageName}}.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import {{packageName}}.core.domain.usecase.IncrementGreetingUseCase
import {{packageName}}.core.domain.usecase.ObserveGreetingUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** MVI: Zustand als StateFlow, Aktionen über [onEvent]. */
@HiltViewModel
class HomeViewModel @Inject constructor(observeGreeting: ObserveGreetingUseCase, private val incrementGreeting: IncrementGreetingUseCase) :
    ViewModel() {

    val uiState: StateFlow<HomeUiState> = observeGreeting()
        .map { HomeUiState(greeting = it.text, count = it.count, isLoading = false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), HomeUiState())

    fun onEvent(event: HomeUiEvent) {
        when (event) {
            HomeUiEvent.IncrementClicked -> viewModelScope.launch { incrementGreeting() }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
