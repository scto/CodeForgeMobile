package {{packageName}}.feature.library

import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

/** Ziel der Bibliothek (typsichere Navigation). */
@Serializable
data object LibraryDestination

/** Ziel einer Podcast-Seite. */
@Serializable
data class PodcastDestination(val id: Long) {
    companion object {
        /** Schlüssel des Arguments im SavedStateHandle (Name der Eigenschaft). */
        const val ARG_ID = "id"
    }
}

fun NavGraphBuilder.libraryScreen(onOpenPodcast: (Long) -> Unit) {
    composable<LibraryDestination> { LibraryRoute(onOpenPodcast) }
}

fun NavGraphBuilder.podcastScreen(onBack: () -> Unit) {
    composable<PodcastDestination>(
        enterTransition = { slideInHorizontally { it } },
        exitTransition = { fadeOut() },
        popExitTransition = { slideOutHorizontally { it } }
    ) {
        PodcastRoute(onBack)
    }
}
