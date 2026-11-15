package {{packageName}}.feature.home

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

/** Ziel der Startseite (typsichere Navigation). */
@Serializable
data object HomeDestination

fun NavGraphBuilder.homeScreen(onOpenNotes: () -> Unit) {
    composable<HomeDestination> { HomeRoute(onOpenNotes = onOpenNotes) }
}
