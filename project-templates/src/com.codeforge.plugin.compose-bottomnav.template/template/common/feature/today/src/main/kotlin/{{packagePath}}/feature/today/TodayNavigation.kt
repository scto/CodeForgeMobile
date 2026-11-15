package {{packageName}}.feature.today

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

/** Ziel „Heute“ (typsichere Navigation). */
@Serializable
data object TodayDestination

fun NavGraphBuilder.todayScreen(onOpenTasks: () -> Unit) {
    composable<TodayDestination> { TodayRoute(onOpenTasks = onOpenTasks) }
}
