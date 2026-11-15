package {{packageName}}.feature.profile

import androidx.compose.runtime.Immutable
import {{packageName}}.core.domain.model.Profile
import {{packageName}}.core.domain.model.TaskStats

/** Zustand des Profils. */
@Immutable
data class ProfileUiState(val profile: Profile = Profile(), val stats: TaskStats = TaskStats(0, 0))

/** Aktionen des Nutzers. */
sealed interface ProfileUiEvent {
    data class NameChanged(val value: String) : ProfileUiEvent
    data class GoalChanged(val value: Int) : ProfileUiEvent
}
