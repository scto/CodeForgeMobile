package {{packageName}}.feature.editor

import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

/** Ziel des Editors; [noteId] = [NEW_NOTE] legt eine neue Notiz an. */
@Serializable
data class EditorDestination(val noteId: Long = NEW_NOTE) {
    companion object {
        const val NEW_NOTE = 0L

        /** Schlüssel des Arguments im SavedStateHandle (Name der Eigenschaft). */
        const val ARG_NOTE_ID = "noteId"
    }
}

fun NavGraphBuilder.editorScreen(onBack: () -> Unit) {
    composable<EditorDestination>(
        enterTransition = { slideInVertically { it / SLIDE_DIVISOR } + androidx.compose.animation.fadeIn() },
        exitTransition = { androidx.compose.animation.fadeOut() },
        popExitTransition = { slideOutVertically { it / SLIDE_DIVISOR } + androidx.compose.animation.fadeOut() }
    ) {
        EditorRoute(onBack = onBack)
    }
}

/** Der Editor gleitet um ein Drittel der Höhe herein. */
private const val SLIDE_DIVISOR = 3
