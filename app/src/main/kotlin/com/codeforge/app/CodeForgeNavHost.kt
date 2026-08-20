// Modul: :app
package com.codeforge.app

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.codeforge.feature.editor.EditorRoute
import com.codeforge.feature.onboarding.OnboardingRoute
import com.codeforge.feature.projectwizard.ProjectWizardRoute
import com.codeforge.feature.welcome.WelcomeRoute

private object Routes {
    const val ONBOARDING = "onboarding"
    const val WELCOME = "welcome"
    const val PROJECT_WIZARD = "project_wizard"
    const val IMPORT_PROJECT = "import_project"
    const val CLONE_PROJECT = "clone_project"
    const val SETTINGS = "settings"

    // Editor mit optionalem projectPath-Argument:
    // navigate(Routes.editorWithPath("/sdcard/MyApp")) oder Routes.EDITOR (ohne Pfad)
    const val EDITOR = "editor"
    const val EDITOR_PATH_ARG = "projectPath"
    const val EDITOR_WITH_PATH = "editor?$EDITOR_PATH_ARG={$EDITOR_PATH_ARG}"

    fun editorWithPath(path: String): String = "editor?$EDITOR_PATH_ARG=$path"
}

@Composable
fun CodeForgeNavHost(startOnboarding: Boolean) {
    val navController = rememberNavController()
    val startDestination = if (startOnboarding) Routes.ONBOARDING else Routes.WELCOME

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.ONBOARDING) {
            OnboardingRoute(
                onFinished = {
                    navController.navigate(Routes.WELCOME) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.WELCOME) {
            WelcomeRoute(
                onNavigateToProjectWizard = { navController.navigate(Routes.PROJECT_WIZARD) },
                onNavigateToImportPicker = { navController.navigate(Routes.IMPORT_PROJECT) },
                onNavigateToCloneDialog = { navController.navigate(Routes.CLONE_PROJECT) },
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenProject = { projectPath ->
                    navController.navigate(Routes.editorWithPath(projectPath))
                }
            )
        }

        composable(Routes.PROJECT_WIZARD) {
            ProjectWizardRoute(
                onNavigateToEditor = { projectPath ->
                    navController.navigate(Routes.editorWithPath(projectPath)) {
                        // Wizard aus dem Back-Stack entfernen – nicht zurück in den Wizard
                        popUpTo(Routes.PROJECT_WIZARD) { inclusive = true }
                    }
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Routes.IMPORT_PROJECT) {
            // TODO :feature:filetree / SAF-Picker – Projekt importieren
        }

        composable(Routes.CLONE_PROJECT) {
            // TODO :feature:git – Clone-Dialog
        }

        composable(Routes.SETTINGS) {
            // TODO :feature:settings – App-Settings, Multitheme-Auswahl
        }

        composable(
            route = Routes.EDITOR_WITH_PATH,
            arguments = listOf(
                navArgument(Routes.EDITOR_PATH_ARG) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val projectPath = backStackEntry.arguments?.getString(Routes.EDITOR_PATH_ARG)
            EditorRoute(
                projectPath = projectPath,
                onNavigate = { route -> navController.navigate(route) }
            )
        }
    }
}

