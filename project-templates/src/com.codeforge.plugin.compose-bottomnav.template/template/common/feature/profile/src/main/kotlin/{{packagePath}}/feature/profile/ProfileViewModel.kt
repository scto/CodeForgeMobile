package {{packageName}}.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import {{packageName}}.core.domain.usecase.ObserveProfileUseCase
import {{packageName}}.core.domain.usecase.ObserveTaskStatsUseCase
import {{packageName}}.core.domain.usecase.UpdateProfileUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Profil bearbeiten; Änderungen werden sofort gespeichert. */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    observeProfile: ObserveProfileUseCase,
    observeStats: ObserveTaskStatsUseCase,
    private val updateProfile: UpdateProfileUseCase
) : ViewModel() {

    val uiState: StateFlow<ProfileUiState> = combine(observeProfile(), observeStats(), ::ProfileUiState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ProfileUiState())

    fun onEvent(event: ProfileUiEvent) {
        val current = uiState.value.profile
        viewModelScope.launch {
            when (event) {
                is ProfileUiEvent.NameChanged -> updateProfile(current.copy(name = event.value))
                is ProfileUiEvent.GoalChanged -> updateProfile(current.copy(dailyGoal = event.value))
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
