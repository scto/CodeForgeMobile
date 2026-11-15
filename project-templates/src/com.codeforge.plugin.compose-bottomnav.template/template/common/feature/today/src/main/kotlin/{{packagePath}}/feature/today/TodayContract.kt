package {{packageName}}.feature.today

import androidx.compose.runtime.Immutable
import {{packageName}}.core.domain.model.TaskStats

/** Zustand von „Heute“. */
@Immutable
data class TodayUiState(
    val name: String = "",
    val dailyGoal: Int = 0,
    val stats: TaskStats = TaskStats(0, 0),
    val nextTasks: List<String> = emptyList(),
    val isLoading: Boolean = true
) {
    /** Tagesziel erreicht. */
    val goalReached: Boolean get() = dailyGoal > 0 && stats.done >= dailyGoal
}
