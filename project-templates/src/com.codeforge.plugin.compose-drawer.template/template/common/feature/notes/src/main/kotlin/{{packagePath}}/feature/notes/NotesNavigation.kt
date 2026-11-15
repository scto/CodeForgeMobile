package {{packageName}}.feature.notes

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

/** Ziel der Notizliste (typsichere Navigation). */
@Serializable
data object NotesDestination

fun NavGraphBuilder.notesScreen() {
    composable<NotesDestination> { NotesRoute() }
}
