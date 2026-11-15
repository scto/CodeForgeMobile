package {{packageName}}.feature.settings

import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

/** Ziel der Einstellungen (typsichere Navigation). */
@Serializable
data object SettingsDestination

fun NavGraphBuilder.settingsScreen(onBack: () -> Unit) {
    composable<SettingsDestination>(
        enterTransition = { slideInHorizontally { it } },
        exitTransition = { fadeOut() },
        popExitTransition = { slideOutHorizontally { it } }
    ) {
        SettingsRoute(onBack = onBack)
    }
}
