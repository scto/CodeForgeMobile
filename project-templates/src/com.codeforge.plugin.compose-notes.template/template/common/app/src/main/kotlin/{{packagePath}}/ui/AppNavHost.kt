package {{packageName}}.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import {{packageName}}.feature.editor.EditorDestination
import {{packageName}}.feature.editor.editorScreen
import {{packageName}}.feature.notes.NotesDestination
import {{packageName}}.feature.notes.notesScreen
import {{packageName}}.feature.settings.SettingsDestination
import {{packageName}}.feature.settings.settingsScreen

/** Navigation: Übersicht → Editor bzw. Einstellungen (mit Übergängen aus den Features). */
@Composable
fun AppNavHost(modifier: Modifier = Modifier, navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = NotesDestination, modifier = modifier) {
        notesScreen(
            onOpenNote = { id -> navController.navigate(EditorDestination(id)) },
            onNewNote = { navController.navigate(EditorDestination()) },
            onOpenSettings = { navController.navigate(SettingsDestination) }
        )
        editorScreen(onBack = navController::popBackStack)
        settingsScreen(onBack = navController::popBackStack)
    }
}
