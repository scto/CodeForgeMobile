package {{packageName}}.feature.discover

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

/** Ziel der Startseite (typsichere Navigation). */
@Serializable
data object DiscoverDestination

fun NavGraphBuilder.discoverScreen(onOpenPodcast: (Long) -> Unit, onOpenPlayer: () -> Unit) {
    composable<DiscoverDestination> { DiscoverRoute(onOpenPodcast, onOpenPlayer) }
}
