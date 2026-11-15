package {{packageName}}.feature.notes

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

/** Ziel der Notizübersicht (typsichere Navigation). */
@Serializable
data object NotesDestination

fun NavGraphBuilder.notesScreen(onOpenNote: (Long) -> Unit, onNewNote: () -> Unit, onOpenSettings: () -> Unit) {
    composable<NotesDestination> { NotesRoute(onOpenNote, onNewNote, onOpenSettings) }
}
