package {{packageName}}.feature.tasks

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

/** Ziel der Aufgabenliste (typsichere Navigation). */
@Serializable
data object TasksDestination

fun NavGraphBuilder.tasksScreen() {
    composable<TasksDestination> { TasksRoute() }
}
