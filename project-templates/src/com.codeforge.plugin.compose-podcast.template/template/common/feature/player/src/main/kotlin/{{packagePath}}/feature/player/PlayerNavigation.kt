package {{packageName}}.feature.player

import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

/** Ziel des Vollbild-Players (typsichere Navigation). */
@Serializable
data object PlayerDestination

fun NavGraphBuilder.playerScreen(onClose: () -> Unit) {
    composable<PlayerDestination>(
        enterTransition = { slideInVertically { it } },
        popExitTransition = { slideOutVertically { it } }
    ) {
        PlayerRoute(onClose)
    }
}
