package {{packageName}}.feature.settings

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

/** Ziel der Einstellungen (typsichere Navigation). */
@Serializable
data object SettingsDestination

fun NavGraphBuilder.settingsScreen() {
    composable<SettingsDestination> { SettingsRoute() }
}
