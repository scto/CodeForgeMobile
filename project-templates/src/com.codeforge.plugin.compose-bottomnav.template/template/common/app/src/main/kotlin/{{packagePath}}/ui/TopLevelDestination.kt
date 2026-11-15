package {{packageName}}.ui

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.graphics.vector.ImageVector
import {{packageName}}.core.resources.R
import {{packageName}}.feature.profile.ProfileDestination
import {{packageName}}.feature.tasks.TasksDestination
import {{packageName}}.feature.today.TodayDestination

/** Die drei Ziele der unteren Leiste. [route] ist das typsichere Navigationsziel des Features. */
enum class TopLevelDestination(val route: Any, val icon: ImageVector, @param:StringRes val label: Int) {
    TODAY(TodayDestination, Icons.Filled.Home, R.string.nav_today),
    TASKS(TasksDestination, Icons.Filled.CheckCircle, R.string.nav_tasks),
    PROFILE(ProfileDestination, Icons.Filled.Person, R.string.nav_profile)
}
