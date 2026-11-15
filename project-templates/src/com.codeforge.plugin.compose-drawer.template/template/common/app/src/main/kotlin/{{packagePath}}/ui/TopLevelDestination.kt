package {{packageName}}.ui

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import {{packageName}}.R
import {{packageName}}.feature.home.HomeDestination
import {{packageName}}.feature.notes.NotesDestination
import {{packageName}}.feature.settings.SettingsDestination

/** Die drei Ziele im Menü. [route] ist das typsichere Navigationsziel des Features. */
enum class TopLevelDestination(val route: Any, val icon: ImageVector, @param:StringRes val label: Int) {
    HOME(HomeDestination, Icons.Filled.Home, R.string.nav_home),
    NOTES(NotesDestination, Icons.Filled.Edit, R.string.nav_notes),
    SETTINGS(SettingsDestination, Icons.Filled.Settings, R.string.nav_settings)
}
