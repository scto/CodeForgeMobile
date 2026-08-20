// Modul: :app
package com.codeforge.app

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.codeforge.feature.editor.EditorRoute
import com.codeforge.feature.welcome.WelcomeRoute

private object Routes {
    const val ONBOARDING = "onboarding"
    const val WELCOME = "welcome"
    const val PROJECT_WIZARD = "project_wizard"
    const val IMPORT_PROJECT = "import_project"
    const val CLONE_PROJECT = "clone_project"
    const val SETTINGS = "settings"
    const val EDITOR = "editor"
}

@Composable
fun CodeForgeNavHost(startOnboarding: Boolean) {
    val navController = rememberNavController()
    val startDestination = if (startOnboarding) Routes.ONBOARDING else Routes.WELCOME

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.ONBOARDING) {
            // :feature:onboarding – IntroPagerScreen / PermissionScreen / SetupScreen
            // OnboardingRoute(onFinished = { navController.navigate(Routes.WELCOME) { popUpTo(Routes.ONBOARDING) { inclusive = true } } })
        }
        composable(Routes.WELCOME) {
            WelcomeRoute(
                onNavigateToProjectWizard = { navController.navigate(Routes.PROJECT_WIZARD) },
                onNavigateToImportPicker = { navController.navigate(Routes.IMPORT_PROJECT) },
                onNavigateToCloneDialog = { navController.navigate(Routes.CLONE_PROJECT) },
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenProject = { navController.navigate(Routes.EDITOR) }
            )
        }
        composable(Routes.PROJECT_WIZARD) {
            // :feature:projectwizard – Template-Engine UI, Projekt-Erstellung
        }
        composable(Routes.IMPORT_PROJECT) {
            // :feature:filetree / SAF-Picker – Projekt importieren
        }
        composable(Routes.CLONE_PROJECT) {
            // :feature:git – Clone-Dialog
        }
        composable(Routes.SETTINGS) {
            // :feature:settings – App-Settings, Multitheme-Auswahl
        }
        composable(Routes.EDITOR) {
            EditorRoute(onNavigate = { route -> navController.navigate(route) })
        }
    }
}
